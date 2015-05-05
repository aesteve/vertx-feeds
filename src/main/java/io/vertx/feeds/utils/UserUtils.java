package io.vertx.feeds.utils;

import io.vertx.core.Handler;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.apex.RoutingContext;
import io.vertx.ext.apex.Session;
import io.vertx.ext.mongo.MongoClient;

import java.util.List;

public class UserUtils {

    public static void getUserFromContext(RoutingContext context, MongoClient mongo, Handler<JsonObject> userHandler) {
        Session session = context.session();
        String userId = session.get("userId");
        if (userId == null) {
            final String token = context.request().params().get("accessToken");
            if (token == null) {
                context.fail(401);
                return;
            }
            userId = (String) context.vertx().sharedData().getLocalMap("access_tokens").get(token);
            if (userId == null) {
                context.fail(403);
                return;
            }
        }
        final JsonObject query = new JsonObject();
        query.put("_id", userId);
        mongo.find("users", query, mongoHandler -> {
            if (mongoHandler.failed()) {
                context.fail(mongoHandler.cause());
            } else {
                List<JsonObject> users = mongoHandler.result();
                if (users.isEmpty()) {
                    context.fail(403);
                    return;
                } else {
                    JsonObject user = users.get(0);
                    userHandler.handle(user);
                }
            }
        });
    }
}
