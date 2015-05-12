package io.vertx.feeds.api;

import io.vertx.core.http.HttpServerRequest;
import io.vertx.core.http.HttpServerResponse;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.apex.RoutingContext;
import io.vertx.ext.mongo.MongoClient;
import io.vertx.feeds.utils.StringUtils;
import io.vertx.feeds.utils.UserUtils;
import io.vertx.redis.RedisClient;

import java.util.List;
import java.util.Optional;

/**
 * Simple CRUD for handling feeds
 */
public class FeedsApi {

    private MongoClient mongo;
    private RedisClient redis;
    private StringUtils strUtils;

    public FeedsApi(MongoClient mongo, RedisClient redis) {
        this.mongo = mongo;
        this.redis = redis;
        this.strUtils = new StringUtils();
    }

    public void create(RoutingContext context) {
        JsonObject body = context.getBodyAsJson();
        UserUtils.getUserFromContext(context, mongo, user -> {
            JsonArray subscriptions = user.getJsonArray("subscriptions");
            final String urlHash = strUtils.hash256(body.getString("url"));
            if (subscriptions == null) {
                subscriptions = new JsonArray();
            } else {
            	boolean alreadySubscribed = subscriptions.stream().anyMatch(subscription -> {
            		return ((JsonObject)subscription).getString("hash").equals(urlHash);
            	});
            	if (alreadySubscribed) {
            		context.fail(400);
            		return;
            	}
            }
            body.put("hash", urlHash);
            subscriptions.add(body);
            JsonObject query = new JsonObject();
            query.put("_id", user.getString("_id"));
            JsonObject newSubscriptions = new JsonObject();
            newSubscriptions.put("$set", new JsonObject().put("subscriptions", subscriptions));
            mongo.update("users", query, newSubscriptions, result -> {
                if (result.failed()) {
                    context.fail(result.cause());
                } else {
                	JsonObject duplicateQuery = new JsonObject();
                	duplicateQuery.put("hash", urlHash);
                	mongo.find("feeds", duplicateQuery, duplicateHandler -> {
                		if (duplicateHandler.succeeded()) {
                			List<JsonObject> duplicates = duplicateHandler.result();
                			if (duplicates.isEmpty()) {
                				body.put("subscriber_count", 1);
                                mongo.insert("feeds", body, insertResult -> {
                                    HttpServerResponse response = context.response();
                                    response.end(body.toString());
                                });
                			} else {
                				JsonObject duplicate = duplicates.get(0);
                				JsonObject updateQuery = new JsonObject();
                				Integer oldCount = duplicate.getInteger("subscriber_count");
                				if (oldCount == null) {
                					oldCount = 0;
                				}
                				body.put("subscriber_count", oldCount+1);
                				updateQuery.put("_id", duplicate.getString("_id"));
                				mongo.update("feeds", updateQuery, body, updateHandler -> {
                                    HttpServerResponse response = context.response();
                                    response.end(body.toString());
                				});
                			}
                		}
                	});
                }
            });
        });
    }

    public void retrieve(RoutingContext context) {
    	HttpServerRequest request = context.request();
    	String feedId = request.getParam("feedId");
    	if (feedId == null) {
    		context.fail(400);
    		return;
    	}
        UserUtils.getUserFromContext(context, mongo, user -> {
        	JsonObject subscription = getSubscription(user, context);
        	if (subscription != null) {
                context.response().end(subscription.toString());
        	}
        });
    }

    public void update(RoutingContext context) {
        context.response().end("TODO");
    }

    public void delete(RoutingContext context) {
    	UserUtils.getUserFromContext(context, mongo, user -> {
    		JsonObject subscription = getSubscription(user, context);
    		if (subscription != null) {
    			JsonArray subscriptions = user.getJsonArray("subscriptions");
    			subscriptions.remove(subscription);
    			JsonObject newSubscriptions = new JsonObject();
                newSubscriptions.put("$set", new JsonObject().put("subscriptions", subscriptions));
    			JsonObject userQuery = new JsonObject();
    			userQuery.put("_id", user.getString("_id"));
    			mongo.update("users", userQuery, newSubscriptions, updateHandler -> {
    				if (updateHandler.failed()) {
    					context.fail(updateHandler.cause());
    					return;
    				}
    				String feedId = context.request().getParam("feedId");
                	JsonObject duplicateQuery = new JsonObject();
                	duplicateQuery.put("hash", feedId);
                	mongo.find("feeds", duplicateQuery, duplicateHandler -> {
                		if (duplicateHandler.succeeded()) {
                			List<JsonObject> duplicates = duplicateHandler.result();
                			if (duplicates.isEmpty()) {
                				// should never happen
                				context.response().end(subscription.toString());
                			} else {
                				JsonObject duplicate = duplicates.get(0);
                				JsonObject updateQuery = new JsonObject();
                				Integer oldCount = duplicate.getInteger("subscriber_count");
                				if (oldCount == null) {
                					oldCount = 1;
                				}
                				subscription.put("subscriber_count", oldCount-1);
                				updateQuery.put("_id", duplicate.getString("_id"));
                				mongo.update("feeds", updateQuery, subscription, feedUpdateHandler -> {
                                    HttpServerResponse response = context.response();
                                    subscription.remove("subscriber_count"); // do not expose this
                                    response.end(subscription.toString());
                				});
                			}
                		}
                	});
    			});
    		}
    	});
    }

    public void list(RoutingContext context) {
        UserUtils.getUserFromContext(context, mongo, user -> {
            JsonArray subscriptions = user.getJsonArray("subscriptions");
            if (subscriptions == null) {
                subscriptions = new JsonArray();
            }
            HttpServerResponse response = context.response();
            response.headers().add("Content-Type", "application/json");
            response.end(subscriptions.toString());
        });
    }
    
    public void entries(RoutingContext context) {
        UserUtils.getUserFromContext(context, mongo, user -> {
        	JsonObject feed = getSubscription(user, context);
        	if (feed != null) {
        		String feedId = context.request().getParam("feedId");
            	redis.zrangebyscore(feedId, "-inf", "+inf", null, handler -> {
            		if (handler.failed()) {
            			context.fail(handler.cause());
            		} else {
            			context.response().end(handler.result().toString());
            		}
            	});
        	}
        });
    }

    private JsonObject getSubscription(JsonObject user, RoutingContext context) {
    	String feedId = context.request().getParam("feedId");
    	if (feedId == null) {
    		context.fail(400);
    		return null;
    	}
        JsonArray subscriptions = user.getJsonArray("subscriptions");
        if (subscriptions == null) {
            context.fail(404);
            return null;
        }
        Optional<Object> optional = subscriptions.stream().filter(sub -> {
        	JsonObject subscription = (JsonObject)sub;
        	return subscription.getString("hash").equals(feedId);
        }).findFirst();
        if (optional.isPresent()) {
        	// OK, it's one of user's subscription
        	return (JsonObject)optional.get();
        } else { // either the feed doesn't exist, or the user tries to access someone else's feed -> no distinction
        	context.fail(403);
        	return null;
        }
    }
}
