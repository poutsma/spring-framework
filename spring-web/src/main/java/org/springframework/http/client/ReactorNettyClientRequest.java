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

import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.URI;
import java.time.Duration;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufAllocator;
import io.netty.buffer.ByteBufOutputStream;
import reactor.core.publisher.Mono;
import reactor.netty.http.client.HttpClient;

import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.lang.Nullable;
import org.springframework.util.StreamUtils;

/**
 * {@link ClientHttpRequest} implementation for the Reactor-Netty HTTP client.
 * Created via the {@link ReactorNettyClientRequestFactory}.
 * @author Arjen Poutsma
 * @since 6.1
 */
final class ReactorNettyClientRequest extends AbstractStreamingClientHttpRequest {

	private final HttpClient httpClient;

	private final HttpMethod method;

	private final URI uri;

	private final Duration connectTimeout;

	private final Duration readTimeout;

	private final int bufferCapacity;


	public ReactorNettyClientRequest(HttpClient httpClient, URI uri, HttpMethod method, Duration connectTimeout,
			Duration readTimeout, int bufferCapacity) {

		this.httpClient = httpClient;
		this.method = method;
		this.uri = uri;
		this.connectTimeout = connectTimeout;
		this.readTimeout = readTimeout;
		this.bufferCapacity = bufferCapacity;
	}


	@Override
	public HttpMethod getMethod() {
		return this.method;
	}

	@Override
	public URI getURI() {
		return this.uri;
	}


	@Override
	protected ClientHttpResponse executeInternal(HttpHeaders headers, @Nullable Body body) throws IOException {
		HttpClient.RequestSender requestSender = this.httpClient
				.request(io.netty.handler.codec.http.HttpMethod.valueOf(this.method.name()));

		requestSender = (this.uri.isAbsolute() ? requestSender.uri(this.uri) : requestSender.uri(this.uri.toString()));

		try {
			return requestSender.send((reactorRequest, nettyOutbound) -> {
						headers.forEach((key, value) -> reactorRequest.requestHeaders().set(key, value));

						if (body != null) {
							return nettyOutbound.send(bodyToPublisher(body, nettyOutbound.alloc()));
						}
						else {
							return nettyOutbound;
						}
					})
					.responseConnection((reactorResponse, connection) ->
							Mono.just(new ReactorNettyClientResponse(reactorResponse, connection, this.readTimeout)))
					.next()
					.block(this.connectTimeout);
		}
		catch (RuntimeException ex) { // Exceptions.ReactiveException is package private
			Throwable cause = ex.getCause();

			if (cause instanceof UncheckedIOException uioEx) {
				throw uioEx.getCause();
			}
			else if (cause instanceof IOException ioEx) {
				throw ioEx;
			}
			else {
				throw ex;
			}
		}
	}


	private Mono<ByteBuf> bodyToPublisher(Body body, ByteBufAllocator allocator) {
		ByteBuf buf = allocator.buffer(this.bufferCapacity);
		try (ByteBufOutputStream outputStream = new ByteBufOutputStream(buf)) {
			body.writeTo(StreamUtils.nonClosing(outputStream));
			return Mono.just(buf);
		}
		catch (IOException ex) {
			return Mono.error(ex);
		}
	}
}
