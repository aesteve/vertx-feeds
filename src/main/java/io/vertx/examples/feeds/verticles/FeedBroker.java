package io.vertx.examples.feeds.verticles;

import com.rometools.rome.feed.synd.SyndFeed;
import com.rometools.rome.io.FeedException;
import com.rometools.rome.io.SyndFeedInput;
import io.vertx.core.*;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.http.HttpClient;
import io.vertx.core.http.HttpClientOptions;
import io.vertx.core.http.HttpClientResponse;
import io.vertx.core.http.HttpHeaders;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;
import io.vertx.examples.feeds.dao.RedisDAO;
import io.vertx.examples.feeds.utils.RedisUtils;
import io.vertx.examples.feeds.utils.async.MultipleFutures;
import io.vertx.examples.feeds.utils.rss.FeedUtils;
import io.vertx.ext.mongo.MongoClient;
import io.vertx.redis.RedisClient;

import java.io.StringReader;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class FeedBroker extends AbstractVerticle {

	// private final static Long POLL_PERIOD = 3600000l;
	private final static Long POLL_PERIOD = 10000L;
	private final static Logger LOG = LoggerFactory.getLogger(FeedBroker.class);

	private JsonObject config;
	private MongoClient mongo;
	private RedisDAO redis;
	private Long timerId;
	private Map<String, HttpClient> clients;

	@Override
	public void init(Vertx vertx, Context context) {
		super.init(vertx, context);
		config = context.config();
		clients = new HashMap<>();
	}

	@Override
	public void start(Future<Void> future) {
		mongo = MongoClient.createShared(vertx, config.getJsonObject("mongo"));
		redis = new RedisDAO(RedisClient.create(vertx, RedisUtils.createRedisOptions(config.getJsonObject("redis"))));
		fetchFeeds();
		future.complete();
	}

	@Override
	public void stop(Future<Void> future) {
		if (timerId != null) {
			vertx.cancelTimer(timerId);
		}
		mongo.close();
		clients.forEach((url, client) -> client.close());
		future.complete();
	}

	private void fetchFeeds() {
		JsonObject crit = new JsonObject();
		JsonObject gt0 = new JsonObject();
		gt0.put("$gt", 0);
		crit.put("subscriber_count", gt0);
		mongo.find("feeds", crit, result -> {
			if (result.failed()) {
				LOG.error("Could not retrieve feed list from Mongo", result.cause());
			} else {
				this.readFeeds(result.result());
			}
		});
	}

	private void readFeeds(List<JsonObject> feeds) {
		MultipleFutures fetchFeedsFuture = new MultipleFutures();
		feeds.forEach(feed -> fetchFeedsFuture.add(feedFuture -> readFeed(feed, feedFuture)));
		/* No matter if failed or succeeded -> re-launch periodically */
		fetchFeedsFuture.setHandler(fetchResult -> timerId = vertx.setTimer(POLL_PERIOD, timerIdentifier -> fetchFeeds()));
		fetchFeedsFuture.start();
	}

	private void readFeed(JsonObject jsonFeed, Future<Void> future) {
		String feedUrl = jsonFeed.getString("url");
		String feedId = jsonFeed.getString("hash");
		URL url;
		try {
			url = new URL(feedUrl);
		} catch (MalformedURLException mfe) {
			LOG.warn("Invalid url : " + feedUrl, mfe);
			future.fail(mfe);
			return;
		}

		redis.getMaxDate(feedId, maxDate -> getXML(url, response -> {
      int status = response.statusCode();
      if (status < 200 || status >= 300) {
        if (future != null) {
          future.fail(new RuntimeException("Could not read feed " + feedUrl + ". Response status code : " + status));
        }
        return;
      }
      response.bodyHandler(buffer -> this.parseXmlFeed(buffer, maxDate, url, feedId, future));
    }));
	}

	private void parseXmlFeed(Buffer buffer, Date maxDate, URL url, String feedId, Future<Void> future) {
		String xmlFeed = buffer.toString("UTF-8");
		StringReader xmlReader = new StringReader(xmlFeed);
		SyndFeedInput feedInput = new SyndFeedInput();
		try {
			SyndFeed feed = feedInput.build(xmlReader);
			JsonObject feedJson = FeedUtils.toJson(feed);
			LOG.info(feedJson);
			List<JsonObject> jsonEntries = FeedUtils.toJson(feed.getEntries(), maxDate);
			LOG.info("Insert " + jsonEntries.size() + " entries into Redis");
			if (jsonEntries.isEmpty()) {
				future.complete();
				return;
			}
			vertx.eventBus().publish(feedId, new JsonArray(jsonEntries));
			redis.insertEntries(feedId, jsonEntries, handler -> {
				if (handler.failed()) {
					LOG.error("Insert failed", handler.cause());
					future.fail(handler.cause());
				} else {
					future.complete();
				}
			});
		} catch (FeedException fe) {
			LOG.error("Exception while reading feed : " + url.toString(), fe);
			future.fail(fe);
		}
	}

	private void getXML(URL url, Handler<HttpClientResponse> responseHandler) {
		client(url)
		                .get(url.getPath(), responseHandler)
		                .putHeader(HttpHeaders.ACCEPT, "application/xml")
		                .end();
	}

	private HttpClient client(URL url) {
		HttpClient client = clients.get(url.getHost());
		if (client == null) {
			client = createClient(url);
			clients.put(url.getHost(), client);
		}
		return client;
	}

	private HttpClient createClient(URL url) {
		final HttpClientOptions options = new HttpClientOptions();
		options.setDefaultHost(url.getHost());
		return vertx.createHttpClient(options);
	}
}
