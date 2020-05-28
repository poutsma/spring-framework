/*
 * Copyright 2002-2020 the original author or authors.
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

package org.springframework.http.codec.multipart;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.ByteBuffer;
import java.nio.channels.WritableByteChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.reactivestreams.Subscription;
import reactor.core.publisher.BaseSubscriber;
import reactor.core.publisher.Flux;
import reactor.core.publisher.FluxSink;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import org.springframework.core.codec.DecodingException;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.core.io.buffer.DataBufferFactory;
import org.springframework.core.io.buffer.DataBufferLimitException;
import org.springframework.core.io.buffer.DataBufferUtils;
import org.springframework.core.io.buffer.DefaultDataBufferFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.util.FastByteArrayOutputStream;

/**
 * Subscribes to a token stream (i.e. the result of
 * {@link MultipartParser#parse(Flux, byte[], int)}, and produces a flux of {@link Part} objects.
 *
 * @author Arjen Poutsma
 * @since 5.3
 */
final class PartGenerator extends BaseSubscriber<MultipartParser.Token> {

	private static final DataBufferFactory bufferFactory = new DefaultDataBufferFactory();

	private static final Log logger = LogFactory.getLog(PartGenerator.class);

	private final AtomicReference<State> state = new AtomicReference<>(new InitialState());

	private final FluxSink<Part> sink;

	private final int maxParts;

	private final boolean streaming;

	private final int maxInMemorySize;

	private final long maxDiskUsagePerPart;

	private final Mono<Path> fileStorageDirectory;

	private final AtomicInteger partCount = new AtomicInteger();


	private final AtomicBoolean requestOutstanding = new AtomicBoolean();


	private PartGenerator(FluxSink<Part> sink, int maxParts, int maxInMemorySize, long maxDiskUsagePerPart,
			boolean streaming, Mono<Path> fileStorageDirectory) {

		this.sink = sink;
		this.maxParts = maxParts;
		this.maxInMemorySize = maxInMemorySize;
		this.maxDiskUsagePerPart = maxDiskUsagePerPart;
		this.streaming = streaming;
		this.fileStorageDirectory = fileStorageDirectory;
	}

	/**
	 * Creates parts from a given stream of tokens.
	 */
	public static Flux<Part> createParts(Flux<MultipartParser.Token> tokens, int maxParts, int maxInMemorySize,
			long maxDiskUsagePerPart, boolean streaming, Mono<Path> fileStorageDirectory) {

		return Flux.create(sink -> {
			PartGenerator creator = new PartGenerator(sink, maxParts, maxInMemorySize, maxDiskUsagePerPart, streaming,
					fileStorageDirectory);

			sink.onCancel(creator::cancel);
			sink.onRequest(l -> creator.requestToken());
			sink.onDispose(creator);
			tokens.subscribe(creator);
		});
	}

	@Override
	protected void hookOnSubscribe(Subscription subscription) {
		requestToken();
	}

	@Override
	protected void hookOnNext(MultipartParser.Token token) {
		this.requestOutstanding.set(false);
		if (token instanceof MultipartParser.HeadersToken) {
			// finish previous part
			this.state.get().onComplete();

			if (tooManyParts()) {
				return;
			}

			newPart(token.headers());
		}
		else {
			this.state.get().onBody(token.buffer());
		}
	}

	private void newPart(HttpHeaders headers) {
		if (isFormField(headers)) {
			if (this.maxInMemorySize == -1) {
				emitError(new DataBufferLimitException("Form fields can not be read with memory usage limit of -1"));
				return;
			}
			changeState(new FormFieldState(headers));
			requestToken();
		}
		else if (!this.streaming) {
			if (this.maxInMemorySize != -1) {
				changeState(new InMemoryState(headers));
				requestToken();
			}
			else {
				createFileState(headers, 0).subscribe(newState -> {
							changeState(newState);
							requestToken();
						},
						this::emitError);
			}
		}
		else {
			Flux<DataBuffer> content = Flux.create(contentSink -> {
				changeState(new StreamingState(contentSink));
				contentSink.onRequest(l -> requestToken());
				requestToken();
			});
			emitPart(DefaultParts.part(headers, content));
		}
	}

	@Override
	protected void hookOnComplete() {
		logger.trace("hookOnComplete");
		if (this.state.get().onComplete()) {
			emitComplete();
		}
	}

	@Override
	protected void hookOnError(Throwable throwable) {
		this.state.get().onError(throwable);
		this.sink.error(throwable);
	}

	@Override
	public void dispose() {
		this.state.get().dispose();
		cancel();
	}

	void emitPart(Part part) {
		if (logger.isTraceEnabled()) {
			logger.trace("Emitting: " + part);
		}
		this.sink.next(part);
	}

	void emitComplete() {
		logger.trace("Emitting complete");
		this.sink.complete();
	}


	void emitError(Throwable t) {
		if (logger.isTraceEnabled()) {
			logger.trace("Emitting error: " + t);
		}
		cancel();
		this.sink.error(t);
	}

	void changeState(State newState) {
		State oldState = this.state.getAndSet(newState);
		if (logger.isTraceEnabled()) {
			logger.trace("Changed state: " + oldState + " -> " + newState);
		}
		oldState.dispose();
	}

	Mono<FileState> createFileState(HttpHeaders headers, long byteCount) {
		return this.fileStorageDirectory
				.map(directory -> {
					try {
						Path tempFile = Files.createTempFile(directory, "multipart-data", ".multipart");
						if (logger.isTraceEnabled()) {
							logger.trace("Storing multipart data in file " + tempFile);
						}
						WritableByteChannel channel = Files.newByteChannel(tempFile, StandardOpenOption.WRITE);
						return new FileState(headers, tempFile, channel, byteCount);
					}
					catch (IOException ex) {
						throw new UncheckedIOException("Could not create temp file", ex);
					}
				})
				.subscribeOn(Schedulers.boundedElastic());
	}

	void requestToken() {
		if (upstream() != null &&
				!this.sink.isCancelled() &&
				this.sink.requestedFromDownstream() > 0 &&
				this.requestOutstanding.compareAndSet(false, true)) {
			logger.trace("Requesting token");
			request(1);
		}
		else {
			logger.trace("Ignoring token request");
		}
	}

	private boolean tooManyParts() {
		int count = this.partCount.incrementAndGet();
		if (this.maxParts > 0 && count > this.maxParts) {
			emitError(new DecodingException("Too many parts (" + count + "/" + this.maxParts + " allowed)"));
			return true;
		}
		else {
			return false;
		}
	}

	private static boolean isFormField(HttpHeaders headers) {
		MediaType contentType = headers.getContentType();
		return (contentType == null || MediaType.TEXT_PLAIN.equalsTypeAndSubtype(contentType))
				&& headers.getContentDisposition().getFilename() == null;
	}

	/**
	 * Represents the internal state of the {@link PartGenerator} for
	 * creating a single {@link Part}.
	 * {@link State} instances are stateful, and created when a new
	 * {@link MultipartParser.HeadersToken} is accepted (see
	 * {@link #newPart(HttpHeaders)}.
	 * The following rules determine which state the creator will have:
	 * <ol>
	 * <li>If the part is a {@linkplain #isFormField(HttpHeaders) form field},
	 * the creator will be in the {@link FormFieldState}.</li>
	 * <li>If {@linkplain #streaming} is enabled, the creator will be in the
	 * {@link StreamingState}.</li>
	 * <li>Otherwise, the creator will initially be in the
	 * {@link InMemoryState}, but will switch over to {@link FileState} when
	 * the part byte count exceeds {@link #maxInMemorySize}.</li>
	 * </ol>
	 */
	private interface State {

		/**
		 * Invoked when a {@link MultipartParser.BodyToken} is received.
		 */
		void onBody(DataBuffer dataBuffer);

		/**
		 * Invoked when all tokens for the part have been received.
		 * @return {@code true} if the part sink can be completed after calling
		 * this method; {@code false} otherwise (i.e. when waiting for a temp
		 * file to be created, see {@link InMemoryState#onComplete()})
		 */
		boolean onComplete();

		/**
		 * Invoked when an error has been received.
		 */
		default void onError(Throwable throwable) {
		}

		/**
		 * Cleans up any state.
		 */
		default void dispose() {
		}
	}


	/**
	 * The initial state of the creator. Throws an exception for {@link #onBody(DataBuffer)}.
	 */
	private final class InitialState implements State {

		private InitialState() {
		}

		@Override
		public void onBody(DataBuffer dataBuffer) {
			emitError(new IllegalStateException("Body token not expected"));
		}

		@Override
		public boolean onComplete() {
			return true;
		}

		@Override
		public String toString() {
			return "INITIAL";
		}
	}

	/**
	 * The creator state when a {@linkplain #isFormField(HttpHeaders) form field} is received.
	 * Stores all body buffers in memory (up until {@link #maxInMemorySize}).
	 */
	private final class FormFieldState implements State {

		private final FastByteArrayOutputStream value = new FastByteArrayOutputStream();

		private final HttpHeaders headers;

		public FormFieldState(HttpHeaders headers) {
			this.headers = headers;
		}

		@Override
		public void onBody(DataBuffer dataBuffer) {
			int size = this.value.size() + dataBuffer.readableByteCount();
			if (size < PartGenerator.this.maxInMemorySize) {
				store(dataBuffer);
				requestToken();
			}
			else {
				emitError(new DataBufferLimitException("Form field value exceeded the memory usage limit of " +
						PartGenerator.this.maxInMemorySize + " bytes"));
			}
		}

		@Override
		public boolean onComplete() {
			byte[] bytes = this.value.toByteArrayUnsafe();
			String value = new String(bytes, MultipartUtils.charset(this.headers));
			emitPart(DefaultParts.formFieldPart(this.headers, value));
			return true;
		}

		private void store(DataBuffer dataBuffer) {
			try {
				byte[] bytes = new byte[dataBuffer.readableByteCount()];
				dataBuffer.read(bytes);
				this.value.write(bytes);
			}
			catch (IOException ex) {
				emitError(ex);
			}
			finally {
				DataBufferUtils.release(dataBuffer);
			}
		}

		@Override
		public String toString() {
			return "FORM-FIELD";
		}

	}


	/**
	 * The creator state when {@link #streaming} is {@code true} (and not handling a form field).
	 * Relays all received buffers to a sink.
	 */
	private final class StreamingState implements State {

		private final FluxSink<DataBuffer> bodySink;

		public StreamingState(FluxSink<DataBuffer> bodySink) {
			this.bodySink = bodySink;
		}

		@Override
		public void onBody(DataBuffer dataBuffer) {
			if (!this.bodySink.isCancelled()) {
				this.bodySink.next(dataBuffer);
				if (this.bodySink.requestedFromDownstream() > 0) {
					requestToken();
				}
			}
			else {
				DataBufferUtils.release(dataBuffer);
				// even though the body sink is canceled, the (outer) part sink might not be
				requestToken();
			}
		}

		@Override
		public boolean onComplete() {
			this.bodySink.complete();
			return true;
		}

		@Override
		public void onError(Throwable throwable) {
			this.bodySink.error(throwable);
		}

		@Override
		public String toString() {
			return "STREAMING";
		}

	}

	/**
	 * The creator state when {@link #streaming} is {@code false} (and not
	 * handling a form field). Stores all received buffers in a queue.
	 * If the byte count exceeds {@link #maxInMemorySize}, the creator state
	 * is changed to {@link FileState}.
	 */
	private final class InMemoryState implements State {

		private final AtomicLong byteCount = new AtomicLong();

		private final Queue<DataBuffer> content = new ConcurrentLinkedQueue<>();

		private final HttpHeaders headers;

		private final AtomicBoolean completed = new AtomicBoolean();


		public InMemoryState(HttpHeaders headers) {
			this.headers = headers;
		}

		@Override
		public void onBody(DataBuffer dataBuffer) {
			long prevCount = this.byteCount.get();
			long count = this.byteCount.addAndGet(dataBuffer.readableByteCount());
			if (count <= PartGenerator.this.maxInMemorySize) {
				this.content.add(dataBuffer);
				requestToken();
			}
			else if (prevCount <= PartGenerator.this.maxInMemorySize) {
				switchToFile(dataBuffer, count);
			}
			else {
				emitError(new IllegalStateException("Body token not expected"));
			}
		}

		private void switchToFile(DataBuffer current, long byteCount) {
			createFileState(this.headers, byteCount)
					.subscribe(newState -> {
								for (DataBuffer buffer : this.content) {
									newState.writeBuffer(buffer);
								}
								this.content.clear();
								newState.writeBuffer(current);

								changeState(newState);
								if (!this.completed.get()) {
									requestToken();
								}
								else {
									newState.onComplete();
									emitComplete();
								}
							},
							PartGenerator.this::emitError);
		}

		@Override
		public boolean onComplete() {
			this.completed.set(true);
			long count = this.byteCount.get();
			if (count < PartGenerator.this.maxInMemorySize) {
				emitMemoryPart(count);
				return true;
			}
			else {
				return false;
			}
		}

		private void emitMemoryPart(long count) {
			byte[] bytes = new byte[(int) count];
			int idx = 0;
			for (DataBuffer buffer : this.content) {
				int len = buffer.readableByteCount();
				buffer.read(bytes, idx, len);
				idx += len;
				DataBufferUtils.release(buffer);
			}
			this.content.clear();
			if (logger.isTraceEnabled()) {
				logger.trace("Emitting memory storage for " + count + " bytes");
			}
			Flux<DataBuffer> content = Flux.just(bufferFactory.wrap(bytes));
			emitPart(DefaultParts.part(this.headers, content));
		}

		@Override
		public void dispose() {
			this.content.forEach(DataBufferUtils::release);
		}

		@Override
		public String toString() {
			return "IN-MEMORY";
		}


	}


	/**
	 * The creator state when the byte count exceeds {@link #maxInMemorySize}.
	 * Stores all received buffers in a file. If the byte count exceeds
	 * {@link #maxDiskUsagePerPart}. an exception is emitted.
	 */
	private final class FileState implements State {

		private static final int BUFFER_SIZE = 1024;

		private final HttpHeaders headers;

		private final Path file;

		private final WritableByteChannel channel;

		private final AtomicLong byteCount;


		public FileState(HttpHeaders headers, Path file, WritableByteChannel channel, long byteCount) {
			this.headers = headers;
			this.file = file;
			this.channel = channel;
			this.byteCount = new AtomicLong(byteCount);
		}

		@Override
		public void onBody(DataBuffer dataBuffer) {
			long count = this.byteCount.addAndGet(dataBuffer.readableByteCount());
			if (count <= PartGenerator.this.maxDiskUsagePerPart || PartGenerator.this.maxDiskUsagePerPart == -1) {
				writeBuffer(dataBuffer);
				requestToken();
			}
			else {
				emitError(new DataBufferLimitException(
						"Part exceeded the disk usage limit of " + PartGenerator.this.maxDiskUsagePerPart +
								" bytes"));
			}
		}

		void writeBuffer(DataBuffer dataBuffer) {
			try {
				ByteBuffer byteBuffer = dataBuffer.asByteBuffer();
				while (byteBuffer.hasRemaining()) {
					this.channel.write(byteBuffer);
				}
			}
			catch (IOException ex) {
				emitError(ex);
			}
			finally {
				DataBufferUtils.release(dataBuffer);
			}
		}

		@Override
		public boolean onComplete() {
			if (logger.isTraceEnabled()) {
				logger.trace("Emitting file storage " + this.file);
			}
			closeChannel();
			Flux<DataBuffer> content = DataBufferUtils.read(this.file, bufferFactory, BUFFER_SIZE);
			emitPart(DefaultParts.part(this.headers, content));
			return true;
		}

		@Override
		public void dispose() {
			closeChannel();
		}

		private void closeChannel() {
			try {
				if (this.channel.isOpen()) {
					this.channel.close();
				}
			}
			catch (IOException ex) {
				logger.debug("Could not close channel", ex);
			}
		}

		@Override
		public String toString() {
			return "FILE";
		}

	}


}
