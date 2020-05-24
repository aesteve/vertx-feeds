package io.vertx.examples.feeds.utils;

import io.vertx.core.AsyncResult;
import io.vertx.core.Handler;
import io.vertx.core.json.JsonArray;
import io.vertx.ext.web.RoutingContext;

import java.util.stream.Collector;

public class VertxUtils {

    private VertxUtils() {}

    public static Collector<Object, JsonArray, JsonArray> JSON_ARRAY_COLLECTOR =
            Collector.of(JsonArray::new, JsonArray::add, JsonArray::addAll);

    public static <T> Handler<AsyncResult<T>> failOr(RoutingContext rc, Handler<T> handler) {
        return res -> {
            if (res.failed()) {
                rc.fail(res.cause());
                return;
            }
            handler.handle(res.result());
        };
    }

}
