/*
 * Copyright 2002-2023 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.springframework.core.io.buffer;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.locks.LockSupport;
import java.util.function.Consumer;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.reactivestreams.Subscriber;
import org.reactivestreams.Subscription;

import org.springframework.lang.Nullable;
import org.springframework.util.Assert;
import org.springframework.util.StreamUtils;

/**
 * @author Arjen Poutsma
 */
final class InputStreamSubscriber<T extends DataBuffer> extends InputStream implements Subscriber<T> {

	private static final Log logger = LogFactory.getLog(InputStreamSubscriber.class);

	private static final Object READY = new Object();


	private final AtomicReference<Object> parkedThread = new AtomicReference<>();

	private final AtomicReference<Subscription> subscription = new AtomicReference<>();

	private final AtomicReference<InputStream> current = new AtomicReference<>();

	private final AtomicReference<IOException> error = new AtomicReference<>();

	private final AtomicBoolean complete = new AtomicBoolean();

	private final Consumer<InputStream> inputStreamConsumer;

	private final Executor executor;


	private InputStreamSubscriber(Consumer<InputStream> inputStreamConsumer, Executor executor) {
		this.inputStreamConsumer = inputStreamConsumer;
		this.executor = executor;
	}


	public static <T extends DataBuffer> Subscriber<T> create(Consumer<InputStream> inputStreamConsumer,
			Executor executor) {
		Assert.notNull(inputStreamConsumer, "InputStreamHandler must not be null");
		Assert.notNull(executor, "Executor must not be null");

		return new InputStreamSubscriber<>(inputStreamConsumer, executor);
	}

	private void invokeHandler() {
		try (InputStream inputStream = StreamUtils.nonClosing(this)) {
			this.inputStreamConsumer.accept(inputStream);
		}
		catch (IOException ignored) {
			logger.error(ignored);
		}
	}


	// Subscription

	@Override
	public void onSubscribe(Subscription subscription) {
		if (logger.isDebugEnabled()) {
			logger.debug("onSubscribe: " + subscription);
		}
		if (this.subscription.compareAndSet(null, subscription)) {
			this.executor.execute(this::invokeHandler);
		}
		else {
			subscription.cancel();
		}
	}

	@Override
	public void onNext(DataBuffer dataBuffer) {
		if (logger.isDebugEnabled()) {
			logger.debug("onNext: " + dataBuffer);
		}
		InputStream is = dataBuffer.asInputStream(true);
		if (this.current.compareAndSet(null, is)) {
			resume();
		}
		else {
			throw new IllegalStateException();
		}
	}

	@Override
	public void onError(Throwable t) {
		if (logger.isDebugEnabled()) {
			logger.debug("onError: " + t.getMessage(), t);
		}
		IOException ioEx = convert(t);
		if (this.error.compareAndSet(null, ioEx)) {
			this.subscription.set(DisposedSubscription.INSTANCE);
			this.error.set(ioEx);
			resume();
		}
	}

	private static IOException convert(Throwable t) {
		if (t instanceof IOException ioEx) {
			return ioEx;
		}
		else if (t instanceof UncheckedIOException uncheckedIoEx) {
			return uncheckedIoEx.getCause();
		}
		else if (t instanceof ExecutionException executionEx) {
			Throwable cause = executionEx.getCause();

			if (cause instanceof UncheckedIOException uioEx) {
				return uioEx.getCause();
			}
			else if (cause instanceof IOException ioEx) {
				return ioEx;
			}
			else {
				return new IOException(cause.getMessage(), cause);
			}
		}
		else {
			return new IOException(t.getMessage(), t);
		}
	}

	@Override
	public void onComplete() {
		logger.debug("onComplete");
		if (this.complete.compareAndSet(false, true)) {
			this.subscription.set(DisposedSubscription.INSTANCE);
			resume();
		}
	}

	private void request() {
		Subscription subscription = this.subscription.get();
		if (subscription != null) {
			logger.debug("Requesting");
			subscription.request(1);
		}
		else {
			throw new IllegalStateException("Not subscribed yet");
		}
	}

	private void cancel() {
		Subscription subscription = this.subscription.getAndSet(DisposedSubscription.INSTANCE);
		if (subscription != null) {
			subscription.cancel();
		}
		else {
			throw new IllegalStateException("Not subscribed yet");
		}
	}

	// InputStream

	@Override
	public int read() throws IOException {
		logger.debug("read (int)");
		return readInternal(InputStream::read);
	}

	@Override
	public int read(byte[] b) throws IOException {
		logger.debug("read (byte[])");
		return readInternal(inputStream -> inputStream.read(b));
	}

	@Override
	public int read(byte[] b, int off, int len) throws IOException {
		logger.debug("read (byte[], int, int)");
		return readInternal(inputStream -> inputStream.read(b, off, len));
	}

	private int readInternal(ReadFunction function) throws IOException {
		InputStream inputStream = checkCurrentAndRequestIfNeeded();
		while (inputStream != null) {
			int result = function.read(inputStream);
			if (result == -1) {
				inputStream.close();
				this.current.set(null);
				inputStream = checkCurrentAndRequestIfNeeded();
			}
			else {
				return result;
			}
		}
		IOException ioEx = this.error.getAndSet(null);
		if (ioEx != null) {
			throw ioEx;
		}
		if (this.complete.get()) {
			return -1;
		}
		else {
			throw new IllegalStateException("Stream provides neither error, complete, nor next signal");
		}
	}

	@Nullable
	private InputStream checkCurrentAndRequestIfNeeded() {
		InputStream inputStream = this.current.get();
		while (this.subscription.get() != DisposedSubscription.INSTANCE) {
			if (inputStream != null) {
				break;
			}
			request();

			await();

			inputStream = this.current.get();
		}
		return inputStream;
	}

	@Override
	public void close() throws IOException {
		cancel();
		InputStream inputStream = this.current.get();
		if (inputStream != null) {
			inputStream.close();
		}
	}

	private void await() {
		Thread toUnpark = Thread.currentThread();

		while (true) {
			Object current = this.parkedThread.get();
			if (current == READY) {
				break;
			}

			if (current != null && current != toUnpark) {
				throw new IllegalStateException("Only one (Virtual)Thread can await!");
			}

			if (this.parkedThread.compareAndSet(null, toUnpark)) {
				LockSupport.park();
				// we don't just break here because park() can wake up spuriously
				// if we got a proper resume, get() == READY and the loop will quit above
			}
		}
		// clear the resume indicator so that the next await call will park without a resume()
		this.parkedThread.lazySet(null);
	}

	private void resume() {
		logger.debug("Resuming");
		if (this.parkedThread.get() != READY) {
			Object old = this.parkedThread.getAndSet(READY);
			if (old != READY) {
				LockSupport.unpark((Thread)old);
			}
		}
	}


	@FunctionalInterface
	private interface ReadFunction {

		int read(InputStream inputStream) throws IOException;

	}

	private static final class DisposedSubscription implements Subscription {

		public static final DisposedSubscription INSTANCE = new DisposedSubscription();


		@Override
		public void request(long n) {
		}

		@Override
		public void cancel() {

		}
	}


}
