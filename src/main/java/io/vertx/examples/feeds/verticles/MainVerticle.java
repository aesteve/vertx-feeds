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

	public static final int REDIS_PORT = 7474;
	public static final int MONGO_PORT = 8889;

	private List<String> deploymentIds;
	private DeploymentOptions workerOptions = new DeploymentOptions().setWorker(true);

	@Override
	public void init(Vertx vertx, Context context) {
		super.init(vertx, context);
		deploymentIds = new ArrayList<>(3);
	}

	@Override
	public void start(Promise<Void> future) {
		CompositeFuture
				.all(deployEmbeddedMongo(), deployEmbeddedRedis())
                .flatMap(res -> deployFeedBroker())
                .setHandler(future);
	}

	private Future<String> deployEmbeddedRedis() {
		return vertx.deployVerticle(EmbeddedRedis.class.getName(), workerOptions);
	}

	private Future<String> deployEmbeddedMongo() {
		return vertx.deployVerticle(EmbeddedMongo.class.getName(), workerOptions);
	}

	private Future<Void> deployFeedBroker() {
		var dbConfig = new JsonObject()
                .put("redis", REDIS_CONF)
		        .put("mongo", MONGO_CONF);
		var options = new DeploymentOptions().setConfig(dbConfig);
		return vertx
                .deployVerticle(FeedBroker.class.getName(), options)
                .flatMap(deploymentId -> {
                    deploymentIds.add(deploymentId);
                    return vertx
                            .deployVerticle(WebServer.class.getName(), options)
                            .map(serverDeploymentId -> {
                                deploymentIds.add(serverDeploymentId);
                                return serverDeploymentId;
                            })
                            .mapEmpty();
                });
	}

	@Override
	public void stop(Promise<Void> future) {
		CompositeFuture
                .all(
                    deploymentIds
                            .stream()
                            .map(vertx::undeploy)
                            .collect(Collectors.toList())
                )
                .<Void>mapEmpty()
                .setHandler(future);
	}

	private static final JsonObject MONGO_CONF =
		new JsonObject()
		    .put("host", "localhost")
		    .put("port", MONGO_PORT)
		    .put("db_name", "vertx-feeds");

	private static final JsonObject REDIS_CONF =
		new JsonObject()
		    .put("host", "localhost")
		    .put("port", REDIS_PORT);

	public static void main(String... args) {
		Vertx.vertx()
		    .deployVerticle(MainVerticle.class.getName());
	}

}
