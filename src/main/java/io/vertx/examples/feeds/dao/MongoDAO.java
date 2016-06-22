package io.vertx.examples.feeds.dao;

import io.vertx.core.AsyncResult;
import io.vertx.core.Handler;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.mongo.MongoClient;

import java.util.List;

public class MongoDAO {

	public static final String TABLE_USERS = "users";
	public static final String TABLE_FEEDS = "feeds";
	public static final String COLUMN_SUBSCRIPTIONS = "subscriptions";
	public static final String COLUMN_SUBSCRIBER_COUNT = "subscriber_count";
	private final MongoClient mongo;

	public MongoDAO(MongoClient mongo) {
		this.mongo = mongo;
	}

	public void userById(String userId, Handler<AsyncResult<JsonObject>> handler) {
		JsonObject query = new JsonObject();
		query.put("_id", userId);
		mongo.findOne(TABLE_USERS, query, null, handler);
	}

	public void userByLoginAndPwd(String login, String hashedPwd, Handler<AsyncResult<JsonObject>> handler) {
		JsonObject query = new JsonObject();
		query.put("login", login);
		query.put("password", hashedPwd);
		mongo.findOne(TABLE_USERS, query, null, handler);
	}

	public void newUser(String login, String hashedPwd, Handler<AsyncResult<String>> handler) {
		final JsonObject user = new JsonObject();
		user.put("login", login);
		user.put("password", hashedPwd);
		user.put(TABLE_FEEDS, new JsonArray());
		mongo.insert(TABLE_USERS, user, handler);
	}

	public void getFeed(String feedHash, Handler<AsyncResult<JsonObject>> handler) {
		JsonObject query = new JsonObject();
		query.put("hash", feedHash);
		mongo.findOne(TABLE_FEEDS, query, null, handler);
	}

	public void updateFeed(String feedHash, JsonObject newValue, Handler<AsyncResult<Void>> handler) {
		JsonObject query = new JsonObject();
		query.put("hash", feedHash);
		JsonObject updateQuery = new JsonObject();
		updateQuery.put("$set", newValue);
		mongo.update(TABLE_FEEDS, query, updateQuery, handler);
	}

	@SuppressWarnings("unchecked")
	public void unsubscribe(JsonObject user, JsonObject subscription, Handler<AsyncResult<?>> handler) {
		List<JsonObject> subscriptions = user.getJsonArray(COLUMN_SUBSCRIPTIONS).getList();
		subscriptions.removeIf(sub -> {
			return sub.getString("hash").equals(subscription.getString("hash"));
		});
		JsonObject newSubscriptions = new JsonObject();
		newSubscriptions.put("$set", new JsonObject().put(COLUMN_SUBSCRIPTIONS, new JsonArray(subscriptions)));
		JsonObject userQuery = new JsonObject();
		userQuery.put("_id", user.getString("_id"));
		mongo.update(TABLE_USERS, userQuery, newSubscriptions, updateHandler -> {
			if (updateHandler.failed()) {
				handler.handle(updateHandler);
				return;
			}
			JsonObject feedQuery = new JsonObject();
			feedQuery.put("_id", subscription.getString("_id"));
			mongo.findOne(TABLE_FEEDS, feedQuery, null, duplicateHandler -> {
				if (duplicateHandler.failed()) {
					handler.handle(duplicateHandler);
					return;
				}
				JsonObject feed = duplicateHandler.result();
				JsonObject updateQuery = new JsonObject();
				Integer oldCount = feed.getInteger(COLUMN_SUBSCRIBER_COUNT, 1);
				subscription.put(COLUMN_SUBSCRIBER_COUNT, oldCount - 1);
				updateQuery.put("_id", feed.getString("_id"));
				JsonObject updateValue = new JsonObject();
				updateValue.put("$set", subscription);
				mongo.update(TABLE_FEEDS, updateQuery, updateValue, handler::handle);
			});

		});
	}

	public void newSubscription(JsonObject user, JsonObject subscription, Handler<AsyncResult<?>> handler) {
		String urlHash = subscription.getString("hash");
		JsonObject findQuery = new JsonObject();
		findQuery.put("hash", urlHash);
		mongo.findOne(TABLE_FEEDS, findQuery, null, findResult -> {
			if (findResult.failed()) {
				handler.handle(findResult);
				return;
			}
			JsonObject existingFeed = findResult.result();
			if (existingFeed == null) {
				subscription.put(COLUMN_SUBSCRIBER_COUNT, 1);
				mongo.insert(TABLE_FEEDS, subscription, insertResult -> {
					if (insertResult.failed()) {
						handler.handle(insertResult);
						return;
					}
					subscription.put("_id", insertResult.result());
					attachSubscriptionToUser(user, subscription, handler);
				});
			} else {
				JsonObject updateQuery = new JsonObject();
				Integer oldCount = existingFeed.getInteger(COLUMN_SUBSCRIBER_COUNT, 0);
				subscription.put(COLUMN_SUBSCRIBER_COUNT, oldCount + 1);
				updateQuery.put("_id", existingFeed.getString("_id"));
				subscription.put("_id", existingFeed.getString("_id"));
				JsonObject updateValue = new JsonObject();
				updateValue.put("$set", subscription);
				mongo.update(TABLE_FEEDS, updateQuery, updateValue, updateHandler -> {
					if (updateHandler.failed()) {
						handler.handle(updateHandler);
						return;
					}
					attachSubscriptionToUser(user, subscription, handler);
				});
			}
		});
	}

	private void attachSubscriptionToUser(JsonObject user, JsonObject subscription, Handler<AsyncResult<?>> handler) {
		JsonArray subscriptions = user.getJsonArray(COLUMN_SUBSCRIPTIONS, new JsonArray());
		subscriptions.add(subscription);
		JsonObject query = new JsonObject();
		query.put("_id", user.getString("_id"));
		JsonObject newSubscriptions = new JsonObject();
		newSubscriptions.put("$set", new JsonObject().put(COLUMN_SUBSCRIPTIONS, subscriptions));
		mongo.update(TABLE_USERS, query, newSubscriptions, handler::handle);
	}

}
