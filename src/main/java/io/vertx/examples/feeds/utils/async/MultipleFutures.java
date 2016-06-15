package io.vertx.examples.feeds.utils.async;

import io.vertx.core.AsyncResult;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;

import java.util.HashMap;
import java.util.Map;

public class MultipleFutures extends SimpleFuture<Void> {

	private final Map<Handler<Future<Void>>, Future<Void>> consumers;
	private static final Logger LOG = LoggerFactory.getLogger(MultipleFutures.class);

	public MultipleFutures() {
		consumers = new HashMap<>();
	}

	public MultipleFutures(Future<Void> after) {
		this();
		join(after);
	}

	public MultipleFutures(Handler<AsyncResult<Void>> after) {
		this();
		join(after);
	}

	public void add(Handler<Future<Void>> handler) {
		Future<Void> future = Future.future();
		future.setHandler(futureHandler -> checkCallHandler());
		consumers.put(handler, future);
	}

	public void start() {
		if (consumers.isEmpty()) {
			complete();
			return;
		}
		consumers.forEach(Handler::handle);
	}

	@Override
	public Void result() {
		return null;
	}

	@Override
	public Throwable cause() {
		Exception e = new Exception("At least one future failed");
		consumers.forEach((consumer, future) -> {
			if (future.cause() != null) {
				LOG.error(future.cause());
				if (e.getCause() == null) {
					e.initCause(future.cause());
				} else {
					e.addSuppressed(future.cause());
				}
			}
		});
		return e;
	}

	@Override
	public boolean succeeded() {
		return consumers.values().stream().allMatch(Future::succeeded);
	}

	@Override
	public boolean failed() {
		return consumers.values().stream().anyMatch(Future::failed);
	}

	@Override
	public boolean isComplete() {
		if (super.isComplete()) { // has been marked explicitly
			return true;
		}
		if (consumers.isEmpty()) {
			return false;
		}
		return consumers.values().stream().allMatch(Future::isComplete);
	}

	public void join(Future<Void> future) {
		setHandler(res -> {
			if (res.succeeded()) {
				future.complete();
			} else {
				future.fail(res.cause());
			}
		});
	}

	public void join(Handler<AsyncResult<Void>> handler) {
		setHandler(handler);
	}
}