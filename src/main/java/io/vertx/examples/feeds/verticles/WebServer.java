package io.vertx.examples.feeds.verticles;

import io.vertx.core.AbstractVerticle;
import io.vertx.core.Context;
import io.vertx.core.Future;
import io.vertx.core.Vertx;
import io.vertx.core.http.HttpServer;
import io.vertx.core.http.HttpServerOptions;
import io.vertx.core.json.JsonObject;
import io.vertx.examples.feeds.api.AuthenticationApi;
import io.vertx.examples.feeds.api.FeedsApi;
import io.vertx.ext.apex.Router;
import io.vertx.ext.apex.Session;
import io.vertx.ext.apex.handler.BodyHandler;
import io.vertx.ext.apex.handler.CookieHandler;
import io.vertx.ext.apex.handler.ErrorHandler;
import io.vertx.ext.apex.handler.SessionHandler;
import io.vertx.ext.apex.handler.StaticHandler;
import io.vertx.ext.apex.handler.TemplateHandler;
import io.vertx.ext.apex.sstore.LocalSessionStore;
import io.vertx.ext.apex.sstore.SessionStore;
import io.vertx.ext.apex.templ.HandlebarsTemplateEngine;
import io.vertx.ext.mongo.MongoClient;
import io.vertx.redis.RedisClient;

public class WebServer extends AbstractVerticle {

    private HttpServer server;

    private AuthenticationApi authApi;
    private FeedsApi feedsApi;
    private MongoClient mongo;
    private RedisClient redis;
    private JsonObject config;

    @Override
    public void init(Vertx vertx, Context context) {
        super.init(vertx, context);
        config = context.config();
    }

    @Override
    public void start(Future<Void> future) {
        mongo = MongoClient.createShared(vertx, config.getJsonObject("mongo"));
        redis = RedisClient.create(vertx, config.getJsonObject("redis"));
        authApi = new AuthenticationApi(mongo);
        feedsApi = new FeedsApi(mongo, redis);
        redis.start(handler -> {
            server = vertx.createHttpServer(createOptions());
            server.requestHandler(createRouter()::accept);
            server.listen(result -> {
                if (result.succeeded()) {
                    future.complete();
                } else {
                    future.fail(result.cause());
                }
            });
        });
    }

    @Override
    public void stop(Future<Void> future) {
        if (server == null) {
            future.complete();
            return;
        }
        server.close(result -> {
            if (result.failed()) {
                future.fail(result.cause());
            } else {
                future.complete();
            }
        });
    }

    private HttpServerOptions createOptions() {
        HttpServerOptions options = new HttpServerOptions();
        options.setHost("localhost");
        options.setPort(9000);
        return options;
    }

    private Router createRouter() {
        Router router = Router.router(vertx);
        router.route().failureHandler(ErrorHandler.create(true)); /* display exception details */

        /* Static resources */
        StaticHandler staticHandler = StaticHandler.create();
        staticHandler.setCachingEnabled(false);
        router.route("/assets/*").handler(staticHandler);

        router.route().handler(CookieHandler.create());
        SessionStore sessionStore = LocalSessionStore.create(vertx);
        SessionHandler sessionHandler = SessionHandler.create(sessionStore);
        router.route().handler(sessionHandler);

        /* Templates */
        HandlebarsTemplateEngine hbsEngine = HandlebarsTemplateEngine.create();
        hbsEngine.setMaxCacheSize(0); // no cache since we wan't hot-reload for templates
        TemplateHandler templateHandler = TemplateHandler.create(hbsEngine);
        router.getWithRegex(".+\\.hbs").handler(context -> {
            final Session session = context.session();
            context.data().put("userLogin", session.get("login")); /* in order to greet him */
            context.data().put("accessToken", session.get("accessToken")); /* for api calls */
            context.next();
        });
        router.getWithRegex(".+\\.hbs").handler(templateHandler);

        /* API */
        router.mountSubRouter("/api", apiRouter());

        return router;
    }

    private Router apiRouter() {
        /*
         * TODO : provide authentication through the AuthService / AuthProvider instead of a custom api handler
         * TODO : every page except login must be private
         * TODO : use FormLoginHandler for the actual login form
         * TODO : use RedirectAuthHandler for "/private"
         * 
         * router.route().handler(CookieHandler.create());
         * router.route().handler(SessionHandler.create(LocalSessionStore.create(vertx)));
         * 
         * AuthProvider provider =
         */
        Router router = Router.router(vertx);
        router.route().consumes("application/json");
        router.route().produces("application/json");
        router.route().handler(context -> {
            context.response().headers().add("Content-Type", "application/json");
            context.next();
        });
        router.route("/*").handler(BodyHandler.create());

        /* login / user-related stuff */
        router.post("/register").handler(authApi::register);
        router.post("/login").handler(authApi::login);
        router.post("/logout").handler(authApi::logout);

        /* API to deal with feeds */
        router.post("/feeds").handler(feedsApi::create);
        router.get("/feeds").handler(feedsApi::list);
        router.get("/feeds/:feedId").handler(feedsApi::retrieve);
        router.put("/feeds/:feedId").handler(feedsApi::update);
        router.get("/feeds/:feedId/entries").handler(feedsApi::entries);
        router.delete("/feeds/:feedId").handler(feedsApi::delete);

        return router;
    }
}
