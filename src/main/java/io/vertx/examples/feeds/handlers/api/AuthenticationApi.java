package io.vertx.examples.feeds.handlers.api;

import io.vertx.core.http.HttpHeaders;
import io.vertx.examples.feeds.dao.MongoDAO;
import io.vertx.examples.feeds.utils.StringUtils;
import io.vertx.ext.web.RoutingContext;

import static io.vertx.examples.feeds.dao.MongoDAO.ID_COLUMN;
import static io.vertx.examples.feeds.utils.VertxUtils.failOr;

public class AuthenticationApi {

	private final MongoDAO mongo;
	private final StringUtils strUtils;

	private static final String LOGIN = "login";
	private static final String PWD = "password";
	private static final String ACCESS_TOKEN = "access_token";
	private static final String INDEX_PAGE = "/index.hbs";
    private static final String LOGIN_PAGE = "/login.hbs";
	private static final String USER_ID = "userId";

	public AuthenticationApi(MongoDAO mongo) {
		this.mongo = mongo;
		this.strUtils = new StringUtils();
	}

	public void register(RoutingContext rc) {
		var login = rc.request().getParam(LOGIN_PAGE);
		var pwd = rc.request().getParam(PWD);
		mongo.newUser(login, strUtils.hash256(pwd))
                .setHandler(failOr(rc, result -> redirectTo(rc, LOGIN_PAGE)));
	}

	public void login(RoutingContext context) {
		var login = context.request().getParam(LOGIN);
		var pwd = context.request().getParam(PWD);
		mongo.userByLoginAndPwd(login, strUtils.hash256(pwd))
            .setHandler(failOr(context, maybeUser -> {
                if (maybeUser.isEmpty()) {
                    redirectTo(context, LOGIN_PAGE);
                    return;
                }
                var user = maybeUser.get();
                var accessToken = strUtils.generateToken();
                var userId = user.getString(ID_COLUMN);
                context.session()
                    .put(ACCESS_TOKEN, accessToken)
                    .put(LOGIN_PAGE, login)
                    .put(USER_ID, userId);
                context.vertx().sharedData().getLocalMap("access_tokens").put(accessToken, userId);
                redirectTo(context, INDEX_PAGE);
    		}));
	}

	public void logout(RoutingContext rc) {
		var session = rc.session();
		session.remove(LOGIN_PAGE);
		session.remove(USER_ID);
		var accessToken = session.get(ACCESS_TOKEN);
		if (accessToken != null) {
			rc.vertx().sharedData().getLocalMap("access_tokens").remove(accessToken);
		}
		session.remove(ACCESS_TOKEN);
		redirectTo(rc, INDEX_PAGE);
	}

	private static void redirectTo(RoutingContext rc, String url) {
		rc.response()
		    .setStatusCode(303)
		    .putHeader(HttpHeaders.LOCATION, url)
		    .end();
	}

}
