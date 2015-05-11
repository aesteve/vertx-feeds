package io.vertx.feeds.verticles;

import io.vertx.core.AbstractVerticle;
import io.vertx.core.Context;
import io.vertx.core.DeploymentOptions;
import io.vertx.core.Future;
import io.vertx.core.Vertx;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.mongo.MongoClient;

import java.util.ArrayList;
import java.util.List;

/**
 * Main verticle, orchestrate the instanciation of other verticles
 */
public class MainVerticle extends AbstractVerticle {

    private FeedBroker broker;
    private WebServer server;
    private List<String> deploymentIds;
    private MongoClient mongo;

    @Override
    public void init(Vertx vertx, Context context) {
        super.init(vertx, context);
        deploymentIds = new ArrayList<String>(2);
    }

    /**
     * First, we need to create the Mongo service and start it.
     * Then : we want to start the broker, and once it's started, we're starting the web/api server
     */
    @Override
    public void start(Future<Void> future) {
        JsonObject mongoConfig = mongoConfig();
        mongo = MongoClient.createShared(vertx, mongoConfig);
        broker = new FeedBroker(mongoConfig);
        server = new WebServer(mongoConfig);
        DeploymentOptions brokerOptions = new DeploymentOptions();
        // brokerOptions.setWorker(true); // not a worker for now, since it's just doing async stuff
        vertx.deployVerticle(broker, brokerOptions, brokerResult -> {
            if (brokerResult.failed()) {
                future.fail(brokerResult.cause());
            } else {
                deploymentIds.add(brokerResult.result());
                vertx.deployVerticle(server, serverResult -> {
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

    /**
     * Stop Mongo, then undeploy every verticle.
     */
    @Override
    public void stop(Future<Void> future) {
        mongo.close();
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
}
