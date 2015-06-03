package io.vertx.examples.feeds.verticles;

import java.io.IOException;

import redis.embedded.RedisServer;
import io.vertx.core.AbstractVerticle;
import io.vertx.core.Future;

public class EmbeddedRedis extends AbstractVerticle {
    private RedisServer server;

    @Override
    public void start(Future<Void> future) {
        try {
            server = new RedisServer(MainVerticle.REDIS_PORT);
            server.start(); // seems to be blocking
            future.complete();
        } catch (IOException ioe) {
            future.fail(ioe);
        }
    }

    @Override
    public void stop(Future<Void> future) {
        if (server != null) {
            server.stop();
            server = null;
        }
        future.complete();
    }

}
