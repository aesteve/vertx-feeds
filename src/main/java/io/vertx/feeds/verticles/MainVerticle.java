package io.vertx.feeds.verticles;

import io.vertx.core.AbstractVerticle;
import io.vertx.core.Future;

/** 
 * Main verticle, orchestrate the instanciation of other verticles
 */
public class MainVerticle extends AbstractVerticle {
	@Override
	public void start(Future<Void> future) {
		future.complete();
	}
	
	@Override
	public void stop(Future<Void> future) {
		future.complete();
	}
}
