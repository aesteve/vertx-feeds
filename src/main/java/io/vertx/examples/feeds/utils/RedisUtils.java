package io.vertx.examples.feeds.utils;

import io.vertx.core.json.JsonObject;
import io.vertx.redis.client.RedisOptions;

import java.util.Collections;

public class RedisUtils {
	
	public static RedisOptions createRedisOptions(JsonObject conf) {
		var endpoints = Collections.singletonList("redis://" + conf.getString("host") + ":" + conf.getInteger("port"));
		return new RedisOptions().setEndpoints(endpoints);
	}
	
}
