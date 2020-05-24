package io.vertx.examples.feeds.handlers;

import io.vertx.examples.feeds.dao.MongoDAO;
import io.vertx.ext.web.RoutingContext;

import static io.vertx.examples.feeds.utils.VertxUtils.failOr;

public class UserContextHandler {

	private final MongoDAO mongo;

	public UserContextHandler(MongoDAO mongo) {
		this.mongo = mongo;
	}

	public void fromApiToken(RoutingContext rc) {
		var token = rc.request().params().get("accessToken");
		if (token == null) {
			rc.fail(401);
			return;
		}
		var userId = rc.vertx().sharedData().<String, String>getLocalMap("access_tokens").get(token);
		if (userId == null) {
			rc.fail(401);
		} else {
			findAndInjectUser(rc, userId);
		}
	}

	public void fromSession(RoutingContext rc) {
		var userId = rc.session().<String>get("userId");
		if (userId == null) {
			rc.fail(401);
		} else {
			findAndInjectUser(rc, userId);
		}
	}

	private void findAndInjectUser(RoutingContext rc, String userId) {
		mongo.userById(userId)
            .setHandler(failOr(rc, maybeUser -> {
                if (maybeUser.isEmpty()) {
                    rc.fail(403);
                    return;
                }
                rc.put("user", maybeUser.get());
                rc.next();
            }));
	}
}
