package io.vertx.feeds.api;

import java.util.List;

import io.vertx.core.http.HttpServerResponse;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.apex.RoutingContext;
import io.vertx.ext.apex.Session;
import io.vertx.ext.mongo.MongoService;
import io.vertx.feeds.utils.StringUtils;

public class AuthenticationApi {

    private MongoService mongo;
    private StringUtils strUtils;

    public AuthenticationApi(MongoService mongo) {
        this.mongo = mongo;
        this.strUtils = new StringUtils();
    }

    public void register(RoutingContext context) {
        final String login = context.request().getParam("login");
        final String pwd = context.request().getParam("password");
        final JsonObject user = new JsonObject();
        user.put("login", login);
        user.put("password", strUtils.hash256(pwd));
        user.put("feeds", new JsonArray());
        mongo.insert("users", user, result -> {
            if (result.failed()) {
                context.fail(result.cause());
            } else {
                redirectTo(context, "/index.hbs");
            }
        });
    }

    public void login(RoutingContext context) {
        final String login = context.request().getParam("login");
        final String pwd = context.request().getParam("password");
        final JsonObject query = new JsonObject();
        query.put("login", login);
        query.put("password", strUtils.hash256(pwd));
        mongo.find("users", query, result -> {
            if (result.failed()) {
                context.fail(result.cause());
            } else {
                List<JsonObject> users = result.result();
                if (users.isEmpty()) {
                    redirectTo(context, "/login.hbs");
                } else {
                    JsonObject user = users.get(0);
                    Session session = context.session();
                    String accessToken = strUtils.generateToken();
                    session.put("accessToken", accessToken);
                    context.vertx().sharedData().getLocalMap("access_tokens").put(accessToken, user.getString("_id"));
                    redirectTo(context, "/index.hbs");
                }
            }
        });

    }

    public void logout(RoutingContext context) {
        final Session session = context.session();
        session.remove("login");
        redirectTo(context, "/index.hbs");
    }

    private void redirectTo(RoutingContext context, String url) {
        HttpServerResponse response = context.response();
        response.setStatusCode(303);
        response.headers().add("Location", url);
        response.end();
    }

}
