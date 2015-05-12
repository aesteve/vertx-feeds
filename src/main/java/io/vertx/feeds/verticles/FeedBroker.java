package io.vertx.feeds.verticles;

import io.vertx.core.AbstractVerticle;
import io.vertx.core.Context;
import io.vertx.core.Future;
import io.vertx.core.Vertx;
import io.vertx.core.http.HttpClient;
import io.vertx.core.http.HttpClientOptions;
import io.vertx.core.json.JsonObject;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.impl.LoggerFactory;
import io.vertx.ext.mongo.MongoClient;
import io.vertx.feeds.utils.async.MultipleFutures;
import io.vertx.feeds.utils.rss.FeedUtils;
import io.vertx.redis.RedisClient;

import java.io.StringReader;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.rometools.rome.feed.synd.SyndFeed;
import com.rometools.rome.io.FeedException;
import com.rometools.rome.io.SyndFeedInput;

public class FeedBroker extends AbstractVerticle {

    private final static Long POLL_PERIOD = 60000l;
    private final static Logger log = LoggerFactory.getLogger(FeedBroker.class);
    private MongoClient mongo;
    private RedisClient redis;
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
        redis = RedisClient.create(vertx, config.getJsonObject("redis"));
    	redis.start(handler -> {
    		if (handler.succeeded()) {
                readFeeds();
                future.complete();
    		} else {
    			future.fail(handler.cause());
    		}
    	});
    }

    @Override
    public void stop(Future<Void> future) {
        if (timerId != null) {
            vertx.cancelTimer(timerId);
        }
        mongo.close();
        redis.stop(handler -> {
    		if (handler.succeeded()) {
                future.complete();
    		} else {
    			future.fail(handler.cause());
    		}
        });
    }

    private void readFeeds() {
        mongo.find("feeds", new JsonObject(), result -> {
            if (result.failed()) {
                if (result.cause() != null) {
                    result.cause().printStackTrace(); // TODO : log instead
                    }
                } else {
                    List<JsonObject> feeds = result.result();
                    MultipleFutures<Void> fetchFeedsFuture = new MultipleFutures<Void>();
                    Map<JsonObject, Future<Void>> futuresByFeed = new HashMap<JsonObject, Future<Void>>(feeds.size());
                    feeds.forEach(feed -> {
                        Future<Void> future = Future.future();
                        fetchFeedsFuture.addFuture(future);
                        futuresByFeed.put(feed, future);
                    });
                    fetchFeedsFuture.setHandler(fetchResult -> {
                    	// No matter if failed or succeeded -> re-launch periodically
                        this.timerId = vertx.setTimer(POLL_PERIOD, timerId -> {
                            this.readFeeds();
                        });
                    });
                    futuresByFeed.forEach((feed, future) -> {
                        readFeed(feed, future);
                    });
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
                    List<JsonObject> jsonEntries = FeedUtils.toJson(feed.getEntries());
                    insertEntriesIntoRedis(feedId, jsonEntries, future);
                } catch (FeedException fe) {
                    future.fail(fe);
                    return;
                }
            });
        }).putHeader("Accept", "application/xml").end();
    }

    private void insertEntriesIntoRedis(String feedId, List<JsonObject> jsonEntries, Future<Void> future) {
    	// TODO : check if entry exists ? like check hash ?
    	Map<String, Double> members = new HashMap<String, Double>(jsonEntries.size());
    	jsonEntries.forEach(entry -> {
    		members.put(entry.toString(), entry.getDouble("score"));
    	});
    	redis.zaddMany(feedId, members, handler -> {
    		if (handler.failed()) {
    			future.fail(handler.cause());
    		} else {
    			future.complete();
    		}
    	});
    }
    
    private HttpClient createClient(URL url) {
        final HttpClientOptions options = new HttpClientOptions();
        options.setDefaultHost(url.getHost());
        return vertx.createHttpClient(options);
    }
}
