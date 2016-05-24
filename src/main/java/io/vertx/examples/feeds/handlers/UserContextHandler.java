package io.vertx.examples.feeds.handlers;

import io.vertx.core.json.JsonObject;
import io.vertx.examples.feeds.dao.MongoDAO;
import io.vertx.ext.web.RoutingContext;
import io.vertx.ext.web.Session;

public class UserContextHandler {

	private final MongoDAO mongo;

	public UserContextHandler(MongoDAO mongo) {
		this.mongo = mongo;
	}

	public void fromApiToken(RoutingContext context) {
		String userId;
		String token = context.request().params().get("accessToken");
		if (token == null) {
			context.fail(401);
			return;
		}
		userId = (String) context.vertx().sharedData().getLocalMap("access_tokens").get(token);
		if (userId == null) {
			context.fail(401);
		} else {
			findAndInjectUser(context, userId);
		}
	}

	public void fromSession(RoutingContext context) {
		Session session = context.session();
		String userId = session.get("userId");
		if (userId == null) {
			context.fail(401);
		} else {
			findAndInjectUser(context, userId);
		}
	}

	private void findAndInjectUser(RoutingContext context, String userId) {
		mongo.userById(userId, mongoHandler -> {
			if (mongoHandler.failed()) {
				context.fail(mongoHandler.cause());
			} else {
				JsonObject user = mongoHandler.result();
				if (user == null) {
					context.fail(403);
				} else {
					context.put("user", user);
					context.next();
				}
			}
		});
	}
}
