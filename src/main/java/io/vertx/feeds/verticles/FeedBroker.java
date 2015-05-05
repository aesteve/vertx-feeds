package io.vertx.feeds.verticles;

import io.vertx.core.AbstractVerticle;
import io.vertx.core.Future;
import io.vertx.core.http.HttpClient;
import io.vertx.core.http.HttpClientOptions;
import io.vertx.core.json.JsonObject;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.impl.LoggerFactory;
import io.vertx.ext.mongo.MongoService;
import io.vertx.feeds.utils.async.MultipleFutures;
import io.vertx.feeds.utils.rss.FeedUtils;

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
	
    private MongoService mongo;
    private Long timerId;

    public FeedBroker(MongoService mongo) {
        this.mongo = mongo;
    }

    @Override
    public void start(Future<Void> future) {
        readFeeds();
        future.complete();
    }

    @Override
    public void stop(Future<Void> future) {
    	if (timerId != null) {
    		vertx.cancelTimer(timerId);
    	}
        future.complete();
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
                	this.timerId = vertx.setTimer(POLL_PERIOD, timerId -> {
                		this.readFeeds();
                	});
        		});
        		futuresByFeed.forEach((feed, future) -> {
        			readFeed(feed.getString("url"), future);
        		});
        	}
        });
    }
    
    public void readFeed(String feedUrl, Future<Void> future) {
    	URL url;
    	try {
    		url = new URL(feedUrl);
    	} catch(MalformedURLException mfe) {
    		log.warn("Invalid url : "+feedUrl, mfe);
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
        			future.fail(new RuntimeException("Could not read feed "+feedUrl+ ". Response status code : "+status));
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
    			} catch(FeedException fe) {
    				future.fail(fe);
    				return;
    			}
    			future.complete();
    		});
    	}).putHeader("Accept", "application/xml")
    	.end();
    }
    
    private HttpClient createClient(URL url) {
    	final HttpClientOptions options = new HttpClientOptions();
    	options.setDefaultHost(url.getHost());
    	return vertx.createHttpClient(options);
    }
}
