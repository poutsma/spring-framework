/*
 * Copyright 2002-2017 the original author or authors.
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

package org.springframework.http;

import java.net.URI;
import java.util.Arrays;

import org.springframework.lang.Nullable;
import org.springframework.util.Assert;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;

/**
 * Subclass of {@link RequestEntity} that exposes multipart-specific functionality.
 * Created via {@link #builder(URI)}.
 *
 * @author Arjen Poutsma
 * @since 5.0.2
 */
public class MultipartEntity extends RequestEntity<MultiValueMap<String, Object>> {

	private MultipartEntity(@Nullable MultiValueMap<String, Object> body,
			@Nullable MultiValueMap<String, String> headers,
			@Nullable HttpMethod method, URI url) {
		super(body, headers, method, url);
	}

	// Static builder methods

	/**
	 * Create a builder with the given method and url.
	 * @param url the URL
	 * @return the created builder
	 */
	public static FirstStepBuilder builder(URI url) {
		Assert.notNull(url, "'url' must not be null");
		return new DefaultBuilder(url);
	}


	/**
	 * Defines methods for adding parts to this builder.
	 */
	public interface FirstStepBuilder {

		/**
		 * Adds the given part to the entity that will be built by this builder.
		 * @param name the name of the part
		 * @param part the part to be added
		 * @return this builder
		 */
		Builder part(String name, Object part);

		/**
		 * Adds the given parts to the entity that will be built by this builder.
		 * @param parts the part to be added
		 * @return this builder
		 */
		Builder parts(MultiValueMap<String, Object> parts);
	}


	/**
	 * Defines a builder for {@code MultipartEntity}.
	 */
	public interface Builder extends FirstStepBuilder {

		/**
		 * Sets the HTTP method. Defaults to {@link HttpMethod#POST}.
		 * @param method the
		 * @return this builder
		 */
		Builder method(HttpMethod method);

		/**
		 * Add the given header values under the given name.
		 * @param headerName  the header name
		 * @param headerValues the header value(s)
		 * @return this builder
		 * @see HttpHeaders#add(String, String)
		 */
		Builder header(String headerName, String... headerValues);

		/**
		 * Builds the multipart entity.
		 * @return the multipart entity
		 */
		MultipartEntity build();
	}


	private static class DefaultBuilder implements Builder {

		private final HttpHeaders headers = new HttpHeaders();

		private final MultiValueMap<String, Object> parts = new LinkedMultiValueMap<>();

		private HttpMethod method = HttpMethod.POST;

		private URI url;

		public DefaultBuilder(URI url) {
			this.url = url;
			this.headers.setContentType(MediaType.MULTIPART_FORM_DATA);
		}

		@Override
		public Builder part(String name, Object part) {
			Assert.hasLength(name, "'name' must not be empty");
			Assert.notNull(part, "'part' must not be null");

			this.parts.add(name, part);
			return this;
		}

		@Override
		public Builder parts(MultiValueMap<String, Object> parts) {
			Assert.notNull(parts, "'parts' must not be null");

			this.parts.addAll(parts);
			return this;
		}

		@Override
		public Builder method(HttpMethod method) {
			Assert.notNull(method, "'method' must not be null");
			this.method = method;
			return this;
		}

		@Override
		public Builder header(String headerName, String... headerValues) {
			this.headers.addAll(headerName, Arrays.asList(headerValues));
			return this;
		}

		@Override
		public MultipartEntity build() {
			return new MultipartEntity(this.parts, this.headers, this.method, this.url );
		}

	}

}
