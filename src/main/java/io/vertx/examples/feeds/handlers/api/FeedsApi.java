package io.vertx.examples.feeds.handlers.api;

import io.vertx.core.http.HttpServerRequest;
import io.vertx.core.http.HttpServerResponse;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;
import io.vertx.examples.feeds.dao.MongoDAO;
import io.vertx.examples.feeds.dao.RedisDAO;
import io.vertx.examples.feeds.utils.StringUtils;
import io.vertx.ext.web.RoutingContext;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Simple CRUD for handling feeds
 */
public class FeedsApi {

	private static final Logger LOG = LoggerFactory.getLogger(FeedsApi.class);
	public static final String COLUMN_SUBSCRIPTIONS = "subscriptions";
	public static final String COLUMN_FEED_ID = "feedId";

	private final MongoDAO mongo;
	private final RedisDAO redis;
	private final StringUtils strUtils;

	public FeedsApi(MongoDAO mongo, RedisDAO redis) {
		this.mongo = mongo;
		this.redis = redis;
		this.strUtils = new StringUtils();
	}

	public void create(RoutingContext context) {
		JsonObject body = context.getBodyAsJson();
		JsonObject user = context.get("user");
		JsonArray subscriptions = user.getJsonArray(COLUMN_SUBSCRIPTIONS);
		final String urlHash = strUtils.hash256(body.getString("url"));
		body.put("hash", urlHash);
		if (subscriptions != null) {
			boolean alreadySubscribed = subscriptions.stream().anyMatch(subscription ->
					((JsonObject) subscription).getString("hash").equals(urlHash)
			);
			if (alreadySubscribed) {
				context.fail(400);
				return;
			}
		}
		mongo.newSubscription(user, body, result -> {
			if (result.failed()) {
				context.fail(result.cause());
				return;
			}
			context.response().end(body.toString());
		});
	}

	public void retrieve(RoutingContext context) {
		HttpServerRequest request = context.request();
		String feedId = request.getParam(COLUMN_FEED_ID);
		if (feedId == null) {
			context.fail(400);
			return;
		}
		JsonObject user = context.get("user");
		JsonObject subscription = getSubscription(user, context);
		if (subscription != null) {
			context.response().end(subscription.toString());
		}
	}

	public void update(RoutingContext context) {
		context.response().end("TODO");
	}

	public void delete(RoutingContext context) {
		JsonObject user = context.get("user");
		JsonObject subscription = getSubscription(user, context);
		if (subscription == null) {
			return;
		}
		String feedId = context.request().getParam(COLUMN_FEED_ID);
		subscription.put("hash", feedId);
		mongo.unsubscribe(user, subscription, result -> {
			if (result.failed()) {
				context.fail(result.cause());
				return;
			}
			context.response().end(subscription.toString());
		});
	}

	public void list(RoutingContext context) {
		JsonObject user = context.get("user");
		JsonArray subscriptions = user.getJsonArray(COLUMN_SUBSCRIPTIONS);
		if (subscriptions == null) {
			subscriptions = new JsonArray();
		}
		HttpServerResponse response = context.response();
		response.end(subscriptions.toString());
	}

	public void entries(RoutingContext context) {
		JsonObject user = context.get("user");
		JsonObject feed = getSubscription(user, context);
		if (feed != null) {
			String feedId = context.request().getParam(COLUMN_FEED_ID);
			redis.getEntries(feedId, null, null, handler -> {
				if (handler.failed()) {
					context.fail(handler.cause());
				} else {
					JsonArray orig = handler.result();
					List<JsonObject> list = new ArrayList<>(orig.size());
					orig.forEach(val -> {
						LOG.info("found val : " + val);
						list.add(new JsonObject(val.toString()));
					});
					context.response().end(list.toString());
				}
			});
		}
	}

	private static JsonObject getSubscription(JsonObject user, RoutingContext context) {
		String feedId = context.request().getParam(COLUMN_FEED_ID);
		if (feedId == null) {
			context.fail(400);
			return null;
		}
		JsonArray subscriptions = user.getJsonArray(COLUMN_SUBSCRIPTIONS);
		if (subscriptions == null) {
			context.fail(404);
			return null;
		}
		Optional<Object> optional = subscriptions.stream().filter(sub -> {
			JsonObject subscription = (JsonObject) sub;
			return subscription.getString("hash").equals(feedId);
		}).findFirst();
		if (optional.isPresent()) {
			// OK, it's one of user's subscription
			return (JsonObject) optional.get();
		} else { // either the feed doesn't exist, or the user tries to access
			     // someone else's feed -> no distinction
			context.fail(403);
			return null;
		}
	}
}
