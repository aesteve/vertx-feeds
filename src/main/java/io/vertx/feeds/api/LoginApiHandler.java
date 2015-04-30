package io.vertx.feeds.api;

import io.vertx.core.Handler;
import io.vertx.ext.apex.RoutingContext;

public class LoginApiHandler implements Handler<RoutingContext> {

	@Override
	public void handle(RoutingContext context) {
		context.response().end("TODO");
	}
	
}
