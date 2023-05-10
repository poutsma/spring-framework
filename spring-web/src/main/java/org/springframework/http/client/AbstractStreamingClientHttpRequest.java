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
import java.io.OutputStream;

import org.springframework.http.HttpHeaders;
import org.springframework.http.StreamingHttpOutputMessage;
import org.springframework.lang.Nullable;
import org.springframework.util.Assert;
import org.springframework.util.FastByteArrayOutputStream;

/**
 * @author Arjen Poutsma
 * @since 6.1
 */
public abstract class AbstractStreamingClientHttpRequest implements ClientHttpRequest, StreamingHttpOutputMessage {

	private final HttpHeaders headers = new HttpHeaders();

	private boolean executed = false;

	@Nullable
	private Body body;

	@Nullable
	private FastByteArrayOutputStream bodyStream;

	@Nullable
	private HttpHeaders readOnlyHeaders;


	@Override
	public final HttpHeaders getHeaders() {
		if (this.readOnlyHeaders != null) {
			return this.readOnlyHeaders;
		}
		else if (this.executed) {
			this.readOnlyHeaders = HttpHeaders.readOnlyHttpHeaders(this.headers);
			return this.readOnlyHeaders;
		}
		else {
			return this.headers;
		}
	}

	@Override
	public final OutputStream getBody() throws IOException {
		assertNotExecuted();
		Assert.state(this.body == null, "Invoke either getBody or setBody; not both");

		if (this.bodyStream == null) {
			this.bodyStream = new FastByteArrayOutputStream(1024);
		}
		return this.bodyStream;
	}

	@Override
	public final void setBody(Body body) {
		Assert.notNull(body, "Body must not be null");
		assertNotExecuted();
		Assert.state(this.bodyStream == null, "Invoke either getBody or setBody; not both");

		this.body = body;
	}

	@Override
	public final ClientHttpResponse execute() throws IOException {
		assertNotExecuted();
		if (this.body == null && this.bodyStream != null) {
			this.body = outputStream -> this.bodyStream.writeTo(outputStream);
		}
		ClientHttpResponse result = executeInternal(this.headers, this.body);
		this.executed = true;
		return result;
	}

	/**
	 * Assert that this request has not been {@linkplain #execute() executed} yet.
	 * @throws IllegalStateException if this request has been executed
	 */
	protected void assertNotExecuted() {
		Assert.state(!this.executed, "ClientHttpRequest already executed");
	}

	/**
	 * Abstract template method that writes the given headers and content to the HTTP request.
	 * @param headers the HTTP headers
	 * @param body the HTTP body, may be {@code null} if no body was {@linkplain #setBody(Body) set}
	 * @return the response object for the executed request
	 * @since 6.1
	 */
	protected abstract ClientHttpResponse executeInternal(HttpHeaders headers, @Nullable Body body) throws IOException;

}
