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
import java.net.URI;
import java.time.Duration;

import io.netty.channel.ChannelOption;
import reactor.netty.http.client.HttpClient;

import org.springframework.http.HttpMethod;
import org.springframework.util.Assert;

/**
 * Reactor-Netty implementation of {@link ClientHttpRequestFactory}.
 *
 * @author Arjen Poutsma
 * @since 6.1
 */
public class ReactorNettyClientRequestFactory implements ClientHttpRequestFactory {

	private final HttpClient httpClient;

	private Duration exchangeTimeout = Duration.ofSeconds(5);

	private Duration readTimeout = Duration.ofSeconds(10);

	private int requestBufferCapacity = 1024;


	public ReactorNettyClientRequestFactory() {
		this(HttpClient.create().compress(true));
	}

	public ReactorNettyClientRequestFactory(HttpClient httpClient) {
		this.httpClient = httpClient;
	}

	/**
	 * Set the underlying connect timeout in milliseconds.
	 * A value of 0 specifies an infinite timeout.
	 * <p>Default is 30 seconds.
	 */
	public void setConnectTimeout(int connectTimeout) {
		Assert.isTrue(connectTimeout >= 0, "Timeout must be a non-negative value");
		this.httpClient.option(ChannelOption.CONNECT_TIMEOUT_MILLIS, connectTimeout);
	}

	/**
	 * Set the underlying connect timeout in milliseconds.
	 * A value of 0 specifies an infinite timeout.
	 * <p>Default is 5 seconds.
	 */
	public void setConnectTimeout(Duration connectTimeout) {
		Assert.notNull(connectTimeout, "ConnectTimeout must not be null");
		this.httpClient.option(ChannelOption.CONNECT_TIMEOUT_MILLIS, (int)connectTimeout.toMillis());
	}

	/**
	 * Set the underlying read timeout in milliseconds.
	 * <p>Default is 10 seconds.
	 */
	public void setReadTimeout(long readTimeout) {
		Assert.isTrue(readTimeout > 0, "Timeout must be a positive value");
		this.readTimeout = Duration.ofMillis(readTimeout);
	}

	/**
	 * Set the underlying read timeout as {@code Duration}.
	 * <p>Default is 10 seconds.
	 */
	public void setReadTimeout(Duration readTimeout) {
		Assert.notNull(readTimeout, "ReadTimeout must not be null");
		this.readTimeout = readTimeout;
	}


	/**
	 * Set the buffer capacity used when writing the request body.
	 * <p>Default is 1024.
	 */
	public void setRequestBufferCapacity(int requestBufferCapacity) {
		Assert.isTrue(requestBufferCapacity > 0, "RequestBufferCapacity must be larger than 0");
		this.requestBufferCapacity = requestBufferCapacity;
	}


	@Override
	public ClientHttpRequest createRequest(URI uri, HttpMethod httpMethod) throws IOException {
		return new ReactorNettyClientRequest(this.httpClient, uri, httpMethod, this.exchangeTimeout, this.readTimeout,
				this.requestBufferCapacity);
	}
}
