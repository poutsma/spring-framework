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

import static org.assertj.core.api.Assertions.assertThat;

/**
 * @author Arjen Poutsma
 */
class UriComponentsBuilderParserTests {

	@Test
	void sanitizeInput() {
		String result = new String(UriComponentsBuilderParser.sanitizeInput("\0 \t\nfoo \t\nbar \t\n\0"));
		assertThat(result).isEqualTo("foo bar");
	}

	@Test
	void parse() {
		UriComponentsBuilderParser parser = new UriComponentsBuilderParser("http://[1abc:2abc:3abc::5ABC:6abc%eth0]:8080/resource" , System.out::println);
//		UriComponentsBuilderParser parser = new UriComponentsBuilderParser("http://[1080::8:800:200c:417a]/index.html" , System.out::println);
//		UriComponentsBuilderParser parser = new UriComponentsBuilderParser("https://192.168/foo/bar" , System.out::println);
//		UriComponentsBuilderParser parser = new UriComponentsBuilderParser("https://arjen:foobar@java.sun.com:80" +
//						"/javase/6/docs/api/java/util/BitSet.html?foo=bar#and(java.util.BitSet)", System.out::println);
//		UriComponentsBuilderParser parser = new UriComponentsBuilderParser("mailto:java-net@java.sun.com#baz", System.out::println);
		UriComponents result = parser.parse().build();
		System.out.printf("scheme:   '%s'%n", result.getScheme());
		System.out.printf("host:     '%s'%n", result.getHost());
		System.out.printf("port:     '%d'%n", result.getPort());
		System.out.printf("path:     '%s'%n", result.getPath());
		System.out.printf("query:    '%s'%n", result.getQuery());
		System.out.printf("fragment: '%s'%n", result.getFragment());
	}
}
