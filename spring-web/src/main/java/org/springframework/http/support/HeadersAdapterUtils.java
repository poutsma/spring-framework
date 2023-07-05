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

package org.springframework.http.support;

import org.springframework.util.Assert;
import org.springframework.util.MultiValueMap;

/**
 * Utility class that adapts various native header types to
 * {@code MultiValueMap<String, String>}.
 *
 * @author Arjen Poutsma
 * @since 6.1
 */
public abstract class HeadersAdapterUtils {

	/**
	 * Adapt the given HTTP components {@code HttpMessage} to
	 * {@code MultiValueMap<String, String>}.
	 */
	public static MultiValueMap<String, String> httpComponents(org.apache.hc.core5.http.HttpMessage message) {
		Assert.notNull(message, "Message must not be null");
		return new HttpComponentsHeadersAdapter(message);
	}

	/**
	 * Adapt the given Jetty {@code HttpFields} to
	 * {@code MultiValueMap<String, String>}.
	 */
	public static MultiValueMap<String, String> jetty(org.eclipse.jetty.http.HttpFields headers) {
		Assert.notNull(headers, "Headers must not be null");
		return new JettyHeadersAdapter(headers);
	}

	/**
	 * Adapt the given Netty 4 {@code HttpHeaders} to
	 * {@code MultiValueMap<String, String>}.
	 */
	public static MultiValueMap<String, String> netty4(io.netty.handler.codec.http.HttpHeaders headers) {
		Assert.notNull(headers, "Headers must not be null");
		return new NettyHeadersAdapter(headers);
	}

	/**
	 * Adapt the given Netty 5 {@code HttpHeaders} to
	 * {@code MultiValueMap<String, String>}.
	 */
	public static MultiValueMap<String, String> netty5(io.netty5.handler.codec.http.headers.HttpHeaders headers) {
		Assert.notNull(headers, "Headers must not be null");
		return new Netty5HeadersAdapter(headers);
	}

	/**
	 * Adapt the given tomcat {@code MimeHeaders} to
	 * {@code MultiValueMap<String, String>}.
	 */
	public static MultiValueMap<String, String> tomcat(org.apache.tomcat.util.http.MimeHeaders headers) {
		Assert.notNull(headers, "Headers must not be null");
		return new TomcatHeadersAdapter(headers);
	}

	/**
	 * Adapt the given Undertow {@code HeaderMap} to
	 * {@code MultiValueMap<String, String>}.
	 */
	public static MultiValueMap<String, String> undertow(io.undertow.util.HeaderMap headers) {
		Assert.notNull(headers, "Headers must not be null");
		return new UndertowHeadersAdapter(headers);
	}

}
