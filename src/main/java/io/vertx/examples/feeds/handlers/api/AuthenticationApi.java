package io.vertx.examples.feeds.handlers.api;

import io.vertx.core.http.HttpHeaders;
import io.vertx.examples.feeds.dao.MongoDAO;
import io.vertx.examples.feeds.utils.StringUtils;
import io.vertx.ext.web.RoutingContext;

import static io.vertx.examples.feeds.dao.MongoDAO.ID_COLUMN;

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
		var login = context.request().getParam(LOGIN);
		var pwd = context.request().getParam(PWD);
		mongo.newUser(login, strUtils.hash256(pwd))
                .setHandler(result -> {
                    if (result.failed()) {
                        context.fail(result.cause());
                    } else {
                        redirectTo(context, "/login.hbs");
                    }
                });
	}

	public void login(RoutingContext context) {
		var login = context.request().getParam(LOGIN);
		var pwd = context.request().getParam(PWD);
		mongo.userByLoginAndPwd(login, strUtils.hash256(pwd))
            .setHandler(res -> {
			if (res.failed()) {
				context.fail(res.cause());
				return;
			}
			var maybeUser = res.result();
			if (maybeUser.isEmpty()) {
				redirectTo(context, "/login.hbs");
				return;
			}
			var user = maybeUser.get();
			var accessToken = strUtils.generateToken();
			var userId = user.getString(ID_COLUMN);
            context.session()
                .put(ACCESS_TOKEN, accessToken)
			    .put(LOGIN, login)
                .put(USER_ID, userId);
			context.vertx().sharedData().getLocalMap("access_tokens").put(accessToken, userId);
			redirectTo(context, INDEX);
		});
	}

	public void logout(RoutingContext rc) {
		var session = rc.session();
		session.remove(LOGIN);
		session.remove(USER_ID);
		var accessToken = session.get(ACCESS_TOKEN);
		if (accessToken != null) {
			rc.vertx().sharedData().getLocalMap("access_tokens").remove(accessToken);
		}
		session.remove(ACCESS_TOKEN);
		redirectTo(rc, "/index.hbs");
	}

	private static void redirectTo(RoutingContext rc, String url) {
		rc.response()
		    .setStatusCode(303)
		    .putHeader(HttpHeaders.LOCATION, url)
		    .end();
	}

}
