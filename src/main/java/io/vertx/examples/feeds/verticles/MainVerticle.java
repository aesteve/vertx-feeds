package io.vertx.examples.feeds.verticles;

import io.vertx.core.AbstractVerticle;
import io.vertx.core.Context;
import io.vertx.core.DeploymentOptions;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.Vertx;
import io.vertx.core.json.JsonObject;
import io.vertx.examples.feeds.utils.async.MultipleFutures;

import java.util.ArrayList;
import java.util.List;

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
		deployEmbeddedDbs(future, this::deployFeedBroker);
	}

	private void deployEmbeddedDbs(Future<Void> future, Handler<Future<Void>> whatsNext) {
		MultipleFutures dbDeployments = new MultipleFutures();
		dbDeployments.add(this::deployEmbeddedRedis);
		dbDeployments.add(this::deployEmbeddedMongo);
		dbDeployments.setHandler(result -> {
			if (result.failed()) {
				future.fail(result.cause());
			} else {
				whatsNext.handle(future);
			}
		});
		dbDeployments.start();
	}

	private void deployEmbeddedRedis(Future<Void> future) {
		DeploymentOptions options = new DeploymentOptions();
		options.setWorker(true);
		vertx.deployVerticle(EmbeddedRedis.class.getName(), options, result -> {
			if (result.failed()) {
				future.fail(result.cause());
			} else {
				deploymentIds.add(result.result());
				future.complete();
			}
		});
	}

	private void deployEmbeddedMongo(Future<Void> future) {
		DeploymentOptions options = new DeploymentOptions();
		options.setWorker(true);
		vertx.deployVerticle(EmbeddedMongo.class.getName(), options, result -> {
			if (result.failed()) {
				future.fail(result.cause());
			} else {
				deploymentIds.add(result.result());
				future.complete();
			}
		});
	}

	private void deployFeedBroker(Future<Void> future) {
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
						future.complete();
					}
				});
			}
		});
	}

	@Override
	public void stop(Future<Void> future) {
		MultipleFutures futures = new MultipleFutures(future);
		deploymentIds.forEach(deploymentId -> futures.add(fut -> undeploy(deploymentId, fut)));
		futures.start();
	}

	private void undeploy(String deploymentId, Future<Void> future) {
		vertx.undeploy(deploymentId, res -> {
			if (res.succeeded()) {
				future.complete();
			} else {
				future.fail(res.cause());
			}
		});
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
