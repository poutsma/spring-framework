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
import java.util.concurrent.Exchanger;
import java.util.concurrent.Executor;
import java.util.concurrent.Flow;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

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

		subscriber.onSubscribe(new OutputStreamSubscription(subscriber, this.outputStreamHandler, this.executor));
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


	private static final class OutputStreamSubscription implements Flow.Subscription {

		private final Flow.Subscriber<? super ByteBuffer> subscriber;

		private final OutputStreamHandler outputStreamHandler;

		private final Executor executor;

		private final AtomicBoolean handlerInvoked = new AtomicBoolean();

		private final AtomicLong demand = new AtomicLong();

		private final Exchanger<ByteBuffer> exchanger = new Exchanger<>();

		private volatile boolean canceled = false;


		public OutputStreamSubscription(Flow.Subscriber<? super ByteBuffer> subscriber,
										OutputStreamHandler outputStreamHandler,
										Executor executor) {
			this.subscriber = subscriber;
			this.outputStreamHandler = outputStreamHandler;
			this.executor = executor;
		}


		@Override
		public void request(long n) {
			Assert.isTrue(n > 0, "request should be a positive number");

			long prev = this.demand.getAndAccumulate(n, (cur, giv) -> {
				long sum = cur + giv;
				return sum < 0 ? Long.MAX_VALUE : sum;
			});
			if (this.handlerInvoked.compareAndSet(false, true)) {
				this.executor.execute(this::invokeHandler);
			}
			if (prev == 0) {
				exchangeBuffer();
			}
		}

		private void invokeHandler() {
			// use BufferedOutputStream, so that written bytes are buffered
			// before publishing as byte buffer
			try (OutputStream outputStream = new BufferedOutputStream(
					new ExchangerOutputStream(this.exchanger, this.subscriber))) {

				this.outputStreamHandler.handle(outputStream);
			}
			catch (IOException ex) {
				if (!this.canceled) {
					this.subscriber.onError(ex);
				}
			}
		}

		@Override
		public void cancel() {
			this.canceled = true;
		}

		private void exchangeBuffer() {
			long demand = this.demand.get();
			try {
				while (demand > 0 && !this.canceled) {
					ByteBuffer byteBuffer = this.exchanger.exchange(null);
					if (byteBuffer != CLOSED) {
						demand = publishBuffer(byteBuffer);
					}
					else {
						this.subscriber.onComplete();
						demand = 0;
					}
				}
				if (this.canceled) {
					this.exchanger.exchange(CANCELED);
				}
			}
			catch (InterruptedException ex) {
				this.subscriber.onError(ex);
			}
		}

		private long publishBuffer(ByteBuffer byteBuffer) {
			this.subscriber.onNext(byteBuffer);
			return this.demand.decrementAndGet();
		}


	}

	private static final class ExchangerOutputStream extends OutputStream {

		private final AtomicReference<State> state = new AtomicReference<>(State.OPEN);

		private final Exchanger<ByteBuffer> exchanger;

		private final Flow.Subscriber<? super ByteBuffer> subscriber;


		public ExchangerOutputStream(Exchanger<ByteBuffer> exchanger, Flow.Subscriber<? super ByteBuffer> subscriber) {
			this.exchanger = exchanger;
			this.subscriber = subscriber;
		}


		@Override
		public void write(int b) throws IOException {
			this.state.get().write((byte) b, this);
		}

		@Override
		public void write(byte[] b) throws IOException {
			this.state.get().write(b, 0, b.length, this);
		}

		@Override
		public void write(byte[] b, int off, int len) throws IOException {
			this.state.get().write(b, off, len, this);
		}

		private void exchange(ByteBuffer byteBuffer) {
			try {
				ByteBuffer result = this.exchanger.exchange(byteBuffer);
				if (result == CANCELED) {
					this.state.compareAndSet(State.OPEN, State.CANCELED);
				}
			}
			catch (InterruptedException ex) {
				this.subscriber.onError(ex);
			}
		}

		@Override
		public void close() {
			if (this.state.compareAndSet(State.OPEN, State.CLOSED)) {
				try {
					this.exchanger.exchange(CLOSED);
				}
				catch (InterruptedException ex) {
					this.subscriber.onError(ex);
				}
			}
		}

		private enum State {

			OPEN {
				@Override
				public void write(byte b, ExchangerOutputStream wrapper) throws IOException {
					ByteBuffer byteBuffer = ByteBuffer.allocate(1);
					byteBuffer.put(b);
					byteBuffer.flip();
					wrapper.exchange(byteBuffer);
				}

				@Override
				public void write(byte[] b, int off, int len, ExchangerOutputStream wrapper) throws IOException {
					ByteBuffer byteBuffer = ByteBuffer.allocate(len);
					byteBuffer.put(b, off, len);
					byteBuffer.flip();
					wrapper.exchange(byteBuffer);
				}
			}, CLOSED {
				@Override
				public void write(byte b, ExchangerOutputStream wrapper) throws IOException {
					throw new IOException("Stream closed");
				}

				@Override
				public void write(byte[] bytes, int off, int len, ExchangerOutputStream wrapper) throws IOException {
					throw new IOException("Stream closed");
				}
			}, CANCELED {
				@Override
				public void write(byte b, ExchangerOutputStream wrapper) throws IOException {
					throw new IOException("Subscription has been cancelled");
				}

				@Override
				public void write(byte[] bytes, int off, int len, ExchangerOutputStream wrapper) throws IOException {
					throw new IOException("Subscription has been cancelled");
				}
			};

			public abstract void write(byte b, ExchangerOutputStream wrapper) throws IOException;

			public abstract void write(byte[] bytes, int off, int len, ExchangerOutputStream wrapper) throws IOException;

		}
	}



}
