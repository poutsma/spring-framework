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

import org.junit.Test;

import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;

import static org.junit.Assert.*;

/**
 * @author Arjen Poutsma
 */
public class MultipartEntityTests {

	@Test
	public void builder() throws Exception {
		URI url = URI.create("http://example.com");
		Resource logo = new ClassPathResource("/org/springframework/http/converter/logo.jpg");

		MultipartEntity entity = MultipartEntity.builder(url)
				.part("key", "value")
				.part("logo", logo)
				.build();

		assertEquals(url, entity.getUrl());
		assertEquals(HttpMethod.POST, entity.getMethod());
		assertEquals(MediaType.MULTIPART_FORM_DATA, entity.getHeaders().getContentType());
		assertNotNull(entity.getBody());
		MultiValueMap<String, Object> body = entity.getBody();
		assertEquals(2, body.size());
		assertEquals("value", body.getFirst("key"));
		assertEquals(logo, body.getFirst("logo"));
	}

	@Test
	public void method() throws Exception {
		URI url = URI.create("http://example.com");

		MultipartEntity entity = MultipartEntity.builder(url)
				.part("foo", "bar")
				.method(HttpMethod.PUT)
				.build();

		assertEquals(HttpMethod.PUT, entity.getMethod());
	}

	@Test
	public void parts() throws Exception {
		URI url = URI.create("http://example.com");
		MultiValueMap<String, Object> parts = new LinkedMultiValueMap<>();
		parts.add("foo", "bar");

		MultipartEntity entity = MultipartEntity.builder(url)
				.parts(parts)
				.build();

		assertNotNull(entity.getBody());
		MultiValueMap<String, Object> body = entity.getBody();
		assertEquals(1, body.size());
		assertEquals("bar", body.getFirst("foo"));
	}

}