package io.vertx.examples.feeds.dao;

import io.vertx.core.AsyncResult;
import io.vertx.core.Handler;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.mongo.MongoClient;

public class MongoDAO {

    private MongoClient mongo;

    public MongoDAO(MongoClient mongo) {
        this.mongo = mongo;
    }

    public void userById(String userId, Handler<AsyncResult<JsonObject>> handler) {
        JsonObject query = new JsonObject();
        query.put("_id", userId);
        mongo.findOne("users", query, null, handler);
    }

    public void userByLoginAndPwd(String login, String hashedPwd, Handler<AsyncResult<JsonObject>> handler) {
        JsonObject query = new JsonObject();
        query.put("login", login);
        query.put("password", hashedPwd);
        mongo.findOne("users", query, null, handler);
    }

    public void newUser(String login, String hashedPwd, Handler<AsyncResult<String>> handler) {
        final JsonObject user = new JsonObject();
        user.put("login", login);
        user.put("password", hashedPwd);
        user.put("feeds", new JsonArray());
        mongo.insert("users", user, handler);
    }

    public void getFeed(String feedHash, Handler<AsyncResult<JsonObject>> handler) {
        JsonObject query = new JsonObject();
        query.put("hash", feedHash);
        mongo.findOne("feeds", query, null, handler);
    }

    public void updateFeed(String feedHash, JsonObject newValue, Handler<AsyncResult<Void>> handler) {
        JsonObject query = new JsonObject();
        query.put("hash", feedHash);
        JsonObject updateQuery = new JsonObject();
        updateQuery.put("$set", newValue);
        mongo.update("feeds", query, updateQuery, handler);
    }

    public void unsubscribe(JsonObject user, JsonObject subscription, Handler<AsyncResult<?>> handler) {
        JsonArray subscriptions = user.getJsonArray("subscriptions");
        subscriptions.remove(subscription);
        JsonObject newSubscriptions = new JsonObject();
        newSubscriptions.put("$set", new JsonObject().put("subscriptions", subscriptions));
        JsonObject userQuery = new JsonObject();
        userQuery.put("_id", user.getString("_id"));
        mongo.update("users", userQuery, newSubscriptions, updateHandler -> {
            if (updateHandler.failed()) {
                handler.handle(updateHandler);
                return;
            }
            JsonObject feedQuery = new JsonObject();
            feedQuery.put("hash", subscription.getString("hash"));
            mongo.findOne("feeds", feedQuery, null, duplicateHandler -> {
                if (duplicateHandler.failed()) {
                    handler.handle(duplicateHandler);
                    return;
                }
                JsonObject feed = duplicateHandler.result();
                JsonObject updateQuery = new JsonObject();
                Integer oldCount = feed.getInteger("subscriber_count", 1);
                subscription.put("subscriber_count", oldCount - 1);
                updateQuery.put("_id", feed.getString("_id"));
                JsonObject updateValue = new JsonObject();
                updateValue.put("$set", subscription);
                mongo.update("feeds", updateQuery, updateValue, feedUpdateHandler -> {
                    handler.handle(feedUpdateHandler);
                });
            });

        });
    }

    public void newSubscription(JsonObject user, JsonObject subscription, Handler<AsyncResult<?>> handler) {
        JsonArray subscriptions = user.getJsonArray("subscriptions", new JsonArray());
        subscriptions.add(subscription);
        String urlHash = subscription.getString("hash");
        JsonObject query = new JsonObject();
        query.put("_id", user.getString("_id"));
        JsonObject newSubscriptions = new JsonObject();
        newSubscriptions.put("$set", new JsonObject().put("subscriptions", subscriptions));
        mongo.update("users", query, newSubscriptions, result -> {
            if (result.failed()) {
                handler.handle(result);
                return;
            }
            JsonObject findQuery = new JsonObject();
            findQuery.put("hash", urlHash);
            mongo.findOne("feeds", findQuery, null, findResult -> {
                if (findResult.failed()) {
                    handler.handle(findResult);
                    return;
                }
                JsonObject existingFeed = findResult.result();
                if (existingFeed == null) {
                    subscription.put("subscriber_count", 1);
                    mongo.insert("feeds", subscription, insertResult -> {
                        handler.handle(insertResult);
                    });
                } else {
                    JsonObject updateQuery = new JsonObject();
                    Integer oldCount = existingFeed.getInteger("subscriber_count", 0);
                    subscription.put("subscriber_count", oldCount + 1);
                    updateQuery.put("_id", existingFeed.getString("_id"));
                    JsonObject updateValue = new JsonObject();
                    updateValue.put("$set", subscription);
                    mongo.update("feeds", updateQuery, updateValue, updateHandler -> {
                        handler.handle(updateHandler);
                    });
                }
            });
        });

    }
}
