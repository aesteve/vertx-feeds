package io.vertx.examples.feeds.verticles;

import io.vertx.core.AbstractVerticle;
import io.vertx.core.Future;
import io.vertx.core.Promise;
import redis.embedded.RedisServer;


public class EmbeddedRedis extends AbstractVerticle {

	private RedisServer server;

	@Override
	public void start(Promise<Void> future) {
		try {
			server = new RedisServer(MainVerticle.REDIS_PORT);
			server.start(); // seems to be blocking
			future.complete();
		} catch (Exception e) {
			future.fail(e);
		}
	}

	@Override
	public void stop(Promise<Void> future) {
		if (server != null) {
			server.stop();
			server = null;
		}
		future.complete();
	}

}
