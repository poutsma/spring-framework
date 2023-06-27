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

package org.springframework.http.client;

import java.io.BufferedOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.util.Objects;
import java.util.concurrent.Executor;
import java.util.concurrent.Flow;
import java.util.concurrent.atomic.AtomicLongFieldUpdater;
import java.util.concurrent.atomic.AtomicReferenceFieldUpdater;
import java.util.concurrent.locks.LockSupport;

import org.springframework.util.Assert;

/**
 * Bridges between {@link OutputStream} and
 * {@link Flow.Publisher Flow.Publisher&lt;ByteBuffer&gt;}.
 *
 * @author Arjen Poutsma
 * @since 6.1
 * @see #create(OutputStreamHandler, Executor)
 */
final class OutputStreamPublisher implements Flow.Publisher<ByteBuffer> {

	private static final ByteBuffer CLOSED = ByteBuffer.allocate(0);

	private static final ByteBuffer CANCELED = ByteBuffer.allocate(0);


	private final OutputStreamHandler outputStreamHandler;

	private final Executor executor;


	private OutputStreamPublisher(OutputStreamHandler outputStreamHandler, Executor executor) {
		this.outputStreamHandler = outputStreamHandler;
		this.executor = executor;
	}


	/**
	 * Creates a new {@code Publisher<ByteBuffer>} based on bytes written to a
	 * {@code OutputStream}.
	 * <ul>
	 * <li>The parameter {@code outputStreamHandler} is invoked once per
	 * subscription of the returned {@code Publisher}, when the first
	 * {@code ByteBuffer} is
	 * {@linkplain Flow.Subscription#request(long) requested}.</li>
	 * <li>Each {@link OutputStream#write(byte[], int, int) OutputStream.write()}
	 * invocation that {@code outputStreamHandler} makes will result in a
	 * {@linkplain Flow.Subscriber#onNext(Object) published} {@code ByteBuffer}
	 * if there is {@linkplain Flow.Subscription#request(long) demand}.</li>
	 * <li>If there is <em>no demand</em>, {@code OutputStream.write()} will block
	 * until there is.</li>
	 * <li>If the subscription is {@linkplain Flow.Subscription#cancel() cancelled},
	 * {@code OutputStream.write()} will throw a {@code IOException}.</li>
	 * <li>{@linkplain OutputStream#close() Closing} the {@code OutputStream}
	 * will result in a {@linkplain Flow.Subscriber#onComplete() complete} signal.</li>
	 * <li>Any {@code IOException}s thrown from {@code outputStreamHandler} will
	 * be dispatched to the {@linkplain Flow.Subscriber#onError(Throwable) Subscriber}.
	 * </ul>
	 * @param outputStreamHandler invoked when the first buffer is requested
	 * @param executor used to invoke the {@code outputStreamHandler}
	 * @return a {@code Publisher<ByteBuffer>} based on bytes written by
	 * {@code outputStreamHandler}
	 */
	public static Flow.Publisher<ByteBuffer> create(OutputStreamHandler outputStreamHandler, Executor executor) {
		Assert.notNull(outputStreamHandler, "OutputStreamHandler must not be null");
		Assert.notNull(executor, "Executor must not be null");

		return new OutputStreamPublisher(outputStreamHandler, executor);
	}


	@Override
	public void subscribe(Flow.Subscriber<? super ByteBuffer> subscriber) {
		Objects.requireNonNull(subscriber, "Subscriber must not be null");

		OutputStreamSubscription subscription = new OutputStreamSubscription(subscriber, this.outputStreamHandler);
		subscriber.onSubscribe(subscription);
		this.executor.execute(subscription::invokeHandler);
	}


	/**
	 * Defines the contract for handling the {@code OutputStream} provided by
	 * the {@code OutputStreamPublisher}.
	 */
	@FunctionalInterface
	public interface OutputStreamHandler {

		/**
		 * Use the given stream for writing.
		 * <ul>
		 * <li>If the linked subscription has
		 * {@linkplain Flow.Subscription#request(long) demand}, any
		 * {@linkplain OutputStream#write(byte[], int, int) written} bytes
		 * will be {@linkplain Flow.Subscriber#onNext(Object) published} to the
		 * {@link Flow.Subscriber Subscriber}.</li>
		 * <li>If there is no demand, any
		 * {@link OutputStream#write(byte[], int, int) write()} invocations will
		 * block until there is demand.</li>
		 * <li>If the linked subscription is
		 * {@linkplain Flow.Subscription#cancel() cancelled},
		 * {@link OutputStream#write(byte[], int, int) write()} invocations will
		 * result in a {@code IOException}.</li>
		 * </ul>
		 * @param outputStream the stream to write to
		 * @throws IOException any thrown I/O errors will be dispatched to the
		 * {@linkplain Flow.Subscriber#onError(Throwable) Subscriber}
		 */
		void handle(OutputStream outputStream) throws IOException;

	}


	private static final class OutputStreamSubscription extends OutputStream implements Flow.Subscription {

		static final Object READY = new Object();

		private final Flow.Subscriber<? super ByteBuffer> actual;

		private final OutputStreamHandler outputStreamHandler;

		private volatile long requested;
		static final AtomicLongFieldUpdater<OutputStreamSubscription> REQUESTED =
				AtomicLongFieldUpdater.newUpdater(OutputStreamSubscription.class, "requested");

		private volatile Object parkedThread;
		static final AtomicReferenceFieldUpdater<OutputStreamSubscription, Object> PARKED_THREAD =
				AtomicReferenceFieldUpdater.newUpdater(OutputStreamSubscription.class, Object.class, "parkedThread");

		long produced;


		public OutputStreamSubscription(Flow.Subscriber<? super ByteBuffer> actual,
										OutputStreamHandler outputStreamHandler) {
			this.actual = actual;
			this.outputStreamHandler = outputStreamHandler;
		}

		@Override
		public void write(int b) throws IOException {
			long r = getCancellableRequestOrAwait();

			ByteBuffer byteBuffer = ByteBuffer.allocate(1);
			byteBuffer.put((byte) b);
			byteBuffer.flip();
			this.actual.onNext(byteBuffer);

			produceIfNeeded(r);
		}

		@Override
		public void write(byte[] b) throws IOException {
			write(b, 0, b.length);
		}

		@Override
		public void write(byte[] b, int off, int len) throws IOException {
			long r = getCancellableRequestOrAwait();

			ByteBuffer byteBuffer = ByteBuffer.allocate(len);
			byteBuffer.put(b, off, len);
			byteBuffer.flip();
			this.actual.onNext(byteBuffer);

			produceIfNeeded(r);
		}

		private long getCancellableRequestOrAwait() throws IOException {
			long r;
			for (;;) {
				r = this.requested;
				if (r == Long.MIN_VALUE) {
					throw new IOException("Subscription has been cancelled");
				}

				if (r != 0) {
					return r;
				}

				await();
			}
		}

		private void produceIfNeeded(long requested) throws IOException {
			long p = this.produced + 1;
			if (p == requested) {
				if (p > 0) {
					requested = Operators.producedCancellable(REQUESTED, this, p);
				}

				if (requested == Long.MIN_VALUE) {
					throw new IOException("Subscription has been cancelled");
				}

				this.produced = 0;

				return;
			}

			this.produced = p;
		}

		@Override
		public void close() {
		}


		private void invokeHandler() {
			// use BufferedOutputStream, so that written bytes are buffered
			// before publishing as byte buffer
			try (OutputStream outputStream = new BufferedOutputStream(this)) {
				this.outputStreamHandler.handle(outputStream);
			}
			catch (IOException ex) {
				this.actual.onError(ex);
				return;
			}

			this.actual.onComplete();
		}


		@Override
		public void request(long n) {
			if (Operators.validate(n)) {
				if (Operators.addCapCancellable(REQUESTED, this, n) == 0) {
					resume();
				}
			}
		}

		@Override
		public void cancel() {
			long previousState = REQUESTED.getAndSet(this, Long.MIN_VALUE);
			if (previousState == Long.MIN_VALUE || previousState > 0) {
				return;
			}

			resume();
		}

		private void await() {
			Thread toUnpark = Thread.currentThread();

			for (;;) {
				Object current = parkedThread;
				if (current == READY) {
					break;
				}

				if (current != null && current != toUnpark) {
					throw new IllegalStateException("Only one (Virtual)Thread can await!");
				}

				if (PARKED_THREAD.compareAndSet(this, null, toUnpark)) {
					LockSupport.park();
					// we don't just break here because park() can wake up spuriously
					// if we got a proper resume, get() == READY and the loop will quit above
				}
			}
			// clear the resume indicator so that the next await call will park without a resume()
			PARKED_THREAD.lazySet(this, null);
		}

		private void resume() {
			if (parkedThread != READY) {
				Object old = PARKED_THREAD.getAndSet(this, READY);
				if (old != READY) {
					LockSupport.unpark((Thread)old);
				}
			}
		}
	}
}
