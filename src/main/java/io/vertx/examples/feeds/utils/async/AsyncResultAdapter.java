package io.vertx.examples.feeds.utils.async;

import io.vertx.core.AsyncResult;

import java.util.function.Function;

public class AsyncResultAdapter<T, R> extends SimpleAsyncResult<R> {

	public AsyncResultAdapter(AsyncResult<T> original, Function<T, R> transform) {
		super(original.cause(), original.result() == null ? null : transform
				.apply(original.result()));
	}

}
