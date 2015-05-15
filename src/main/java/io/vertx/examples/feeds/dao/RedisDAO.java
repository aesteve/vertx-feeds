package io.vertx.examples.feeds.dao;

import io.vertx.core.AsyncResult;
import io.vertx.core.Handler;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.redis.RedisClient;

import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class RedisDAO {

    private RedisClient redis;

    public RedisDAO(RedisClient redis) {
        this.redis = redis;
    }

    public void start(Handler<AsyncResult<Void>> handler) {
        redis.start(handler);
    }

    public void stop(Handler<AsyncResult<Void>> handler) {
        redis.stop(handler);
    }

    public void getEntries(String feedHash, Date from, Date to, Handler<AsyncResult<JsonArray>> handler) {
        String fromStr;
        String toStr;
        if (from != null) {
            fromStr = Double.toString(Long.valueOf(from.getTime()).doubleValue());
        } else {
            fromStr = "-inf";
        }
        if (to != null) {
            toStr = Double.toString(Long.valueOf(from.getTime()).doubleValue());
        } else {
            toStr = "+inf";
        }
        redis.zrevrangebyscore(feedHash, toStr, fromStr, null, handler);
    }

    public void insertEntries(String feedHash, List<JsonObject> entries, Handler<AsyncResult<Long>> handler) {
        Map<String, Double> members = new HashMap<String, Double>(entries.size());
        entries.forEach(entry -> {
            members.put(entry.toString(), entry.getDouble("score"));
        });
        redis.zaddMany(feedHash, members, handler);
    }
}
