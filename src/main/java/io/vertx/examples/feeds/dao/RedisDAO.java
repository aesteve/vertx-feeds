package io.vertx.examples.feeds.dao;

import io.vertx.core.AsyncResult;
import io.vertx.core.Handler;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;
import io.vertx.examples.feeds.utils.rss.FeedUtils;
import io.vertx.redis.RedisClient;
import io.vertx.redis.op.RangeLimitOptions;

import java.text.ParseException;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class RedisDAO {

	private static final Logger LOG = LoggerFactory.getLogger(RedisDAO.class);

	private final RedisClient redis;

	public RedisDAO(RedisClient redis) {
		this.redis = redis;
	}

	public void getEntries(String feedHash, Date from, Date to, Handler<AsyncResult<JsonArray>> handler) {
		String fromStr;
		String toStr;
		if (from != null) {
			fromStr = Double.toString((double)from.getTime());
		} else {
			fromStr = "-inf";
		}
		if (to != null) {
			toStr = Double.toString((double)to.getTime());
		} else {
			toStr = "+inf";
		}
		redis.zrevrangebyscore(feedHash, toStr, fromStr, RangeLimitOptions.NONE, handler);
	}

	public void getMaxDate(String feedHash, Handler<Date> handler) {
		/*
		 * FIXME : this fails with a ClassCastException use it as soon as RedisClient is fixed
		 * RangeLimitOptions options = new RangeLimitOptions();
		 * options.setLimit(0, 1);
		 */
		redis.zrevrangebyscore(feedHash, "+inf", "-inf", RangeLimitOptions.NONE, result -> {
			if (result.failed()) {
				LOG.error("Fetch max date failed : ", result.cause());
				handler.handle(null);
			} else {
				JsonArray array = result.result();
				if (array.isEmpty()) {
					LOG.info("Fetch max date is null, array is empty for feedHash : " + feedHash);
					handler.handle(null);
					return;
				}
				JsonObject max = new JsonObject(array.getString(0));
				String published = max.getString("published");
				try {
					handler.handle(FeedUtils.getDate(published));
				} catch (ParseException pe) {
					LOG.error("Could not fetch max date : ", pe);
					handler.handle(null);
				}
			}
		});
	}

	public void insertEntries(String feedHash, List<JsonObject> entries, Handler<AsyncResult<Long>> handler) {
		Map<String, Double> members = new HashMap<>(entries.size());
		entries.forEach(entry -> members.put(entry.toString(), entry.getDouble("score")));
		redis.zaddMany(feedHash, members, handler);
	}
}
