package io.vertx.examples.feeds.verticles;

import de.flapdoodle.embed.mongo.MongodExecutable;
import de.flapdoodle.embed.mongo.MongodStarter;
import de.flapdoodle.embed.mongo.config.MongodConfigBuilder;
import de.flapdoodle.embed.mongo.config.Net;
import de.flapdoodle.embed.mongo.distribution.Version;
import de.flapdoodle.embed.process.runtime.Network;
import io.vertx.core.AbstractVerticle;
import io.vertx.core.Promise;

import java.io.IOException;

public class EmbeddedMongo extends AbstractVerticle {

	private MongodExecutable mongod;

	@Override
	public void start(Promise<Void> future) {
		MongodStarter starter = MongodStarter.getDefaultInstance();

		try {
			int port = MainVerticle.MONGO_PORT;
			MongodConfigBuilder builder = new MongodConfigBuilder();
			builder.version(Version.Main.PRODUCTION);
			builder.net(new Net(port, Network.localhostIsIPv6()));
			mongod = starter.prepare(builder.build());
			mongod.start();
		} catch (IOException ioe) {
			future.fail(ioe);
			return;
		}
		future.complete();
	}

	@Override
	public void stop(Promise<Void> future) {
		if (mongod != null) {
			mongod.stop();
			mongod = null;
		}
		future.complete();
	}

}
