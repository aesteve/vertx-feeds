package io.vertx.examples.feeds.handlers.api;

import io.vertx.core.http.HttpServerResponse;
import io.vertx.core.json.JsonObject;
import io.vertx.examples.feeds.dao.MongoDAO;
import io.vertx.examples.feeds.utils.StringUtils;
import io.vertx.ext.web.RoutingContext;
import io.vertx.ext.web.Session;

public class AuthenticationApi {

	private final MongoDAO mongo;
	private final StringUtils strUtils;

	private static final String LOGIN = "login";
	private static final String PWD = "password";
	private static final String ACCESS_TOKEN = "access_token";
	private static final String INDEX = "/index.hbs";
	private static final String USER_ID = "userId";


	public AuthenticationApi(MongoDAO mongo) {
		this.mongo = mongo;
		this.strUtils = new StringUtils();
	}

	public void register(RoutingContext context) {
		final String login = context.request().getParam(LOGIN);
		final String pwd = context.request().getParam(PWD);
		mongo.newUser(login, strUtils.hash256(pwd), result -> {
			if (result.failed()) {
				context.fail(result.cause());
			} else {
				redirectTo(context, "/login.hbs");
			}
		});
	}

	public void login(RoutingContext context) {
		final String login = context.request().getParam(LOGIN);
		final String pwd = context.request().getParam(PWD);
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
			session.put(ACCESS_TOKEN, accessToken);
			session.put(LOGIN, login);
			session.put(USER_ID, user.getString("_id"));
			context.vertx().sharedData().getLocalMap("access_tokens").put(accessToken, user.getString("_id"));
			redirectTo(context, INDEX);
		});
	}

	public void logout(RoutingContext context) {
		final Session session = context.session();
		session.remove(LOGIN);
		session.remove(USER_ID);
		String accessToken = session.get(ACCESS_TOKEN);
		if (accessToken != null) {
			context.vertx().sharedData().getLocalMap("access_tokens").remove(accessToken);
		}
		session.remove(ACCESS_TOKEN);
		redirectTo(context, "/index.hbs");
	}

	private static void redirectTo(RoutingContext context, String url) {
		HttpServerResponse response = context.response();
		response.setStatusCode(303);
		response.headers().add("Location", url);
		response.end();
	}

}
