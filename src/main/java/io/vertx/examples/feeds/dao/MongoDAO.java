package io.vertx.examples.feeds.dao;

import io.vertx.core.Future;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.mongo.MongoClient;

import java.util.List;
import java.util.Optional;

public class MongoDAO {

    public static final String ID_COLUMN = "_id";
	public static final String TABLE_USERS = "users";
	public static final String TABLE_FEEDS = "feeds";
	public static final String COLUMN_SUBSCRIPTIONS = "subscriptions";
	public static final String COLUMN_SUBSCRIBER_COUNT = "subscriber_count";
	private final MongoClient mongo;

	public MongoDAO(MongoClient mongo) {
		this.mongo = mongo;
	}

	public Future<Optional<JsonObject>> userById(String userId) {
		return mongo
                .findOne(TABLE_USERS, new JsonObject().put(ID_COLUMN, userId), null)
                .map(Optional::ofNullable);
	}

	public Future<Optional<JsonObject>> userByLoginAndPwd(String login, String hashedPwd) {
		var query = new JsonObject()
                .put("login", login)
		        .put("password", hashedPwd);
		return mongo
                .findOne(TABLE_USERS, query, null)
                .map(Optional::ofNullable);
	}

	public Future<String> newUser(String login, String hashedPwd) {
		var user = new JsonObject()
            .put("login", login)
            .put("password", hashedPwd)
            .put(TABLE_FEEDS, new JsonArray());
		return mongo.insert(TABLE_USERS, user);
	}

	public Future<Optional<JsonObject>> getFeedByHash(String feedHash) {
		return mongo
                .findOne(TABLE_FEEDS, new JsonObject().put("hash", feedHash), null)
                .map(Optional::ofNullable);
	}

	@SuppressWarnings("unchecked")
	public Future<Void> unsubscribe(JsonObject user, JsonObject subscription) {
		List<JsonObject> subscriptions = user.getJsonArray(COLUMN_SUBSCRIPTIONS).getList();
		subscriptions.removeIf(sub ->
                sub.getString("hash").equals(subscription.getString("hash"))
        );
		var newSubscriptions = new JsonObject().put("$set", new JsonObject().put(COLUMN_SUBSCRIPTIONS, new JsonArray(subscriptions)));
		var userQuery = new JsonObject().put(ID_COLUMN, user.getString(ID_COLUMN));
		return mongo.findOneAndUpdate(TABLE_USERS, userQuery, newSubscriptions)
                .flatMap(res -> {
                    var feedQuery = new JsonObject().put(ID_COLUMN, subscription.getString(ID_COLUMN));
                    return mongo.findOne(TABLE_FEEDS, feedQuery, null);
                })
                .flatMap(feed -> {
                    var updateQuery = new JsonObject().put(ID_COLUMN, feed.getString(ID_COLUMN));
                    var oldCount = feed.getInteger(COLUMN_SUBSCRIBER_COUNT, 1);
                    subscription.put(COLUMN_SUBSCRIBER_COUNT, oldCount - 1);
                    var updateValue = new JsonObject().put("$set", subscription);
                    return mongo.updateCollection(TABLE_FEEDS, updateQuery, updateValue);
                })
                .mapEmpty();
	}

	public Future<Void> newSubscription(JsonObject user, JsonObject subscription) {
		var urlHash = subscription.getString("hash");
		return getFeedByHash(urlHash)
                .flatMap(maybeFeed -> {
                    if (maybeFeed.isEmpty()) {
                        return createSubscription(subscription, user);
                    } else {
                        return updateSubscription(subscription, user, maybeFeed.get());
                    }
                })
                .mapEmpty();
	}

	private Future<JsonObject> createSubscription(JsonObject subscription, JsonObject user) {
        subscription.put(COLUMN_SUBSCRIBER_COUNT, 1);
        return mongo
                .insert(TABLE_FEEDS, subscription)
                .flatMap(newSubscription -> {
                    subscription.put(ID_COLUMN, newSubscription);
                    return attachSubscriptionToUser(user, subscription);
                });
    }

    private Future<JsonObject> updateSubscription(JsonObject subscription, JsonObject user, JsonObject feed) {
        var feedId = feed.getString(ID_COLUMN);
        var updateQuery = new JsonObject().put(ID_COLUMN, feedId);
        var oldCount = feed.getInteger(COLUMN_SUBSCRIBER_COUNT, 0);
        subscription
                .put(COLUMN_SUBSCRIBER_COUNT, oldCount + 1)
                .put(ID_COLUMN, feedId);
        var updateValue = new JsonObject().put("$set", subscription);
        return mongo
                .findOneAndUpdate(TABLE_FEEDS, updateQuery, updateValue)
                .flatMap(updateHandler -> attachSubscriptionToUser(user, subscription));
    }

	private Future<JsonObject> attachSubscriptionToUser(JsonObject user, JsonObject subscription) {
		var subscriptions = user.getJsonArray(COLUMN_SUBSCRIPTIONS, new JsonArray()).add(subscription);
		var query = new JsonObject().put(ID_COLUMN, user.getString(ID_COLUMN));
		var newSubscriptions = new JsonObject()
                .put("$set", new JsonObject().put(COLUMN_SUBSCRIPTIONS, subscriptions));
		return mongo.findOneAndUpdate(TABLE_USERS, query, newSubscriptions);
	}

}
