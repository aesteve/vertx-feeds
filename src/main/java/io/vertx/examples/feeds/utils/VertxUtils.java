package io.vertx.examples.feeds.utils;

import io.vertx.core.json.JsonArray;

import java.util.stream.Collector;

public class VertxUtils {

    private VertxUtils() {}

    public static Collector<Object, JsonArray, JsonArray> JSON_ARRAY_COLLECTOR =
            Collector.of(JsonArray::new, JsonArray::add, JsonArray::addAll);

}
