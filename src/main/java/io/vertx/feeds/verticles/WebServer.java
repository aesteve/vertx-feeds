package io.vertx.feeds.verticles;

import io.vertx.core.AbstractVerticle;
import io.vertx.core.Future;
import io.vertx.core.http.HttpServer;
import io.vertx.core.http.HttpServerOptions;
import io.vertx.ext.apex.Router;

public class WebServer extends AbstractVerticle {

	@Override
	public void start(Future<Void> future) {
		HttpServerOptions options = new HttpServerOptions();
		options.setHost("localhost");
		options.setPort(9000);
		HttpServer server = vertx.createHttpServer(options);
		server.requestHandler(createRouter()::accept);
		server.listen(result -> {
			if (result.succeeded()) {
				future.complete();
			} else {
				future.fail(result.cause());
			}
		});
	}
	
	@Override 
	public void stop(Future<Void> future) {
		future.complete();
	}
	
	private Router createRouter() {
		Router router = Router.router(vertx);
		router.get("/api/login").handler(routingContext -> {
			
		});
		return router;
	}
	
}
