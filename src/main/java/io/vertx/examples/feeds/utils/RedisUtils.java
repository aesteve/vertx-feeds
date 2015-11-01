package io.vertx.examples.feeds.utils;

import io.vertx.core.json.JsonObject;
import io.vertx.redis.RedisOptions;

public class RedisUtils {
	
	public static RedisOptions createRedisOptions(JsonObject conf) {
		RedisOptions options = new RedisOptions();
		options.setHost(conf.getString("host"));
		options.setPort(conf.getInteger("port"));
		return options;
	}
	
}
