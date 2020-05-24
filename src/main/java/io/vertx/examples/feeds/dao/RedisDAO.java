package io.vertx.examples.feeds.dao;

import io.vertx.core.Future;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.examples.feeds.utils.rss.FeedConverters;
import io.vertx.redis.client.RedisAPI;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.text.ParseException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.List;
import java.util.stream.Collector;

public class RedisDAO {

	private static final Logger LOG = LoggerFactory.getLogger(RedisDAO.class);

	private final RedisAPI redis;

	public RedisDAO(RedisAPI redis) {
		this.redis = redis;
	}

	public Future<JsonArray> allEntriesForFeed(String feedHash, Date from, Date to) {
	    List<String> args = new ArrayList<>();
	    args.add(feedHash);
		if (from != null) {
			args.add(Double.toString((double)from.getTime()));
		} else {
			args.add("-inf");
		}
		if (to != null) {
			args.add(Double.toString((double)to.getTime()));
		} else {
			args.add("+inf");
		}
		return redis
                .zrevrangebyscore(args)
                .map(res -> res.stream()
                                .map(entry -> new JsonObject(entry.toString()))
                                .collect(Collector.of(JsonArray::new, JsonArray::add, JsonArray::addAll))
                );
	}

	public Future<Date> getMaxDate(String feedHash) {
		return redis.zrevrangebyscore(Arrays.asList(feedHash, "+inf", "-inf"))
                .map(resp -> {
                    if (resp.size() == 0) {
                        LOG.info("Fetch max date is null, array is empty for feedHash : " + feedHash);
                        return null;
                    }
                    var max = new JsonObject(resp.get(0).toString());
                    var published = max.getString("published");
                    try {
                        return FeedConverters.getDate(published);
                    } catch (ParseException pe) {
                        LOG.error("Could not fetch max date : ", pe);
                        return null;
                    }
    			});
	}

	public Future<Void> insertEntries(String feedHash, List<JsonObject> entries) {
	    var args = new ArrayList<String>();
		args.add(feedHash);
		entries.forEach(entry -> {
            args.add(Double.toString(entry.getDouble("score")));
		    args.add(entry.toString());
        });
		return redis
                .zadd(args)
                .onFailure(f ->
                        LOG.error("Could not insert entries into redis", f)
                )
                .mapEmpty();
	}

}
