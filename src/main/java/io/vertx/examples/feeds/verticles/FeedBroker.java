package io.vertx.examples.feeds.verticles;

import io.vertx.core.AbstractVerticle;
import io.vertx.core.Context;
import io.vertx.core.Future;
import io.vertx.core.Vertx;
import io.vertx.core.http.HttpClient;
import io.vertx.core.http.HttpClientOptions;
import io.vertx.core.http.HttpHeaders;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;
import io.vertx.examples.feeds.dao.RedisDAO;
import io.vertx.examples.feeds.utils.async.MultipleFutures;
import io.vertx.examples.feeds.utils.rss.FeedUtils;
import io.vertx.ext.mongo.MongoClient;
import io.vertx.redis.RedisClient;

import java.io.StringReader;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.List;
import java.util.stream.Collectors;

import com.rometools.rome.feed.synd.SyndFeed;
import com.rometools.rome.io.FeedException;
import com.rometools.rome.io.SyndFeedInput;

public class FeedBroker extends AbstractVerticle {

	// private final static Long POLL_PERIOD = 3600000l;
	private final static Long POLL_PERIOD = 10000l;
	private final static Logger log = LoggerFactory.getLogger(FeedBroker.class);
	private MongoClient mongo;
	private RedisDAO redis;
	private JsonObject config;

	private Long timerId;

	@Override
	public void init(Vertx vertx, Context context) {
		super.init(vertx, context);
		config = context.config();
	}

	@Override
	public void start(Future<Void> future) {
		mongo = MongoClient.createShared(vertx, config.getJsonObject("mongo"));
		redis = new RedisDAO(RedisClient.create(vertx, config.getJsonObject("redis")));
		readFeeds();
		future.complete();
	}

	@Override
	public void stop(Future<Void> future) {
		if (timerId != null) {
			vertx.cancelTimer(timerId);
		}
		mongo.close();
		future.complete();
	}

	private void readFeeds() {
		mongo.find("feeds", new JsonObject(), result -> {
			if (result.failed()) {
				log.error("Could not retrieve feed list from Mongo", result.cause());
			} else {
				List<JsonObject> allFeeds = result.result();
				List<JsonObject> feeds = allFeeds.stream().filter(feed -> {
					return feed.getInteger("subscriber_count") > 0;
				}).collect(Collectors.toList());
				MultipleFutures fetchFeedsFuture = new MultipleFutures();
				feeds.forEach(feed -> {
					fetchFeedsFuture.add(feedFuture -> {
						readFeed(feed, feedFuture);
					});
				});
				fetchFeedsFuture.setHandler(fetchResult -> {
					// No matter if failed or succeeded -> re-launch
					// periodically
						timerId = vertx.setTimer(POLL_PERIOD, timerId -> {
							readFeeds();
						});
					});
				fetchFeedsFuture.start();
			}
		});
	}

	public void readFeed(JsonObject jsonFeed, Future<Void> future) {
		String feedUrl = jsonFeed.getString("url");
		String feedId = jsonFeed.getString("hash");
		URL url;
		try {
			url = new URL(feedUrl);
		} catch (MalformedURLException mfe) {
			log.warn("Invalid url : " + feedUrl, mfe);
			if (future != null) {
				future.fail(mfe);
			}
			return;
		}

		redis.getMaxDate(feedId, maxDate -> {
			HttpClient client = createClient(url);
			client.get(url.getPath(), response -> {
				final int status = response.statusCode();
				if (status < 200 || status >= 300) {
					if (future != null) {
						future.fail(new RuntimeException("Could not read feed " + feedUrl + ". Response status code : " + status));
					}
					return;
				}
				response.bodyHandler(buffer -> {
					String xmlFeed = buffer.toString("UTF-8");
					StringReader xmlReader = new StringReader(xmlFeed);
					SyndFeedInput feedInput = new SyndFeedInput();
					try {
						SyndFeed feed = feedInput.build(xmlReader);
						JsonObject feedJson = FeedUtils.toJson(feed);
						log.info(feedJson);
						List<JsonObject> jsonEntries = FeedUtils.toJson(feed.getEntries(), maxDate);
						log.info("Insert " + jsonEntries.size() + " entries into Redis");
						if (jsonEntries.size() == 0) {
							future.complete();
							return;
						}
						vertx.eventBus().publish(feedId, new JsonArray(jsonEntries));
						redis.insertEntries(feedId, jsonEntries, handler -> {
							if (handler.failed()) {
								log.error("Insert failed", handler.cause());
								future.fail(handler.cause());
							} else {
								future.complete();
							}
						});
					} catch (FeedException fe) {
						log.error("Exception while reading feed : " + url.toString(), fe);
						future.fail(fe);
						return;
					}
				});
			}).putHeader(HttpHeaders.ACCEPT, "application/xml").end();
		});
	}

	private HttpClient createClient(URL url) {
		final HttpClientOptions options = new HttpClientOptions();
		options.setDefaultHost(url.getHost());
		return vertx.createHttpClient(options);
	}
}
