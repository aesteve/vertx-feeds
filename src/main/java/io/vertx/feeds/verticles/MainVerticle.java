package io.vertx.feeds.verticles;

import java.util.ArrayList;
import java.util.List;

import io.vertx.core.AbstractVerticle;
import io.vertx.core.Context;
import io.vertx.core.Future;
import io.vertx.core.Vertx;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.mongo.MongoService;

/**
 * Main verticle, orchestrate the instanciation of other verticles
 */
public class MainVerticle extends AbstractVerticle {

    private MongoService mongo;
    private FeedBroker broker;
    private WebServer server;
    private List<String> deploymentIds;

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
        mongo = MongoService.create(vertx, mongoConfig());
        mongo.start();
        broker = new FeedBroker(mongo);
        server = new WebServer(mongo);
        vertx.deployVerticle(broker, brokerResult -> {
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
        mongo.stop();
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
