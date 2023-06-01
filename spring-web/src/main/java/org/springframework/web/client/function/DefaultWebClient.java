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
import java.io.UncheckedIOException;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.net.URI;
import java.nio.charset.Charset;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;

import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.StreamingHttpOutputMessage;
import org.springframework.http.client.ClientHttpRequest;
import org.springframework.http.client.ClientHttpRequestFactory;
import org.springframework.http.client.ClientHttpRequestInitializer;
import org.springframework.http.client.ClientHttpRequestInterceptor;
import org.springframework.http.client.ClientHttpResponse;
import org.springframework.http.client.InterceptingClientHttpRequestFactory;
import org.springframework.http.converter.GenericHttpMessageConverter;
import org.springframework.http.converter.HttpMessageConverter;
import org.springframework.lang.Nullable;
import org.springframework.util.Assert;
import org.springframework.util.CollectionUtils;
import org.springframework.web.client.UnknownContentTypeException;
import org.springframework.web.util.UriBuilder;
import org.springframework.web.util.UriBuilderFactory;

/**
 * @author Arjen Poutsma
 * @since 6.1
 */
final class DefaultWebClient implements WebClient {

	private static final String URI_TEMPLATE_ATTRIBUTE = WebClient.class.getName() + ".uriTemplate";

	private final ClientHttpRequestFactory clientRequestFactory;

	@Nullable
	private volatile ClientHttpRequestFactory interceptingRequestFactory;

	@Nullable
	private final List<ClientHttpRequestInitializer> initializers;

	@Nullable
	private final List<ClientHttpRequestInterceptor> interceptors;

	private final UriBuilderFactory uriBuilderFactory;

	@Nullable
	private final HttpHeaders defaultHeaders;

	private final List<DefaultResponseSpec.StatusHandler> defaultStatusHandlers;

	private final DefaultWebClientBuilder builder;

	private final List<HttpMessageConverter<?>> messageConverters;


	DefaultWebClient(ClientHttpRequestFactory clientRequestFactory,
					 @Nullable List<ClientHttpRequestInterceptor> interceptors,
					 @Nullable List<ClientHttpRequestInitializer> initializers,
					 UriBuilderFactory uriBuilderFactory,
					 @Nullable HttpHeaders defaultHeaders,
					 @Nullable Map<Predicate<HttpStatusCode>, Function<ClientHttpResponse, Optional<? extends RuntimeException>>> statusHandlerMap,
					 List<HttpMessageConverter<?>> messageConverters,
					 DefaultWebClientBuilder builder) {

		this.clientRequestFactory = clientRequestFactory;
		this.initializers = initializers;
		this.interceptors = interceptors;
		this.uriBuilderFactory = uriBuilderFactory;
		this.defaultHeaders = defaultHeaders;
		this.defaultStatusHandlers = initStatusHandlers(statusHandlerMap);
		this.messageConverters = messageConverters;
		this.builder = builder;
	}

	private static List<DefaultResponseSpec.StatusHandler> initStatusHandlers(
			@Nullable Map<Predicate<HttpStatusCode>, Function<ClientHttpResponse, Optional<? extends RuntimeException>>> handlerMap) {

		if (CollectionUtils.isEmpty(handlerMap)) {
			return Collections.emptyList();
		}
		List<DefaultResponseSpec.StatusHandler> result = new ArrayList<>();
		for (Map.Entry<Predicate<HttpStatusCode>, Function<ClientHttpResponse, Optional<? extends RuntimeException>>> entry : handlerMap.entrySet()) {
			result.add(new DefaultResponseSpec.StatusHandler(entry.getKey(), entry.getValue()));
		}
		return result;
	}


	@Override
	public RequestHeadersUriSpec<?> get() {
		return methodInternal(HttpMethod.GET);
	}

	@Override
	public RequestHeadersUriSpec<?> head() {
		return methodInternal(HttpMethod.HEAD);
	}

	@Override
	public RequestBodyUriSpec post() {
		return methodInternal(HttpMethod.POST);
	}

	@Override
	public RequestBodyUriSpec put() {
		return methodInternal(HttpMethod.PUT);
	}

	@Override
	public RequestBodyUriSpec patch() {
		return methodInternal(HttpMethod.PATCH);
	}

	@Override
	public RequestHeadersUriSpec<?> delete() {
		return methodInternal(HttpMethod.DELETE);
	}

	@Override
	public RequestHeadersUriSpec<?> options() {
		return methodInternal(HttpMethod.OPTIONS);
	}

	@Override
	public RequestBodyUriSpec method(HttpMethod method) {
		Assert.notNull(method, "Method must not be null");
		return methodInternal(method);
	}

	private RequestBodyUriSpec methodInternal(HttpMethod httpMethod) {
		return new DefaultRequestBodyUriSpec(httpMethod);
	}

	@Override
	public Builder mutate() {
		return new DefaultWebClientBuilder(this.builder);
	}


	private class DefaultRequestBodyUriSpec implements RequestBodyUriSpec {

		private final HttpMethod httpMethod;

		@Nullable
		private URI uri;

		@Nullable
		private HttpHeaders headers;

		@Nullable
		private InternalBody body;

		private final Map<String, Object> attributes = new LinkedHashMap<>(4);

		@Nullable
		private Consumer<ClientHttpRequest> httpRequestConsumer;

		public DefaultRequestBodyUriSpec(HttpMethod httpMethod) {
			this.httpMethod = httpMethod;
		}


		@Override
		public RequestBodySpec uri(String uriTemplate, Object... uriVariables) {
			attribute(URI_TEMPLATE_ATTRIBUTE, uriTemplate);
			return uri(DefaultWebClient.this.uriBuilderFactory.expand(uriTemplate, uriVariables));
		}

		@Override
		public RequestBodySpec uri(String uriTemplate, Map<String, ?> uriVariables) {
			attribute(URI_TEMPLATE_ATTRIBUTE, uriTemplate);
			return uri(DefaultWebClient.this.uriBuilderFactory.expand(uriTemplate, uriVariables));
		}

		@Override
		public RequestBodySpec uri(String uriTemplate, Function<UriBuilder, URI> uriFunction) {
			attribute(URI_TEMPLATE_ATTRIBUTE, uriTemplate);
			return uri(uriFunction.apply(DefaultWebClient.this.uriBuilderFactory.uriString(uriTemplate)));
		}

		@Override
		public RequestBodySpec uri(Function<UriBuilder, URI> uriFunction) {
			return uri(uriFunction.apply(DefaultWebClient.this.uriBuilderFactory.builder()));
		}

		@Override
		public RequestBodySpec uri(URI uri) {
			this.uri = uri;
			return this;
		}

		private HttpHeaders getHeaders() {
			if (this.headers == null) {
				this.headers = new HttpHeaders();
			}
			return this.headers;
		}

		@Override
		public DefaultRequestBodyUriSpec header(String headerName, String... headerValues) {
			for (String headerValue : headerValues) {
				getHeaders().add(headerName, headerValue);
			}
			return this;
		}

		@Override
		public DefaultRequestBodyUriSpec headers(Consumer<HttpHeaders> headersConsumer) {
			headersConsumer.accept(getHeaders());
			return this;
		}

		@Override
		public DefaultRequestBodyUriSpec accept(MediaType... acceptableMediaTypes) {
			getHeaders().setAccept(Arrays.asList(acceptableMediaTypes));
			return this;
		}

		@Override
		public DefaultRequestBodyUriSpec acceptCharset(Charset... acceptableCharsets) {
			getHeaders().setAcceptCharset(Arrays.asList(acceptableCharsets));
			return this;
		}

		@Override
		public DefaultRequestBodyUriSpec contentType(MediaType contentType) {
			getHeaders().setContentType(contentType);
			return this;
		}

		@Override
		public DefaultRequestBodyUriSpec contentLength(long contentLength) {
			getHeaders().setContentLength(contentLength);
			return this;
		}

		@Override
		public DefaultRequestBodyUriSpec ifModifiedSince(ZonedDateTime ifModifiedSince) {
			getHeaders().setIfModifiedSince(ifModifiedSince);
			return this;
		}

		@Override
		public DefaultRequestBodyUriSpec ifNoneMatch(String... ifNoneMatches) {
			getHeaders().setIfNoneMatch(Arrays.asList(ifNoneMatches));
			return this;
		}

		@Override
		public RequestBodySpec attribute(String name, Object value) {
			this.attributes.put(name, value);
			return this;
		}

		@Override
		public RequestBodySpec attributes(Consumer<Map<String, Object>> attributesConsumer) {
			attributesConsumer.accept(this.attributes);
			return this;
		}

		@Override
		public RequestBodySpec httpRequest(Consumer<ClientHttpRequest> requestConsumer) {
			this.httpRequestConsumer = (this.httpRequestConsumer != null ?
					this.httpRequestConsumer.andThen(requestConsumer) : requestConsumer);
			return this;
		}

		@Override
		public RequestBodySpec body(Object body) {
			this.body = clientHttpRequest -> writeWithMessageConverters(body, body.getClass(), clientHttpRequest);
			return this;
		}

		@Override
		public <T> RequestBodySpec body(T body, ParameterizedTypeReference<T> bodyType) {
			this.body = clientHttpRequest -> writeWithMessageConverters(body, bodyType.getType(), clientHttpRequest);
			return this;
		}

		@Override
		public RequestBodySpec body(StreamingHttpOutputMessage.Body body) {
			this.body = request -> body.writeTo(request.getBody());
			return this;
		}

		@SuppressWarnings({"rawtypes", "unchecked"})
		private void writeWithMessageConverters(Object body, Type bodyType, ClientHttpRequest clientRequest)
				throws IOException {

			MediaType contentType = clientRequest.getHeaders().getContentType();
			Class<?> bodyClass = body.getClass();

			for (HttpMessageConverter messageConverter : DefaultWebClient.this.messageConverters) {
				if (messageConverter instanceof GenericHttpMessageConverter genericMessageConverter) {
					if (genericMessageConverter.canWrite(bodyType, bodyClass, contentType)) {
						genericMessageConverter.write(body, bodyType, contentType, clientRequest);
						return;
					}
				}
				if (messageConverter.canWrite(bodyClass, contentType)) {
					messageConverter.write(body, contentType, clientRequest);
					return;
				}
			}
			String message = "No HttpMessageConverter for " + bodyClass.getName();
			if (contentType != null) {
				message += " and content type \"" + contentType + "\"";
			}
			throw new WebClientRequestException(message, this.httpMethod, initUri(), initHeaders());
		}

		@Override
		public ResponseSpec retrieve() {
			return exchangeInternal(DefaultResponseSpec::new, false);
		}

		@Override
		public <T> T exchange(ExchangeFunction<T> exchangeFunction) {
			return exchangeInternal(exchangeFunction, true);
		}

		private <T> T exchangeInternal(ExchangeFunction<T> exchangeFunction, boolean close) {
			Assert.notNull(exchangeFunction, "ExchangeFunction must not be null");

			ClientHttpResponse clientResponse = null;
			URI uri = null;
			HttpHeaders headers = null;
			try {
				uri = initUri();
				headers = initHeaders();
				ClientHttpRequest clientRequest = createRequest(uri);
				clientRequest.getHeaders().addAll(headers);
				if (this.body != null) {
					this.body.writeTo(clientRequest);
				}
				if (this.httpRequestConsumer != null) {
					this.httpRequestConsumer.accept(clientRequest);
				}
				clientResponse = clientRequest.execute();
				return exchangeFunction.exchange(clientResponse);
			}
			catch (IOException ex) {
				if (clientResponse == null) {
					throw new WebClientRequestException(ex, this.httpMethod, uri, headers);
				}
				else {
					try {
						byte[] body = WebClientUtils.getBody(clientResponse);
						Charset charset = null;

						MediaType contentType = clientResponse.getHeaders().getContentType();
						if (contentType != null) {
							charset = contentType.getCharset();
						}
						throw new WebClientResponseException("Could not execute request: " + ex.getMessage(),
								clientResponse.getStatusCode(), clientResponse.getStatusText(),
								clientResponse.getHeaders(), body, charset, null);
					}
					catch (IOException ignored) {
						throw new WebClientException("Could not execute request: " + ex.getMessage(), ex);
					}
				}
			}
			finally {
				if (close && clientResponse != null) {
					clientResponse.close();
				}
			}
		}

		private URI initUri() {
			return (this.uri != null ? this.uri : DefaultWebClient.this.uriBuilderFactory.expand(""));
		}

		private HttpHeaders initHeaders() {
			HttpHeaders defaultHeaders = DefaultWebClient.this.defaultHeaders;
			if (CollectionUtils.isEmpty(this.headers)) {
				return (defaultHeaders != null ? defaultHeaders : new HttpHeaders());
			}
			else if (CollectionUtils.isEmpty(defaultHeaders)) {
				return this.headers;
			}
			else {
				HttpHeaders result = new HttpHeaders();
				result.putAll(defaultHeaders);
				result.putAll(this.headers);
				return result;
			}
		}

		private ClientHttpRequest createRequest(URI uri) throws IOException {
			ClientHttpRequestFactory factory;
			if (DefaultWebClient.this.interceptors != null) {
				factory = DefaultWebClient.this.interceptingRequestFactory;
				if (factory == null) {
					factory = new InterceptingClientHttpRequestFactory(DefaultWebClient.this.clientRequestFactory, DefaultWebClient.this.interceptors);
					DefaultWebClient.this.interceptingRequestFactory = factory;
				}
			}
			else {
				factory = DefaultWebClient.this.clientRequestFactory;
			}
			ClientHttpRequest request = factory.createRequest(uri, this.httpMethod);
			if (DefaultWebClient.this.initializers != null) {
				DefaultWebClient.this.initializers.forEach(initializer -> initializer.initialize(request));
			}
			return request;
		}


		@FunctionalInterface
		private interface InternalBody {

			void writeTo(ClientHttpRequest request) throws IOException;
		}
	}

	private class DefaultResponseSpec implements ResponseSpec {

		private static final Predicate<HttpStatusCode> STATUS_CODE_ERROR = HttpStatusCode::isError;

		private static final StatusHandler DEFAULT_STATUS_HANDLER =
				new StatusHandler(STATUS_CODE_ERROR,
						clientResponse -> Optional.of(WebClientResponseException.create(clientResponse)));


		private final ClientHttpResponse clientResponse;

		private final List<StatusHandler> statusHandlers = new ArrayList<>(1);

		private final int defaultStatusHandlerCount;


		DefaultResponseSpec(ClientHttpResponse clientResponse) {
			this.clientResponse = clientResponse;
			this.statusHandlers.addAll(DefaultWebClient.this.defaultStatusHandlers);
			this.statusHandlers.add(DEFAULT_STATUS_HANDLER);
			this.defaultStatusHandlerCount = this.statusHandlers.size();
		}

		@Override
		public ResponseSpec onStatus(Predicate<HttpStatusCode> statusCodePredicate,
				Function<ClientHttpResponse, Optional<? extends RuntimeException>> exceptionFunction) {

			Assert.notNull(statusCodePredicate, "StatusCodePredicate must not be null");
			Assert.notNull(exceptionFunction, "Function must not be null");
			int index = this.statusHandlers.size() - this.defaultStatusHandlerCount;  // Default handlers always last
			this.statusHandlers.add(index, new StatusHandler(statusCodePredicate, exceptionFunction));
			return this;
		}

		@Override
		public <T> T body(Class<T> bodyType) {
			return readWithMessageConverters(bodyType, bodyType);
		}

		@Override
		public <T> T body(ParameterizedTypeReference<T> bodyType) {
			Type type = bodyType.getType();
			Class<T> bodyClass = bodyClass(type);
			return readWithMessageConverters(type, bodyClass);
		}

		@Override
		public <T> ResponseEntity<T> toEntity(Class<T> bodyType) {
			return toEntityInternal(bodyType, bodyType);
		}

		@Override
		public <T> ResponseEntity<T> toEntity(ParameterizedTypeReference<T> bodyType) {
			Type type = bodyType.getType();
			Class<T> bodyClass = bodyClass(type);
			return toEntityInternal(type, bodyClass);
		}

		@Override
		public ResponseEntity<Void> toBodilessEntity() {
			try {
				return ResponseEntity.status(this.clientResponse.getStatusCode())
						.headers(this.clientResponse.getHeaders())
						.build();
			}
			catch (IOException ex) {
				throw new UncheckedIOException("Could not retrieve response status code", ex);
			}
		}

		private <T> ResponseEntity<T> toEntityInternal(Type bodyType, Class<T> bodyClass) {
			T body = readWithMessageConverters(bodyType, bodyClass);
			try {
				return ResponseEntity.status(this.clientResponse.getStatusCode())
						.headers(this.clientResponse.getHeaders())
						.body(body);
			}
			catch (IOException ex) {
				throw new UncheckedIOException("Could not retrieve response status code", ex);
			}
		}

		@SuppressWarnings("unchecked")
		private static <T> Class<T> bodyClass(Type type) {
			if (type instanceof Class<?> clazz) {
				return (Class<T>) clazz;
			}
			if (type instanceof ParameterizedType parameterizedType &&
					parameterizedType.getRawType() instanceof Class<?> rawType) {
				return (Class<T>) rawType;
			}
			return (Class<T>) Object.class;
		}

		@SuppressWarnings("unchecked")
		private <T> T readWithMessageConverters(Type bodyType, Class<T> bodyClass) {
			try (this.clientResponse) {
				applyStatusHandlers(this.clientResponse);

				MediaType contentType = this.clientResponse.getHeaders().getContentType();
				if (contentType == null) {
					contentType = MediaType.APPLICATION_OCTET_STREAM;
				}

				for (HttpMessageConverter<?> messageConverter : DefaultWebClient.this.messageConverters) {
					if (messageConverter instanceof GenericHttpMessageConverter) {
						GenericHttpMessageConverter<T> theConverter = (GenericHttpMessageConverter<T>) messageConverter;
						if (theConverter.canRead(bodyType, bodyClass, contentType)) {
							return theConverter.read(bodyType, bodyClass, this.clientResponse);
						}
					}
					if (messageConverter.canRead(bodyClass, contentType)) {
						HttpMessageConverter<T> theConverter =
								(HttpMessageConverter<T>) messageConverter;
						return theConverter.read(bodyClass, this.clientResponse);
					}
				}
				throw new UnknownContentTypeException(bodyType, contentType,
						this.clientResponse.getStatusCode(), this.clientResponse.getStatusText(),
						this.clientResponse.getHeaders(), WebClientUtils.getBody(this.clientResponse));
			}
			catch (IOException ex) {
				WebClientResponseException responseEx = WebClientResponseException.create(this.clientResponse);
				responseEx.initCause(ex);
				throw responseEx;
			}
		}

		private void applyStatusHandlers(ClientHttpResponse response) throws IOException {
			HttpStatusCode statusCode = response.getStatusCode();
			for (StatusHandler handler : this.statusHandlers) {
				if (handler.test(statusCode)) {
					Optional<? extends RuntimeException> result = handler.apply(response);
					if (result.isPresent()) {
						throw result.get();
					}
					else {
						return;
					}
				}
			}
		}


		private static class StatusHandler {

			private final Predicate<HttpStatusCode> predicate;

			private final Function<ClientHttpResponse, Optional<? extends RuntimeException>> exceptionFunction;

			public StatusHandler(Predicate<HttpStatusCode> predicate,
					Function<ClientHttpResponse, Optional<? extends RuntimeException>> exceptionFunction) {

				this.predicate = predicate;
				this.exceptionFunction = exceptionFunction;
			}

			public boolean test(HttpStatusCode status) {
				return this.predicate.test(status);
			}

			public Optional<? extends RuntimeException> apply(ClientHttpResponse response) {
				return this.exceptionFunction.apply(response);
			}
		}

	}
}
