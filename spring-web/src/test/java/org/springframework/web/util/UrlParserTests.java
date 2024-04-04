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

import java.nio.charset.StandardCharsets;

import org.junit.jupiter.api.Test;

/**
 * @author Arjen Poutsma
 */
class UrlParserTests {

	private static final UrlParser.UrlRecord EMPTY_URL_RECORD = new UrlParser.UrlRecord();

	@Test
	void parse() {
		//file://localhost/etc/fstab
		//file:///etc/fstab
//		UrlParser parser = new UrlParser("file:///c:\\WINDOWS\\clock.avi" , System.out::println);
//		UrlParser.UrlRecord result = UrlParser.parse("http://[1abc:2abc:3abc::5ABC:6abc]:8080/resource" , System.out::println);
//		UrlParser.UrlRecord result = UrlParser.parse("http://[1080::8:800:200c:417a]/index.html" , System.out::println);
//		UrlParser.UrlRecord result = UrlParser.parse("https://192.168.1.1/foo/bar", System.out::println);
//		UrlParser.UrlRecord result = UrlParser.parse("https://01.102/foo/bar", System.out::println);
//		UrlParser.UrlRecord result = UrlParser.parse("https://arjen:foobar@java.sun.com:80" +
//						"/javase/6/docs/api/java/util/BitSet.html?foo=bar#and(java.util.BitSet)", System.out::println);
//		UrlParser.UrlRecord result = UrlParser.parse("mailto:java-net@java.sun.com#baz", System.out::println);
//		UrlParser.UrlRecord result = UrlParser.parse("docs/guide/collections/designfaq.html#28", EMPTY_URL_RECORD, StandardCharsets.UTF_8, System.out::println);
		UrlParser.UrlRecord result = UrlParser.parse("http://example.com", EMPTY_URL_RECORD, StandardCharsets.UTF_8, System.out::println);
		System.out.printf("scheme:   '%s'%n", result.scheme());
		System.out.printf("host:     '%s'%n", result.host());
		System.out.printf("port:     '%d'%n", result.port());
		System.out.printf("path:     '%s'%n", result.path());
		System.out.printf("query:    '%s'%n", result.query());
		System.out.printf("fragment: '%s'%n", result.fragment());
/*
		URI uri = URI.create("mailto:java-net@java.sun.com#baz");
		System.out.println("URI");
		System.out.printf("scheme:   '%s'%n", uri.getScheme());
		System.out.printf("ssp:      '%s'%n", uri.getSchemeSpecificPart());
		System.out.printf("host:     '%s'%n", uri.getHost());
		System.out.printf("port:     '%d'%n", uri.getPort());
		System.out.printf("path:     '%s'%n", uri.getPath());
		System.out.printf("query:    '%s'%n", uri.getQuery());
		System.out.printf("fragment: '%s'%n", uri.getFragment());
*/
	}
}
