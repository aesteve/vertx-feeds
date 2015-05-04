package io.vertx.feeds.verticles;

import io.vertx.core.AbstractVerticle;
import io.vertx.core.Future;
import io.vertx.ext.mongo.MongoService;

public class FeedBroker extends AbstractVerticle {

    private MongoService mongo;

    public FeedBroker(MongoService mongo) {
        this.mongo = mongo;
    }

    @Override
    public void start(Future<Void> future) {
        readFeedsPeriodically();
        future.complete();
    }

    @Override
    public void stop(Future<Void> future) {
        future.complete();
    }

    private void readFeedsPeriodically() {
        // TODO
    }
}
