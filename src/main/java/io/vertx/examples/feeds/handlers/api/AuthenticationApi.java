package io.vertx.examples.feeds.handlers.api;

import io.vertx.core.http.HttpServerResponse;
import io.vertx.core.json.JsonObject;
import io.vertx.examples.feeds.dao.MongoDAO;
import io.vertx.examples.feeds.utils.StringUtils;
import io.vertx.ext.web.RoutingContext;
import io.vertx.ext.web.Session;

public class AuthenticationApi {

	private MongoDAO mongo;
	private StringUtils strUtils;

	public AuthenticationApi(MongoDAO mongo) {
		this.mongo = mongo;
		this.strUtils = new StringUtils();
	}

	public void register(RoutingContext context) {
		final String login = context.request().getParam("login");
		final String pwd = context.request().getParam("password");
		mongo.newUser(login, strUtils.hash256(pwd), result -> {
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
		mongo.userByLoginAndPwd(login, strUtils.hash256(pwd), result -> {
			if (result.failed()) {
				context.fail(result.cause());
				return;
			}
			JsonObject user = result.result();
			if (user == null) {
				redirectTo(context, "/login.hbs");
				return;
			}
			Session session = context.session();
			String accessToken = strUtils.generateToken();
			session.put("accessToken", accessToken);
			session.put("login", login);
			session.put("userId", user.getString("_id"));
			context.vertx().sharedData().getLocalMap("access_tokens").put(accessToken, user.getString("_id"));
			redirectTo(context, "/index.hbs");
		});
	}

	public void logout(RoutingContext context) {
		final Session session = context.session();
		session.remove("login");
		session.remove("userId");
		String accessToken = session.get("accessToken");
		if (accessToken != null) {
			context.vertx().sharedData().getLocalMap("access_tokens").remove(accessToken);
		}
		session.remove("accessToken");
		redirectTo(context, "/index.hbs");
	}

	private static void redirectTo(RoutingContext context, String url) {
		HttpServerResponse response = context.response();
		response.setStatusCode(303);
		response.headers().add("Location", url);
		response.end();
	}

}
