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

package org.springframework.web.client.function;

import java.io.IOException;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.stream.Stream;

import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Named;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.client.ClientHttpRequestFactory;
import org.springframework.http.client.ClientHttpRequestInterceptor;
import org.springframework.http.client.ClientHttpResponse;
import org.springframework.http.client.HttpComponentsClientHttpRequestFactory;
import org.springframework.http.client.JettyClientHttpRequestFactory;
import org.springframework.http.client.OkHttp3ClientHttpRequestFactory;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.util.CollectionUtils;
import org.springframework.web.testfixture.xml.Pojo;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.assertj.core.api.Assertions.assertThatIllegalStateException;
import static org.junit.jupiter.api.Named.named;

/**
 * Integration tests for {@link WebClient}.
 *
 * @author Brian Clozel
 * @author Rossen Stoyanchev
 * @author Denys Ivano
 * @author Sebastien Deleuze
 * @author Sam Brannen
 * @author Martin Tarjányi
 */
class WebClientIntegrationTests {

	@Retention(RetentionPolicy.RUNTIME)
	@Target(ElementType.METHOD)
	@ParameterizedTest(name = "[{index}] {0}")
	@MethodSource("clientHttpRequestFactories")
	@interface ParameterizedWebClientTest {
	}

	static Stream<Named<ClientHttpRequestFactory>> clientHttpRequestFactories() {
		return Stream.of(
			named("JDK", new SimpleClientHttpRequestFactory()),
			named("HttpComponents", new HttpComponentsClientHttpRequestFactory()),
			named("OkHttp", new OkHttp3ClientHttpRequestFactory()),
			named("Jetty", new JettyClientHttpRequestFactory())
		);
	}


	private MockWebServer server;

	private WebClient webClient;


	private void startServer(ClientHttpRequestFactory requestFactory) {
		this.server = new MockWebServer();
		this.webClient = WebClient
				.builder()
				.requestFactory(requestFactory)
				.baseUrl(this.server.url("/").toString())
				.build();
	}

	@AfterEach
	void shutdown() throws IOException {
		if (server != null) {
			this.server.shutdown();
		}
	}


	@ParameterizedWebClientTest
	void retrieve(ClientHttpRequestFactory requestFactory) {
		startServer(requestFactory);

		prepareResponse(response ->
				response.setHeader("Content-Type", "text/plain").setBody("Hello Spring!"));

		String result = this.webClient.get()
				.uri("/greeting")
				.header("X-Test-Header", "testvalue")
				.retrieve()
				.body(String.class);

		assertThat(result).isEqualTo("Hello Spring!");

		expectRequestCount(1);
		expectRequest(request -> {
			assertThat(request.getHeader("X-Test-Header")).isEqualTo("testvalue");
//	TODO		assertThat(request.getHeader(HttpHeaders.ACCEPT)).isEqualTo("*/*");
			assertThat(request.getPath()).isEqualTo("/greeting");
		});
	}

	@ParameterizedWebClientTest
	void retrieveJson(ClientHttpRequestFactory requestFactory) {
		startServer(requestFactory);

		prepareResponse(response -> response
				.setHeader("Content-Type", "application/json")
				.setBody("{\"bar\":\"barbar\",\"foo\":\"foofoo\"}"));

		Pojo result = this.webClient.get()
				.uri("/pojo")
				.accept(MediaType.APPLICATION_JSON)
				.retrieve()
				.body(Pojo.class);

		assertThat(result.getFoo()).isEqualTo("foofoo");
		assertThat(result.getBar()).isEqualTo("barbar");

		expectRequestCount(1);
		expectRequest(request -> {
			assertThat(request.getPath()).isEqualTo("/pojo");
			assertThat(request.getHeader(HttpHeaders.ACCEPT)).isEqualTo("application/json");
		});
	}

	@ParameterizedWebClientTest
	void retrieveJsonWithParameterizedTypeReference(ClientHttpRequestFactory requestFactory) {
		startServer(requestFactory);

		String content = "{\"containerValue\":{\"bar\":\"barbar\",\"foo\":\"foofoo\"}}";
		prepareResponse(response -> response
				.setHeader("Content-Type", "application/json").setBody(content));

		ValueContainer<Pojo> result = this.webClient.get()
				.uri("/json").accept(MediaType.APPLICATION_JSON)
				.retrieve()
				.body(new ParameterizedTypeReference<ValueContainer<Pojo>>() {});

		assertThat(result.getContainerValue()).isNotNull();
		Pojo pojo = result.getContainerValue();
		assertThat(pojo.getFoo()).isEqualTo("foofoo");
		assertThat(pojo.getBar()).isEqualTo("barbar");

		expectRequestCount(1);
		expectRequest(request -> {
			assertThat(request.getPath()).isEqualTo("/json");
			assertThat(request.getHeader(HttpHeaders.ACCEPT)).isEqualTo("application/json");
		});
	}

	@ParameterizedWebClientTest
	void retrieveJsonAsResponseEntity(ClientHttpRequestFactory requestFactory) {
		startServer(requestFactory);

		String content = "{\"bar\":\"barbar\",\"foo\":\"foofoo\"}";
		prepareResponse(response -> response
				.setHeader("Content-Type", "application/json").setBody(content));

		ResponseEntity<String> result = this.webClient.get()
				.uri("/json").accept(MediaType.APPLICATION_JSON)
				.retrieve()
				.toEntity(String.class);

		assertThat(result.getStatusCode()).isEqualTo(HttpStatus.OK);
		assertThat(result.getHeaders().getContentType()).isEqualTo(MediaType.APPLICATION_JSON);
		assertThat(result.getHeaders().getContentLength()).isEqualTo(31);
		assertThat(result.getBody()).isEqualTo(content);

		expectRequestCount(1);
		expectRequest(request -> {
			assertThat(request.getPath()).isEqualTo("/json");
			assertThat(request.getHeader(HttpHeaders.ACCEPT)).isEqualTo("application/json");
		});
	}

	@ParameterizedWebClientTest
	void retrieveJsonAsBodilessEntity(ClientHttpRequestFactory requestFactory) {
		startServer(requestFactory);

		prepareResponse(response -> response
				.setHeader("Content-Type", "application/json").setBody("{\"bar\":\"barbar\",\"foo\":\"foofoo\"}"));

		ResponseEntity<Void> result = this.webClient.get()
				.uri("/json").accept(MediaType.APPLICATION_JSON)
				.retrieve()
				.toBodilessEntity();

		assertThat(result.getStatusCode()).isEqualTo(HttpStatus.OK);
		assertThat(result.getHeaders().getContentType()).isEqualTo(MediaType.APPLICATION_JSON);
		assertThat(result.getHeaders().getContentLength()).isEqualTo(31);
		assertThat(result.getBody()).isNull();

		expectRequestCount(1);
		expectRequest(request -> {
			assertThat(request.getPath()).isEqualTo("/json");
			assertThat(request.getHeader(HttpHeaders.ACCEPT)).isEqualTo("application/json");
		});
	}

	@ParameterizedWebClientTest
	void retrieveJsonArray(ClientHttpRequestFactory requestFactory) {
		startServer(requestFactory);

		prepareResponse(response -> response
				.setHeader("Content-Type", "application/json")
				.setBody("[{\"bar\":\"bar1\",\"foo\":\"foo1\"},{\"bar\":\"bar2\",\"foo\":\"foo2\"}]"));

		List<Pojo> result = this.webClient.get()
				.uri("/pojos")
				.accept(MediaType.APPLICATION_JSON)
				.retrieve()
				.body(new ParameterizedTypeReference<>() {});

		assertThat(result).hasSize(2);
		assertThat(result.get(0).getFoo()).isEqualTo("foo1");
		assertThat(result.get(0).getBar()).isEqualTo("bar1");
		assertThat(result.get(1).getFoo()).isEqualTo("foo2");
		assertThat(result.get(1).getBar()).isEqualTo("bar2");

		expectRequestCount(1);
		expectRequest(request -> {
			assertThat(request.getPath()).isEqualTo("/pojos");
			assertThat(request.getHeader(HttpHeaders.ACCEPT)).isEqualTo("application/json");
		});
	}

	@ParameterizedWebClientTest
	void retrieveJsonArrayAsResponseEntityList(ClientHttpRequestFactory requestFactory) {
		startServer(requestFactory);

		String content = "[{\"bar\":\"bar1\",\"foo\":\"foo1\"}, {\"bar\":\"bar2\",\"foo\":\"foo2\"}]";
		prepareResponse(response -> response
				.setHeader("Content-Type", "application/json").setBody(content));

		ResponseEntity<List<Pojo>> result = this.webClient.get()
				.uri("/json").accept(MediaType.APPLICATION_JSON)
				.retrieve()
				.toEntity(new ParameterizedTypeReference<>() {});

		assertThat(result.getStatusCode()).isEqualTo(HttpStatus.OK);
		assertThat(result.getHeaders().getContentType()).isEqualTo(MediaType.APPLICATION_JSON);
		assertThat(result.getHeaders().getContentLength()).isEqualTo(58);
		assertThat(result.getBody()).hasSize(2);
		assertThat(result.getBody().get(0).getFoo()).isEqualTo("foo1");
		assertThat(result.getBody().get(0).getBar()).isEqualTo("bar1");
		assertThat(result.getBody().get(1).getFoo()).isEqualTo("foo2");
		assertThat(result.getBody().get(1).getBar()).isEqualTo("bar2");

		expectRequestCount(1);
		expectRequest(request -> {
			assertThat(request.getPath()).isEqualTo("/json");
			assertThat(request.getHeader(HttpHeaders.ACCEPT)).isEqualTo("application/json");
		});
	}

	@ParameterizedWebClientTest
	void retrieveJsonAsSerializedText(ClientHttpRequestFactory requestFactory) {
		startServer(requestFactory);

		String content = "{\"bar\":\"barbar\",\"foo\":\"foofoo\"}";
		prepareResponse(response -> response
				.setHeader("Content-Type", "application/json").setBody(content));

		String result = this.webClient.get()
				.uri("/json").accept(MediaType.APPLICATION_JSON)
				.retrieve()
				.body(String.class);

		assertThat(result).isEqualTo(content);

		expectRequestCount(1);
		expectRequest(request -> {
			assertThat(request.getPath()).isEqualTo("/json");
			assertThat(request.getHeader(HttpHeaders.ACCEPT)).isEqualTo("application/json");
		});
	}

	@ParameterizedWebClientTest
	@SuppressWarnings("rawtypes")
	void retrieveJsonNull(ClientHttpRequestFactory requestFactory) {
		startServer(requestFactory);

		prepareResponse(response -> response
				.setResponseCode(200)
				.setHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
				.setBody("null"));

		Map result = this.webClient.get()
				.uri("/null")
				.retrieve()
				.body(Map.class);

		assertThat(result).isNull();
	}

	@ParameterizedWebClientTest
	void retrieve404(ClientHttpRequestFactory requestFactory) {
		startServer(requestFactory);

		prepareResponse(response -> response.setResponseCode(404)
				.setHeader("Content-Type", "text/plain"));

		assertThatExceptionOfType(WebClientResponseException.class).isThrownBy(() ->
				this.webClient.get().uri("/greeting")
						.retrieve()
						.body(String.class)
		);

		expectRequestCount(1);
		expectRequest(request -> {
			assertThat(request.getPath()).isEqualTo("/greeting");
		});

	}

	@ParameterizedWebClientTest
	void retrieve404WithBody(ClientHttpRequestFactory requestFactory) {
		startServer(requestFactory);

		prepareResponse(response -> response.setResponseCode(404)
				.setHeader("Content-Type", "text/plain").setBody("Not Found"));

		assertThatExceptionOfType(WebClientResponseException.class).isThrownBy(() ->
				this.webClient.get()
						.uri("/greeting")
						.retrieve()
						.body(String.class)
		);

		expectRequestCount(1);
		expectRequest(request -> {
			assertThat(request.getPath()).isEqualTo("/greeting");
		});
	}

	@ParameterizedWebClientTest
	void retrieve500(ClientHttpRequestFactory requestFactory) {
		startServer(requestFactory);

		String errorMessage = "Internal Server error";
		prepareResponse(response -> response.setResponseCode(500)
				.setHeader("Content-Type", "text/plain").setBody(errorMessage));

		String path = "/greeting";
		try {
			this.webClient.get()
					.uri(path)
					.retrieve()
					.body(String.class);
		} catch (WebClientResponseException ex) {
			assertThat(ex.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
			assertThat(ex.getStatusText()).isEqualTo("Server Error");
			assertThat(ex.getHeaders().getContentType()).isEqualTo(MediaType.TEXT_PLAIN);
			assertThat(ex.getResponseBodyAsString()).isEqualTo(errorMessage);
		}

		expectRequestCount(1);
		expectRequest(request -> {
			assertThat(request.getPath()).isEqualTo(path);
		});
	}

	@ParameterizedWebClientTest
	void retrieve500AsEntity(ClientHttpRequestFactory requestFactory) {
		startServer(requestFactory);

		prepareResponse(response -> response.setResponseCode(500)
				.setHeader("Content-Type", "text/plain").setBody("Internal Server error"));

		assertThatExceptionOfType(WebClientResponseException.class).isThrownBy(() ->
				this.webClient.get()
						.uri("/").accept(MediaType.APPLICATION_JSON)
						.retrieve()
						.toEntity(String.class)
		);

		expectRequestCount(1);
		expectRequest(request -> {
			assertThat(request.getPath()).isEqualTo("/");
			assertThat(request.getHeader(HttpHeaders.ACCEPT)).isEqualTo("application/json");
		});
	}

	@ParameterizedWebClientTest
	void retrieve500AsBodilessEntity(ClientHttpRequestFactory requestFactory) {
		startServer(requestFactory);

		prepareResponse(response -> response.setResponseCode(500)
				.setHeader("Content-Type", "text/plain").setBody("Internal Server error"));

		assertThatExceptionOfType(WebClientResponseException.class).isThrownBy(() ->
				this.webClient.get()
						.uri("/").accept(MediaType.APPLICATION_JSON)
						.retrieve()
						.toBodilessEntity()
		);

		expectRequestCount(1);
		expectRequest(request -> {
			assertThat(request.getPath()).isEqualTo("/");
			assertThat(request.getHeader(HttpHeaders.ACCEPT)).isEqualTo("application/json");
		});
	}

	@ParameterizedWebClientTest
	void retrieve555UnknownStatus(ClientHttpRequestFactory requestFactory) {
		startServer(requestFactory);

		int errorStatus = 555;
		assertThat(HttpStatus.resolve(errorStatus)).isNull();
		String errorMessage = "Something went wrong";
		prepareResponse(response -> response.setResponseCode(errorStatus)
				.setHeader("Content-Type", "text/plain").setBody(errorMessage));

		try {
			this.webClient.get()
					.uri("/unknownPage")
					.retrieve()
					.body(String.class);

		} catch (WebClientResponseException ex) {
			assertThat(ex.getMessage()).isEqualTo("555 Server Error");
			assertThat(ex.getStatusText()).isEqualTo("Server Error");
			assertThat(ex.getHeaders().getContentType()).isEqualTo(MediaType.TEXT_PLAIN);
			assertThat(ex.getResponseBodyAsString()).isEqualTo(errorMessage);
		}

		expectRequestCount(1);
		expectRequest(request -> {
			assertThat(request.getPath()).isEqualTo("/unknownPage");
		});
	}

	@ParameterizedWebClientTest
	void postPojoAsJson(ClientHttpRequestFactory requestFactory) {
		startServer(requestFactory);

		prepareResponse(response -> response.setHeader("Content-Type", "application/json")
				.setBody("{\"bar\":\"BARBAR\",\"foo\":\"FOOFOO\"}"));

		Pojo result = this.webClient.post()
				.uri("/pojo/capitalize")
				.accept(MediaType.APPLICATION_JSON)
				.contentType(MediaType.APPLICATION_JSON)
				.body(new Pojo("foofoo", "barbar"))
				.retrieve()
				.body(Pojo.class);

		assertThat(result).isNotNull();
		assertThat(result.getFoo()).isEqualTo("FOOFOO");
		assertThat(result.getBar()).isEqualTo("BARBAR");

		expectRequestCount(1);
		expectRequest(request -> {
			assertThat(request.getPath()).isEqualTo("/pojo/capitalize");
			assertThat(request.getBody().readUtf8()).isEqualTo("{\"foo\":\"foofoo\",\"bar\":\"barbar\"}");
//			assertThat(request.getHeader(HttpHeaders.CONTENT_LENGTH)).isEqualTo("31");
			assertThat(request.getHeader(HttpHeaders.ACCEPT)).isEqualTo("application/json");
			assertThat(request.getHeader(HttpHeaders.CONTENT_TYPE)).isEqualTo("application/json");
		});
	}

	@ParameterizedWebClientTest
	void statusHandler(ClientHttpRequestFactory requestFactory) {
		startServer(requestFactory);

		prepareResponse(response -> response.setResponseCode(500)
				.setHeader("Content-Type", "text/plain").setBody("Internal Server error"));

		assertThatExceptionOfType(MyException.class).isThrownBy(() ->
				this.webClient.get()
						.uri("/greeting")
						.retrieve()
						.onStatus(HttpStatusCode::is5xxServerError, response -> Optional.of(new MyException("500 error!")))
						.body(String.class)
		);

		expectRequestCount(1);
		expectRequest(request -> {
			assertThat(request.getPath()).isEqualTo("/greeting");
		});
	}

	@ParameterizedWebClientTest
	void statusHandlerParameterizedTypeReference(ClientHttpRequestFactory requestFactory) {
		startServer(requestFactory);

		prepareResponse(response -> response.setResponseCode(500)
				.setHeader("Content-Type", "text/plain").setBody("Internal Server error"));

		assertThatExceptionOfType(MyException.class).isThrownBy(() ->
				this.webClient.get()
						.uri("/greeting")
						.retrieve()
						.onStatus(HttpStatusCode::is5xxServerError, response -> Optional.of(new MyException("500 error!")))
						.body(new ParameterizedTypeReference<String>() {
						})
		);

		expectRequestCount(1);
		expectRequest(request -> {
			assertThat(request.getPath()).isEqualTo("/greeting");
		});
	}

	@ParameterizedWebClientTest
	void statusHandlerSuppressedErrorSignal(ClientHttpRequestFactory requestFactory) {
		startServer(requestFactory);

		prepareResponse(response -> response.setResponseCode(500)
				.setHeader("Content-Type", "text/plain").setBody("Internal Server error"));

		String result = this.webClient.get()
				.uri("/greeting")
				.retrieve()
				.onStatus(HttpStatusCode::is5xxServerError, response -> Optional.empty())
				.body(String.class);

		assertThat(result).isEqualTo("Internal Server error");

		expectRequestCount(1);
		expectRequest(request -> {
			assertThat(request.getPath()).isEqualTo("/greeting");
		});
	}

	@ParameterizedWebClientTest
	void statusHandlerSuppressedErrorSignalWithEntity(ClientHttpRequestFactory requestFactory) {
		startServer(requestFactory);

		String content = "Internal Server error";
		prepareResponse(response -> response.setResponseCode(500)
				.setHeader("Content-Type", "text/plain").setBody(content));

		ResponseEntity<String> result = this.webClient.get()
				.uri("/").accept(MediaType.APPLICATION_JSON)
				.retrieve()
				.onStatus(HttpStatusCode::is5xxServerError, response -> Optional.empty())// use normal response
				.toEntity(String.class);


		assertThat(result.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
		assertThat(result.getBody()).isEqualTo(content);

		expectRequestCount(1);
		expectRequest(request -> {
			assertThat(request.getPath()).isEqualTo("/");
			assertThat(request.getHeader(HttpHeaders.ACCEPT)).isEqualTo("application/json");
		});
	}

	@ParameterizedWebClientTest
	void exchangeForPlainText(ClientHttpRequestFactory requestFactory) {
		startServer(requestFactory);

		prepareResponse(response -> response.setBody("Hello Spring!"));

		String result = this.webClient.get()
				.uri("/greeting")
				.header("X-Test-Header", "testvalue")
				.exchange(clientResponse -> new String(WebClientUtils.getBody(clientResponse), StandardCharsets.UTF_8));

		assertThat(result).isEqualTo("Hello Spring!");

		expectRequestCount(1);
		expectRequest(request -> {
			assertThat(request.getHeader("X-Test-Header")).isEqualTo("testvalue");
			assertThat(request.getPath()).isEqualTo("/greeting");
		});
	}

	@ParameterizedWebClientTest
	void exchangeFor404(ClientHttpRequestFactory requestFactory) {
		startServer(requestFactory);

		prepareResponse(response -> response.setResponseCode(404)
				.setHeader("Content-Type", "text/plain").setBody("Not Found"));

		String result = this.webClient.get()
				.uri("/greeting")
				.exchange(clientResponse -> new String(WebClientUtils.getBody(clientResponse), StandardCharsets.UTF_8));

		assertThat(result).isEqualTo("Not Found");

		expectRequestCount(1);
		expectRequest(request -> {
			assertThat(request.getPath()).isEqualTo("/greeting");
		});
	}

	@ParameterizedWebClientTest
	void requestInitializer(ClientHttpRequestFactory requestFactory) {
		startServer(requestFactory);

		prepareResponse(response -> response.setHeader("Content-Type", "text/plain")
				.setBody("Hello Spring!"));

		WebClient initializedClient = this.webClient.mutate()
				.requestInitializer(request -> request.getHeaders().add("foo", "bar"))
				.build();

		String result = initializedClient.get()
				.uri("/greeting")
				.retrieve()
				.body(String.class);

		assertThat(result).isEqualTo("Hello Spring!");

		expectRequestCount(1);
		expectRequest(request -> assertThat(request.getHeader("foo")).isEqualTo("bar"));
	}

	@ParameterizedWebClientTest
	void requestInterceptor(ClientHttpRequestFactory requestFactory) {
		startServer(requestFactory);

		prepareResponse(response -> response.setHeader("Content-Type", "text/plain")
				.setBody("Hello Spring!"));


		WebClient interceptedClient = this.webClient.mutate()
				.requestInterceptor((request, body, execution) -> {
					request.getHeaders().add("foo", "bar");
					return execution.execute(request, body);
				})
				.build();

		String result = interceptedClient.get()
				.uri("/greeting")
				.retrieve()
				.body(String.class);

		assertThat(result).isEqualTo("Hello Spring!");

		expectRequestCount(1);
		expectRequest(request -> assertThat(request.getHeader("foo")).isEqualTo("bar"));
	}


	@ParameterizedWebClientTest
	void filterForErrorHandling(ClientHttpRequestFactory requestFactory) {
		startServer(requestFactory);

		ClientHttpRequestInterceptor interceptor = (request, body, execution) -> {
			ClientHttpResponse response = execution.execute(request, body);
			List<String> headerValues = response.getHeaders().get("Foo");
			if (CollectionUtils.isEmpty(headerValues)) {
				throw new MyException("Response does not contain Foo header");
			} else {
				return response;
			}
		};

		WebClient interceptedClient = this.webClient.mutate().requestInterceptor(interceptor).build();

		// header not present
		prepareResponse(response -> response
				.setHeader("Content-Type", "text/plain").setBody("Hello Spring!"));

		assertThatExceptionOfType(MyException.class).isThrownBy(() ->
				interceptedClient.get()
						.uri("/greeting")
						.retrieve()
						.body(String.class)
		);

		// header present

		prepareResponse(response -> response.setHeader("Content-Type", "text/plain")
				.setHeader("Foo", "Bar")
				.setBody("Hello Spring!"));

		String result = interceptedClient.get()
				.uri("/greeting")
				.retrieve().body(String.class);

		assertThat(result).isEqualTo("Hello Spring!");

		expectRequestCount(2);
	}


	@ParameterizedWebClientTest
	void invalidDomain(ClientHttpRequestFactory requestFactory) {
		startServer(requestFactory);

		String url = "http://example.invalid";
		assertThatExceptionOfType(WebClientRequestException.class).isThrownBy(() ->
			this.webClient.get().uri(url).retrieve().toBodilessEntity()
		);

	}

	@ParameterizedWebClientTest
	void sseEventString(ClientHttpRequestFactory requestFactory) {
		startServer(requestFactory);

		prepareResponse(response -> response
				.setHeader("Content-Type", MediaType.TEXT_EVENT_STREAM_VALUE)
				.setBody("""
						id: id1
						event: event1
						retry: 42
						: comment1
						data: data1

						id: id2
						event: event2
						retry: 43
						: comment2
						data: data2

						"""));

		List<ServerSentEvent<String>> result = new ArrayList<>();
		this.webClient.get()
				.uri("/sse")
				.retrieve()
				.sseEvents(result::add, String.class);

		assertThat(result).hasSize(2);
		assertThat(result.get(0).id()).isEqualTo("id1");
		assertThat(result.get(0).event()).isEqualTo("event1");
		assertThat(result.get(0).retry()).isEqualTo(Duration.ofMillis(42));
		assertThat(result.get(0).comment()).isEqualTo("comment1");
		assertThat(result.get(0).data()).isEqualTo("data1");
		assertThat(result.get(1).id()).isEqualTo("id2");
		assertThat(result.get(1).event()).isEqualTo("event2");
		assertThat(result.get(1).retry()).isEqualTo(Duration.ofMillis(43));
		assertThat(result.get(1).comment()).isEqualTo("comment2");
		assertThat(result.get(1).data()).isEqualTo("data2");

		expectRequestCount(1);
		expectRequest(request -> {
			assertThat(request.getPath()).isEqualTo("/sse");
		});
	}

	@ParameterizedWebClientTest
	void sseEventJson(ClientHttpRequestFactory requestFactory) {
		startServer(requestFactory);

		prepareResponse(response -> response
				.setHeader("Content-Type", MediaType.TEXT_EVENT_STREAM_VALUE)
				.setBody("""
						id: id1
						event: event1
						retry: 42
						: comment1
						data: {"bar":"bar1","foo":"foo1"}

						id: id2
						event: event2
						retry: 43
						: comment2
						data: {"bar":"bar2","foo":"foo2"}

						"""));


		List<ServerSentEvent<Pojo>> result = new ArrayList<>();
		this.webClient.get()
				.uri("/sse")
				.retrieve()
				.sseEvents(result::add, Pojo.class);

		assertThat(result).hasSize(2);
		assertThat(result.get(0).id()).isEqualTo("id1");
		assertThat(result.get(0).event()).isEqualTo("event1");
		assertThat(result.get(0).retry()).isEqualTo(Duration.ofMillis(42));
		assertThat(result.get(0).comment()).isEqualTo("comment1");
		assertThat(result.get(0).data().getFoo()).isEqualTo("foo1");
		assertThat(result.get(0).data().getBar()).isEqualTo("bar1");
		assertThat(result.get(1).id()).isEqualTo("id2");
		assertThat(result.get(1).event()).isEqualTo("event2");
		assertThat(result.get(1).retry()).isEqualTo(Duration.ofMillis(43));
		assertThat(result.get(1).comment()).isEqualTo("comment2");
		assertThat(result.get(1).data().getFoo()).isEqualTo("foo2");
		assertThat(result.get(1).data().getBar()).isEqualTo("bar2");

		expectRequestCount(1);
		expectRequest(request -> {
			assertThat(request.getPath()).isEqualTo("/sse");
		});
	}

	@ParameterizedWebClientTest
	void sseDataString(ClientHttpRequestFactory requestFactory) throws InterruptedException {
		startServer(requestFactory);

		prepareResponse(response -> response
				.setHeader("Content-Type", MediaType.TEXT_EVENT_STREAM_VALUE)
				.setBody("""
						data: foo

						data: bar

						"""));

		List<String> result = new ArrayList<>();
		this.webClient.get()
				.uri("/sse")
				.retrieve()
				.sseData(result::add, String.class);

		assertThat(result).containsExactly("foo", "bar");

		expectRequestCount(1);
		expectRequest(request -> {
			assertThat(request.getPath()).isEqualTo("/sse");
		});
	}

	@ParameterizedWebClientTest
	void sseDataJson(ClientHttpRequestFactory requestFactory) throws InterruptedException {
		startServer(requestFactory);

		prepareResponse(response -> response
				.setHeader("Content-Type", MediaType.TEXT_EVENT_STREAM_VALUE)
				.setBody("""
						data: {"bar":"bar1","foo":"foo1"}

						data: {"bar":"bar2","foo":"foo2"}

						"""));

		List<Pojo> result = new ArrayList<>();
		this.webClient.get()
				.uri("/sse")
				.retrieve()
				.sseData(result::add, Pojo.class);

		assertThat(result).hasSize(2);
		assertThat(result.get(0).getFoo()).isEqualTo("foo1");
		assertThat(result.get(0).getBar()).isEqualTo("bar1");
		assertThat(result.get(1).getFoo()).isEqualTo("foo2");
		assertThat(result.get(1).getBar()).isEqualTo("bar2");

		expectRequestCount(1);
		expectRequest(request -> {
			assertThat(request.getPath()).isEqualTo("/sse");
		});
	}

	@ParameterizedWebClientTest
	void sseWrongContentType(ClientHttpRequestFactory requestFactory) {
		startServer(requestFactory);

		prepareResponse(response -> response
				.setHeader("Content-Type", MediaType.TEXT_PLAIN_VALUE)
				.setBody("data: foo\n\n"));

		assertThatIllegalStateException().isThrownBy(() -> this.webClient.get()
				.uri("/sse")
				.retrieve()
				.sseData(t -> {}, String.class));

		expectRequestCount(1);
		expectRequest(request -> {
			assertThat(request.getPath()).isEqualTo("/sse");
		});
	}


	private void prepareResponse(Consumer<MockResponse> consumer) {
		MockResponse response = new MockResponse();
		consumer.accept(response);
		this.server.enqueue(response);
	}

	private void expectRequest(Consumer<RecordedRequest> consumer) {
		try {
			consumer.accept(this.server.takeRequest());
		}
		catch (InterruptedException ex) {
			throw new IllegalStateException(ex);
		}
	}

	private void expectRequestCount(int count) {
		assertThat(this.server.getRequestCount()).isEqualTo(count);
	}


	@SuppressWarnings("serial")
	private static class MyException extends RuntimeException {

		MyException(String message) {
			super(message);
		}
	}


	static class ValueContainer<T> {

		private T containerValue;


		public T getContainerValue() {
			return containerValue;
		}

		public void setContainerValue(T containerValue) {
			this.containerValue = containerValue;
		}
	}

}
