package io.vertx.examples.feeds.verticles;

import io.vertx.core.AbstractVerticle;
import io.vertx.core.Context;
import io.vertx.core.Promise;
import io.vertx.core.Vertx;
import io.vertx.core.http.HttpServer;
import io.vertx.core.http.HttpServerOptions;
import io.vertx.core.json.JsonObject;
import io.vertx.examples.feeds.dao.MongoDAO;
import io.vertx.examples.feeds.dao.RedisDAO;
import io.vertx.examples.feeds.handlers.UserContextHandler;
import io.vertx.examples.feeds.handlers.api.AuthenticationApi;
import io.vertx.examples.feeds.handlers.api.FeedsApi;
import io.vertx.examples.feeds.utils.RedisUtils;
import io.vertx.ext.bridge.PermittedOptions;
import io.vertx.ext.mongo.MongoClient;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.handler.BodyHandler;
import io.vertx.ext.web.handler.ErrorHandler;
import io.vertx.ext.web.handler.SessionHandler;
import io.vertx.ext.web.handler.StaticHandler;
import io.vertx.ext.web.handler.TemplateHandler;
import io.vertx.ext.web.handler.sockjs.BridgeOptions;
import io.vertx.ext.web.handler.sockjs.SockJSHandler;
import io.vertx.ext.web.sstore.LocalSessionStore;
import io.vertx.ext.web.sstore.SessionStore;
import io.vertx.ext.web.templ.handlebars.HandlebarsTemplateEngine;
import io.vertx.redis.client.Redis;
import io.vertx.redis.client.RedisAPI;

import static io.vertx.core.http.HttpHeaders.CONTENT_TYPE;

public class WebServer extends AbstractVerticle {

	public static final String APPLICATION_JSON = "application/json";
	private HttpServer server;

	private AuthenticationApi authApi;
	private FeedsApi feedsApi;
	private UserContextHandler userContextHandler;
	private MongoDAO mongo;
	private JsonObject config;

	@Override
	public void init(Vertx vertx, Context context) {
		super.init(vertx, context);
		config = context.config();
	}

	@Override
	public void start(Promise<Void> future) {
		mongo = new MongoDAO(MongoClient.createShared(vertx, config.getJsonObject("mongo")));
		var redisCli = Redis.createClient(vertx, RedisUtils.createRedisOptions(config.getJsonObject("redis")));
		var redis = new RedisDAO(RedisAPI.api(redisCli));
		authApi = new AuthenticationApi(mongo);
		feedsApi = new FeedsApi(mongo, redis);
		server = vertx.createHttpServer(createOptions());
		server.requestHandler(createRouter())
                .listen()
                .<Void>mapEmpty()
                .setHandler(future);
	}

	@Override
	public void stop(Promise<Void> future) {
		if (server == null) {
			future.complete();
			return;
		}
		server.close(future);
	}

	private static HttpServerOptions createOptions() {
		return new HttpServerOptions()
                .setHost("localhost")
                .setPort(9000);
	}

	private Router createRouter() {
		var router = Router.router(vertx);
		router.route().failureHandler(ErrorHandler.create(true));

		/* Static resources */
		staticHandler(router);

		/* Session / cookies for users */
		var sessionStore = LocalSessionStore.create(vertx);
		var sessionHandler = SessionHandler.create(sessionStore);
		router.route().handler(sessionHandler);
		userContextHandler = new UserContextHandler(mongo);

		/* Dynamic pages */
		dynamicPages(router);

		/* API */
		router.mountSubRouter("/api", apiRouter());

		/* SockJS / EventBus */
		router.route("/eventbus/*").handler(eventBusHandler());

		return router;
	}

	private SockJSHandler eventBusHandler() {
		var sockJSHandler = SockJSHandler.create(vertx);
		sockJSHandler.bridge(
		        new BridgeOptions().addOutboundPermitted(new PermittedOptions())
        );
		return sockJSHandler;
	}

	private static void staticHandler(Router router) {
		router.route("/assets/*").handler(StaticHandler
                .create()
                .setCachingEnabled(false));
	}

	private void dynamicPages(Router router) {
		var templateHandler = TemplateHandler.create(HandlebarsTemplateEngine.create(vertx));
		router.get("/private/*").handler(userContextHandler::fromSession);
		router.getWithRegex(".+\\.hbs").handler(rc -> {
			var session = rc.session();
			rc.data().put("userLogin", session.get("login")); /* in order to greet him */
			rc.data().put("accessToken", session.get("access_token")); /* for api calls */
			rc.next();
		});
		router.getWithRegex(".+\\.hbs").handler(templateHandler);
	}

	private Router apiRouter() {
		/*
		 * TODO : provide authentication through the AuthService / AuthProvider instead of a custom api handler
		 * TODO : every page except login must be private TODO : use FormLoginHandler for the actual login form TODO : use RedirectAuthHandler for "/private"
		 */
		var router = Router.router(vertx);
		router.route().consumes(APPLICATION_JSON);
		router.route().produces(APPLICATION_JSON);
		router.route().handler(BodyHandler.create());
		router.route().handler(rc -> {
			rc.response().headers().add(CONTENT_TYPE, APPLICATION_JSON);
			rc.next();
		});

		/* login / user-related stuff : no token needed */
		router.post("/register").handler(authApi::register);
		router.post("/login").handler(authApi::login);
		router.post("/logout").handler(authApi::logout);

		/* API to deal with feeds : token required */
		router.route("/feeds*").handler(userContextHandler::fromApiToken);
		router.post("/feeds").handler(feedsApi::create);
		router.get("/feeds").handler(feedsApi::list);
		router.get("/feeds/:feedId").handler(feedsApi::retrieve);
		router.get("/feeds/:feedId/entries").handler(feedsApi::entries);
		router.delete("/feeds/:feedId").handler(feedsApi::delete);

		return router;
	}

}
