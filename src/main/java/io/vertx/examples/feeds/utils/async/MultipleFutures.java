package io.vertx.examples.feeds.utils.async;

import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.impl.LoggerFactory;

import java.util.HashMap;
import java.util.Map;

public class MultipleFutures extends SimpleFuture<Void> {

    private final Map<Handler<Future<Void>>, Future<Void>> consumers;
    private static final Logger log = LoggerFactory.getLogger(MultipleFutures.class);

    public MultipleFutures() {
        consumers = new HashMap<Handler<Future<Void>>, Future<Void>>();
    }

    public void add(Handler<Future<Void>> handler) {
        Future<Void> future = Future.future();
        future.setHandler(futureHandler -> {
            checkCallHandler();
        });
        consumers.put(handler, future);
    }

    public void start() {
        consumers.forEach((consumer, future) -> {
            consumer.handle(future);
        });
    }

    @Override
    public Void result() {
        return null;
    }

    @Override
    public Throwable cause() {
        Exception e = new Exception("At least one future failed");
        consumers.forEach((consumer, future) -> {
            if (future.cause() != null) {
                log.error(future.cause());
                if (e.getCause() == null) {
                    e.initCause(future.cause());
                } else {
                    e.addSuppressed(future.cause());
                }
            }
        });
        return e;
    }

    @Override
    public boolean succeeded() {
        return consumers.values().stream().allMatch(future -> {
            return future.succeeded();
        });
    }

    @Override
    public boolean failed() {
        return consumers.values().stream().anyMatch(future -> {
            return future.failed();
        });
    }

    @Override
    public boolean isComplete() {
        return consumers.values().stream().allMatch(future -> {
            return future.isComplete();
        });
    }
}