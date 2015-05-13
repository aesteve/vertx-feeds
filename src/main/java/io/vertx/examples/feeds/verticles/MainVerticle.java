package io.vertx.examples.feeds.verticles;

import io.vertx.core.AbstractVerticle;
import io.vertx.core.Context;
import io.vertx.core.DeploymentOptions;
import io.vertx.core.Future;
import io.vertx.core.Vertx;
import io.vertx.core.json.JsonObject;

import java.util.ArrayList;
import java.util.List;

/**
 * Main verticle, orchestrate the instanciation of other verticles
 */
public class MainVerticle extends AbstractVerticle {

    private List<String> deploymentIds;

    @Override
    public void init(Vertx vertx, Context context) {
        super.init(vertx, context);
        deploymentIds = new ArrayList<String>(2);
    }

    @Override
    public void start(Future<Void> future) {
        JsonObject dbConfig = new JsonObject();
        dbConfig.put("redis", redisConfig());
        dbConfig.put("mongo", mongoConfig());
        DeploymentOptions brokerOptions = new DeploymentOptions();
        brokerOptions.setConfig(dbConfig);
        // brokerOptions.setWorker(true); // not a worker for now, since it's just doing async stuff
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
        deploymentIds.forEach(deploymentId -> {
            vertx.undeploy(deploymentId);
        });
    }

    private JsonObject mongoConfig() {
        JsonObject config = new JsonObject();
        config.put("host", "localhost");
        config.put("port", 27017);
        config.put("db_name", "vertx-feeds");
        return config;
    }

    private JsonObject redisConfig() {
        JsonObject config = new JsonObject();
        config.put("host", "localhost");
        config.put("port", 6379);
        return config;
    }
}
