package io.vertx.examples.feeds.utils.async;

import io.vertx.core.AsyncResult;

public class SimpleAsyncResult<T> implements AsyncResult<T> {

    protected final Throwable cause;
    protected final T result;

    public SimpleAsyncResult(String cause) {
        this(new RuntimeException(cause), null);
    }

    public SimpleAsyncResult(Throwable cause) {
        this(cause, null);
    }

    public SimpleAsyncResult(Throwable cause, T result) {
        this.cause = cause;
        this.result = result;
    }

    @Override
    public T result() {
        return result;
    }

    @Override
    public Throwable cause() {
        return cause;
    }

    @Override
    public boolean succeeded() {
        return cause == null;
    }

    @Override
    public boolean failed() {
        return cause != null;
    }
}
