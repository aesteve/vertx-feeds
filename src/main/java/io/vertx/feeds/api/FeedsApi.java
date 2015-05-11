package io.vertx.feeds.api;

import io.vertx.core.Vertx;
import io.vertx.core.http.HttpServerResponse;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.apex.RoutingContext;
import io.vertx.ext.mongo.MongoClient;
import io.vertx.feeds.utils.StringUtils;
import io.vertx.feeds.utils.UserUtils;

/**
 * Simple CRUD for handling feeds
 */
public class FeedsApi {

    private MongoClient mongo;
    private StringUtils strUtils;

    public FeedsApi(JsonObject mongoConfig) {
        this.mongo = MongoClient.createShared(Vertx.vertx(), mongoConfig);
        this.strUtils = new StringUtils();
    }

    public void create(RoutingContext context) {
        JsonObject body = context.getBodyAsJson();
        UserUtils.getUserFromContext(context, mongo, user -> {
            JsonArray subscriptions = user.getJsonArray("subscriptions");
            final String urlHash = strUtils.hash256(body.getString("url"));
            if (subscriptions == null) {
                subscriptions = new JsonArray();
            }
            body.put("hash", urlHash);
            subscriptions.add(body);
            JsonObject query = new JsonObject();
            query.put("_id", user.getString("_id"));
            JsonObject newSubscriptions = new JsonObject();
            newSubscriptions.put("$set", new JsonObject().put("subscriptions", subscriptions));
            mongo.update("users", query, newSubscriptions, result -> {
                if (result.failed()) {
                    context.fail(result.cause());
                } else {
                    /* TODO : read first */
                    mongo.insert("feeds", body, insertResult -> {
                        HttpServerResponse response = context.response();
                        response.end(body.toString());
                    });
                }
            });
        });
    }

    public void retrieve(RoutingContext context) {
        context.response().end("TODO");
    }

    public void update(RoutingContext context) {
        context.response().end("TODO");
    }

    public void delete(RoutingContext context) {
        context.response().end("TODO");
    }

    public void list(RoutingContext context) {
        UserUtils.getUserFromContext(context, mongo, user -> {
            JsonArray subscriptions = user.getJsonArray("subscriptions");
            if (subscriptions == null) {
                subscriptions = new JsonArray();
            }
            HttpServerResponse response = context.response();
            response.headers().add("Content-Type", "application/json");
            response.end(subscriptions.toString());
        });
    }

}
