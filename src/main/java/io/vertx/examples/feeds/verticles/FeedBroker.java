package io.vertx.examples.feeds.verticles;

import com.rometools.rome.io.FeedException;
import com.rometools.rome.io.SyndFeedInput;
import io.vertx.core.AbstractVerticle;
import io.vertx.core.CompositeFuture;
import io.vertx.core.Context;
import io.vertx.core.Future;
import io.vertx.core.Promise;
import io.vertx.core.Vertx;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.http.HttpClient;
import io.vertx.core.http.HttpClientOptions;
import io.vertx.core.http.HttpHeaders;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.examples.feeds.dao.RedisDAO;
import io.vertx.examples.feeds.utils.RedisUtils;
import io.vertx.examples.feeds.utils.rss.FeedConverters;
import io.vertx.ext.mongo.MongoClient;
import io.vertx.ext.web.client.WebClient;
import io.vertx.ext.web.client.WebClientOptions;
import io.vertx.redis.client.Redis;
import io.vertx.redis.client.RedisAPI;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.StringReader;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public class FeedBroker extends AbstractVerticle {

	// private final static Long POLL_PERIOD = 3600000l;
	private final static Long POLL_PERIOD = 10000L;
	private final static Logger LOG = LoggerFactory.getLogger(FeedBroker.class);

	private JsonObject config;
	private MongoClient mongo;
	private RedisDAO redis;
	private Long timerId;
	private Map<String, WebClient> clients;

	@Override
	public void init(Vertx vertx, Context context) {
		super.init(vertx, context);
		config = context.config();
		clients = new HashMap<>();
	}

	@Override
	public void start(Promise<Void> promise) {
		mongo = MongoClient.createShared(vertx, config.getJsonObject("mongo"));
        Redis.createClient(vertx, RedisUtils.createRedisOptions(config.getJsonObject("redis")))
            .connect()
                .map(api -> {
                    this.redis = new RedisDAO(RedisAPI.api(api));
                    fetchFeedsPeriodically();
                    return api;
                })
                .<Void>mapEmpty()
                .setHandler(promise);
	}

	@Override
	public void stop() {
		if (timerId != null) {
			vertx.cancelTimer(timerId);
		}
		mongo.close();
		clients.values().forEach(WebClient::close);
	}

	private void fetchFeedsPeriodically() {
    	var clientsWithAtLeast1Subscription = new JsonObject().put("subscriber_count", new JsonObject().put("$gt", 0));
    	vertx.setPeriodic(POLL_PERIOD, timerId -> {
    	    this.timerId = timerId;
            mongo.find("feeds", clientsWithAtLeast1Subscription)
                    .setHandler(res -> {
                        if (res.failed()) {
                            LOG.error("Could not fetch feeds from Mongo", res.cause());
                            return;
                        }
                        this.readFeeds(res.result())
                                .setHandler(res2 -> {
                                    if (res2.failed()) {
                                        LOG.error("Could not read feeds", res.cause());
                                        return;
                                    }
                                    LOG.info("Successfully read feeds");
                                });
                    });
		});
	}

	private CompositeFuture readFeeds(List<JsonObject> feeds) {
	    return CompositeFuture.all(
                feeds.stream()
                        .map(this::readFeed)
                        .collect(Collectors.toList())
        );
    }

	private Future<Void> readFeed(JsonObject jsonFeed) {
		var feedUrl = jsonFeed.getString("url");
		var feedId = jsonFeed.getString("hash");
		URL url;
		try {
			url = new URL(feedUrl);
		} catch (MalformedURLException mfe) {
			LOG.warn("Invalid url : " + feedUrl, mfe);
			return Future.failedFuture(mfe);
		}
		return redis
                .getMaxDate(feedId)
                .flatMap(maxDate ->
                    getXML(url).flatMap(buffer ->
                            this.parseXmlFeed(buffer, maxDate, url, feedId)
                    )
                )
                .mapEmpty();
	}

	private Future<Void> parseXmlFeed(Buffer buffer, Date maxDate, URL url, String feedId) {
		var xmlFeed = buffer.toString("UTF-8");
		var xmlReader = new StringReader(xmlFeed);
		var feedInput = new SyndFeedInput();
		try {
			var feed = feedInput.build(xmlReader);
			var feedJson = FeedConverters.toJson(feed);
			LOG.info("Read feed {}", feedJson);
			var jsonEntries = FeedConverters.toJson(feed.getEntries(), maxDate);
			LOG.info("Insert {} entries into Redis", jsonEntries.size());
			if (jsonEntries.isEmpty()) {
				return Future.succeededFuture();
			}
			vertx.eventBus().publish(feedId, new JsonArray(jsonEntries));
			return redis.insertEntries(feedId, jsonEntries).mapEmpty();
		} catch (FeedException fe) {
			LOG.error("Exception while reading feed : " + url.toString(), fe);
			return Future.failedFuture(fe);
		}
	}

	private Future<Buffer> getXML(URL url) {
		return client(url)
            .get(url.getPath())
            .putHeader(HttpHeaders.ACCEPT.toString(), "application/xml")
            .send()
            .flatMap(response ->  {
                var status = response.statusCode();
                if (status < 200 || status >= 300) {
                    return Future.failedFuture(new RuntimeException("Could not read feed " + url + ". Response status code : " + status));
                }
                return Future.succeededFuture(response.bodyAsBuffer());
            });
	}

	private WebClient client(URL url) {
		var client = clients.get(url.getHost());
		if (client == null) {
			client = createClient(url);
			clients.put(url.getHost(), client);
		}
		return client;
	}

	private WebClient createClient(URL url) {
		return WebClient.create(vertx,
		        new WebClientOptions().setDefaultHost(url.getHost())
        );
	}
}
