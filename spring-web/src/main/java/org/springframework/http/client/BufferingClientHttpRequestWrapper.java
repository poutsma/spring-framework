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

import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.StreamingHttpOutputMessage;
import org.springframework.lang.Nullable;

/**
 * Simple implementation of {@link ClientHttpRequest} that wraps another request.
 *
 * @author Arjen Poutsma
 * @since 3.1
 */
final class BufferingClientHttpRequestWrapper extends AbstractStreamingClientHttpRequest {

	private final ClientHttpRequest request;


	BufferingClientHttpRequestWrapper(ClientHttpRequest request) {
		this.request = request;
	}


	@Override
	public HttpMethod getMethod() {
		return this.request.getMethod();
	}

	@Override
	public URI getURI() {
		return this.request.getURI();
	}

	@Override
	protected ClientHttpResponse executeInternal(HttpHeaders headers, @Nullable Body body) throws IOException {
		this.request.getHeaders().putAll(headers);

		if (body != null) {
			if (this.request instanceof StreamingHttpOutputMessage streamingHttpOutputMessage) {
				streamingHttpOutputMessage.setBody(body);
			}
			else {
				body.writeTo(this.request.getBody());
			}
		}

		ClientHttpResponse response = this.request.execute();
		return new BufferingClientHttpResponseWrapper(response);
	}

}
