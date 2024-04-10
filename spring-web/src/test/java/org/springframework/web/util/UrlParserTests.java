/*
 * Copyright 2002-2024 the original author or authors.
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

package org.springframework.web.util;

import org.junit.jupiter.api.Test;

import org.springframework.lang.Nullable;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * @author Arjen Poutsma
 */
class UrlParserTests {

	private static final UrlParser.UrlRecord EMPTY_URL_RECORD = new UrlParser.UrlRecord();

	@Test
	void parse() {
//		testParse("https://example.com", "https", "example.com", null, "", null, null);
//		testParse("https://example.com/", "https", "example.com", null, "/", null, null);
//		testParse("https://example.com/foo", "https", "example.com", null, "/foo", null, null);
//		testParse("https://example.com/foo/", "https", "example.com", null, "/foo/", null, null);
//		testParse("https://example.com:81/foo", "https", "example.com", "81", "/foo", null, null);
//		testParse("/foo", "", null, null, "/foo", null, null);
//		testParse("/foo/", "", null, null, "/foo/", null, null);
		testParse("/foo/../bar", "", null, null, "/bar", null, null);
	}

	private void testParse(String input, String scheme, @Nullable String host, @Nullable String port, String path, @Nullable String query, @Nullable String fragment) {
		UrlParser.UrlRecord result = UrlParser.parse(input, new UrlParser.UrlRecord(), null, null);
		assertThat(result.scheme()).isEqualTo(scheme);
		if (host != null) {
			assertThat(result.host()).isNotNull();
			assertThat(result.host().toString()).isEqualTo(host);
		} else {
			assertThat(result.host()).isNull();
		}
		assertThat(result.port()).isEqualTo(port);
		assertThat(result.path().toString()).isEqualTo(path);
		assertThat(result.query()).isEqualTo(query);
		assertThat(result.fragment()).isEqualTo(fragment);
	}
}
