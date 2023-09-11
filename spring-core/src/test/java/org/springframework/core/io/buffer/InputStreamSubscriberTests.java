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

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.time.Duration;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.junit.jupiter.api.Test;
import org.reactivestreams.Subscriber;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import static java.nio.charset.StandardCharsets.UTF_8;

/**
 * @author Arjen Poutsma
 */
class InputStreamSubscriberTests {

	private static final Log logger = LogFactory.getLog(InputStreamSubscriber.class);

	@Test
	void basic() throws InterruptedException {
		DefaultDataBufferFactory factory = new DefaultDataBufferFactory();
		Flux<DataBuffer> source = Flux.interval(Duration.ofMillis(300))
				.take(10)
				.map(l -> {
					String s = l + "\n";
					return factory.wrap(s.getBytes(UTF_8));
				});

//		source.subscribe(dataBuffer -> System.out.println(dataBuffer.toString(UTF_8)));

		CountDownLatch latch = new CountDownLatch(1);
		Subscriber<DataBuffer> subscriber = InputStreamSubscriber.create(inputStream -> {
			try (BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream, UTF_8))) {
				String line;
				while ((line = reader.readLine()) != null) {
					logger.info(line.stripTrailing());
				}
			}
			catch (IOException ex) {
				ex.printStackTrace();
			}
			finally {
				latch.countDown();
			}
		}, Executors.newSingleThreadExecutor());

		source.subscribe(subscriber);
		latch.await();
	}

	@Test
	void error() throws InterruptedException {
		DefaultDataBufferFactory factory = new DefaultDataBufferFactory();
		Flux<DataBuffer> source = Flux.concat(
				Mono.just(factory.wrap("foo".getBytes(UTF_8))),
				Mono.error(new RuntimeException("foo"))
		);

		CountDownLatch latch = new CountDownLatch(1);
		Subscriber<DataBuffer> subscriber = InputStreamSubscriber.create(inputStream -> {
			try (BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream, UTF_8))) {
				String line;
				while ((line = reader.readLine()) != null) {
					logger.info(line.stripTrailing());
				}
			}
			catch (IOException ex) {
				ex.printStackTrace();
			}
			finally {
				latch.countDown();
			}
		}, Executors.newSingleThreadExecutor());

		source.subscribe(subscriber);
		latch.await();
	}

}
