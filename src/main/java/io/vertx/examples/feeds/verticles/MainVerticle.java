package io.vertx.examples.feeds.verticles;

import io.vertx.core.*;
import io.vertx.core.json.JsonObject;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Main verticle, orchestrate the instanciation of other verticles
 */
public class MainVerticle extends AbstractVerticle {

	public static final int REDIS_PORT = 8888;
	public static final int MONGO_PORT = 8889;

	private List<String> deploymentIds;

	@Override
	public void init(Vertx vertx, Context context) {
		super.init(vertx, context);
		deploymentIds = new ArrayList<>(3);
	}

	@Override
	public void start(Future<Void> future) {
		CompositeFuture
				.all(deployEmbeddedMongo(), deployEmbeddedRedis(), deployFeedBroker())
				.setHandler(future.<CompositeFuture>map(res -> null).completer());
	}

	private Future<String> deployEmbeddedRedis() {
		Future<String> f = Future.future();
		DeploymentOptions options = new DeploymentOptions();
		options.setWorker(true);
		vertx.deployVerticle(EmbeddedRedis.class.getName(), options, f);
		return f;
	}

	private Future<String> deployEmbeddedMongo() {
		Future<String> f = Future.future();
		DeploymentOptions options = new DeploymentOptions();
		options.setWorker(true);
		vertx.deployVerticle(EmbeddedMongo.class.getName(), options, f);
		return f;
	}

	private Future<String> deployFeedBroker() {
		Future<String> future = Future.future();
		JsonObject dbConfig = new JsonObject();
		dbConfig.put("redis", redisConfig());
		dbConfig.put("mongo", mongoConfig());
		DeploymentOptions brokerOptions = new DeploymentOptions();
		brokerOptions.setConfig(dbConfig);
		vertx.deployVerticle(FeedBroker.class.getName(), brokerOptions, brokerResult -> {
			if (brokerResult.failed()) {
				future.fail(brokerResult.cause());
			} else {
				deploymentIds.add(brokerResult.result());
				DeploymentOptions webserverOptions = new DeploymentOptions();
				webserverOptions.setConfig(dbConfig);
				vertx.deployVerticle(WebServer.class.getName(), webserverOptions, serverResult -> {
					if (serverResult.failed()) {
						future.fail(serverResult.cause());
					} else {
						deploymentIds.add(serverResult.result());
						future.complete(serverResult.result());
					}
				});
			}
		});
		return future;
	}

	@Override
	public void stop(Future<Void> future) {
		CompositeFuture.all(
			deploymentIds
					.stream()
					.map(this::undeploy)
					.collect(Collectors.toList())
		).setHandler(future.<CompositeFuture>map(c -> null).completer());

	}

	private Future<Void> undeploy(String deploymentId) {
		Future<Void> future = Future.future();
		vertx.undeploy(deploymentId, res -> {
			if (res.succeeded()) {
				future.complete();
			} else {
				future.fail(res.cause());
			}
		});
		return future;
	}

	private static JsonObject mongoConfig() {
		JsonObject config = new JsonObject();
		config.put("host", "localhost");
		config.put("port", MONGO_PORT);
		config.put("db_name", "vertx-feeds");
		return config;
	}

	private static JsonObject redisConfig() {
		JsonObject config = new JsonObject();
		config.put("host", "localhost");
		config.put("port", REDIS_PORT);
		return config;
	}
}
