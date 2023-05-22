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

import java.io.OutputStreamWriter;
import java.io.Writer;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.concurrent.Flow;

import org.junit.jupiter.api.Test;
import org.reactivestreams.FlowAdapters;
import reactor.core.publisher.Flux;
import reactor.test.StepVerifier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIOException;

/**
 * @author Arjen Poutsma
 */
class OutputStreamPublisherTests {

	private final Executor executor = Executors.newSingleThreadExecutor();

	@Test
	void basic() {
		Flow.Publisher<ByteBuffer> flowPublisher = OutputStreamPublisher.create(outputStream -> {
			try (Writer writer = new OutputStreamWriter(outputStream, StandardCharsets.UTF_8)) {
				writer.write("foo");
				writer.write("bar");
				writer.write("baz");
			}
		}, this.executor);
		Flux<CharSequence> flux = toString(flowPublisher);

		StepVerifier.create(flux)
				.assertNext(s -> assertThat(s).isEqualTo("foobarbaz"))
				.verifyComplete();
	}

	@Test
	void flush() {
		Flow.Publisher<ByteBuffer> flowPublisher = OutputStreamPublisher.create(outputStream -> {
			try (Writer writer = new OutputStreamWriter(outputStream, StandardCharsets.UTF_8)) {
				writer.write("foo");
				writer.flush();
				writer.write("bar");
				writer.flush();
				writer.write("baz");
				writer.flush();
			}
		}, this.executor);
		Flux<CharSequence> flux = toString(flowPublisher);

		StepVerifier.create(flux)
				.assertNext(s -> assertThat(s).isEqualTo("foo"))
				.assertNext(s -> assertThat(s).isEqualTo("bar"))
				.assertNext(s -> assertThat(s).isEqualTo("baz"))
				.verifyComplete();
	}

	@Test
	void cancel() throws InterruptedException {
		CountDownLatch latch = new CountDownLatch(1);

		Flow.Publisher<ByteBuffer> flowPublisher = OutputStreamPublisher.create(outputStream -> {
			try (Writer writer = new OutputStreamWriter(outputStream, StandardCharsets.UTF_8)) {
				writer.write("foo");
				writer.flush();
				writer.write("bar");
				writer.flush();
				assertThatIOException().isThrownBy(() -> {
							writer.write("baz");
							writer.flush();
						})
						.withMessage("Subscription has been cancelled");
				latch.countDown();
			}
		}, this.executor);
		Flux<CharSequence> flux = toString(flowPublisher);

		StepVerifier.create(flux, 1)
				.assertNext(s -> assertThat(s).isEqualTo("foo"))
				.thenCancel()
				.verify();

		latch.await();
	}

	@Test
	void closed() throws InterruptedException {
		CountDownLatch latch = new CountDownLatch(1);

		Flow.Publisher<ByteBuffer> flowPublisher = OutputStreamPublisher.create(outputStream -> {
			Writer writer = new OutputStreamWriter(outputStream, StandardCharsets.UTF_8);
			writer.write("foo");
			writer.close();
			assertThatIOException().isThrownBy(() -> writer.write("bar"))
					.withMessage("Stream closed");
			latch.countDown();
		}, this.executor);
		Flux<CharSequence> flux = toString(flowPublisher);

		StepVerifier.create(flux)
				.assertNext(s -> assertThat(s).isEqualTo("foo"))
				.verifyComplete();

		latch.await();
	}

	private static Flux<CharSequence> toString(Flow.Publisher<ByteBuffer> flowPublisher) {
		return Flux.from(FlowAdapters.toPublisher(flowPublisher))
				.map(bb -> StandardCharsets.UTF_8.decode(bb).toString());
	}

}
