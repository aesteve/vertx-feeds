package io.vertx.examples.feeds;

import io.vertx.examples.feeds.verticles.MainVerticle;
import redis.embedded.RedisServer;

public class Test {

    public static void main(String... args) throws Exception {
        RedisServer server = new RedisServer(MainVerticle.REDIS_PORT);
        server.start();
        System.out.println("Started");
    }
}
