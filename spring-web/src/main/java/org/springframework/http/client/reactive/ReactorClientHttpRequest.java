/*
 * Copyright 2002-2016 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.springframework.http.client.reactive;

import java.net.URI;
import java.util.Collection;

import io.netty.buffer.ByteBuf;
import io.netty.handler.codec.http.cookie.DefaultCookie;
import org.reactivestreams.Publisher;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.io.netty.http.HttpClientRequest;

import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.core.io.buffer.DataBufferFactory;
import org.springframework.core.io.buffer.NettyDataBufferFactory;
import org.springframework.http.HttpMethod;

/**
 * {@link ClientHttpRequest} implementation for the Reactor-Netty HTTP client.
 *
 * @author Brian Clozel
 * @since 5.0
 * @see reactor.io.netty.http.HttpClient
 */
public class ReactorClientHttpRequest extends AbstractClientHttpRequest {

	private final HttpMethod httpMethod;

	private final URI uri;

	private final HttpClientRequest httpRequest;

	private final NettyDataBufferFactory bufferFactory;


	public ReactorClientHttpRequest(HttpMethod httpMethod, URI uri,
			HttpClientRequest httpRequest) {
		this.httpMethod = httpMethod;
		this.uri = uri;
		this.httpRequest = httpRequest;
		this.bufferFactory = new NettyDataBufferFactory(httpRequest.delegate().alloc());
	}


	@Override
	public DataBufferFactory bufferFactory() {
		return this.bufferFactory;
	}

	@Override
	public HttpMethod getMethod() {
		return this.httpMethod;
	}

	@Override
	public URI getURI() {
		return this.uri;
	}

	@Override
	public Mono<Void> writeWith(Publisher<DataBuffer> body) {
		return applyBeforeCommit().then(this.httpRequest
				.send(Flux.from(body).map(NettyDataBufferFactory::toByteBuf)));
	}

	@Override
	public Mono<Void> writeAndFlushWith(Publisher<Publisher<DataBuffer>> body) {
		return applyBeforeCommit().then(Flux.from(body).
				map(bodyElement -> this.httpRequest.send(toByteBufs(bodyElement))).then());
	}

	private static Publisher<ByteBuf> toByteBufs(Publisher<DataBuffer> dataBuffers) {
		return Flux.from(dataBuffers).
				map(NettyDataBufferFactory::toByteBuf);
	}

	@Override
	public Mono<Void> setComplete() {
		return applyBeforeCommit().then(httpRequest.sendHeaders());
	}

	@Override
	protected void writeHeaders() {
		getHeaders().entrySet()
				.forEach(e -> this.httpRequest.headers().set(e.getKey(), e.getValue()));
	}

	@Override
	protected void writeCookies() {
		getCookies().values().stream().flatMap(Collection::stream)
				.map(cookie -> new DefaultCookie(cookie.getName(), cookie.getValue()))
				.forEach(this.httpRequest::addCookie);
	}

}