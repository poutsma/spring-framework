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

package org.springframework.mock.http;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;

import org.springframework.http.HttpHeaders;
import org.springframework.http.StreamingHttpOutputMessage;
import org.springframework.lang.Nullable;

/**
 * Mock implementation of {@link StreamingHttpOutputMessage}.
 *
 * @author Arjen Poutsma
 * @since 6.1
 */
public class MockStreamingHttpOutputMessage implements StreamingHttpOutputMessage {

	private final HttpHeaders headers = new HttpHeaders();

	@Nullable
	private Body body;

	@Nullable
	private byte[] bytes;


	@Override
	public HttpHeaders getHeaders() {
		return this.headers;
	}

	@Override
	public OutputStream getBody() throws IOException {
		throw new UnsupportedOperationException();
	}

	@Override
	public void setBody(Body body) {
		this.body = body;
	}

	/**
	 * Return the body content as a byte array.
	 */
	public byte[] getBodyAsBytes() {
		if (this.bytes != null) {
			return this.bytes;
		}
		else if (this.body != null) {
			ByteArrayOutputStream os = new ByteArrayOutputStream(1024);
			try {
				this.body.writeTo(os);
				this.bytes = os.toByteArray();
			}
			catch (IOException ex) {
				this.bytes = new byte[0];
			}
		}
		if (this.bytes != null) {
			return this.bytes;
		}
		else {
			return new byte[0];
		}
	}

	/**
	 * Return the body content interpreted as a UTF-8 string.
	 */
	public String getBodyAsString() {
		return getBodyAsString(StandardCharsets.UTF_8);
	}

	/**
	 * Return the body content interpreted as a string using the supplied character set.
	 * @param charset the charset to use to turn the body content into a String
	 */
	public String getBodyAsString(Charset charset) {
		byte[] bytes = getBodyAsBytes();
		return new String(bytes, charset);
	}

}
