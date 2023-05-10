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
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.http.client.ClientHttpResponse;
import org.springframework.lang.Nullable;

/**
 * Exceptions that contain actual HTTP response data.
 *
 * @author Arjen Poutsma
 * @since 6.1
 */
@SuppressWarnings("RedundantSuppression")
public class WebClientResponseException extends WebClientException {

	private static final long serialVersionUID = 4127543205414951612L;


	private final HttpStatusCode statusCode;

	private final String statusText;

	private final byte[] responseBody;

	private final HttpHeaders headers;

	@Nullable
	@SuppressWarnings("serial")
	private final Charset responseCharset;

	@Nullable
	private transient final HttpRequest request;

//	@SuppressWarnings("MutableException")
//	@Nullable
//	private transient Function<ResolvableType, ?> bodyDecodeFunction;


	/**
	 * Constructor with response data only, and a default message.
	 */
	public WebClientResponseException(
			int statusCode, String statusText, @Nullable HttpHeaders headers,
			@Nullable byte[] body, @Nullable Charset charset) {

		this(statusCode, statusText, headers, body, charset, null);
	}

	/**
	 * Constructor with response data only, and a default message.
	 */
	public WebClientResponseException(
			int status, String reasonPhrase, @Nullable HttpHeaders headers,
			@Nullable byte[] body, @Nullable Charset charset, @Nullable HttpRequest request) {

		this(HttpStatusCode.valueOf(status), reasonPhrase, headers, body, charset, request);
	}

	/**
	 * Constructor with response data only, and a default message.
	 */
	public WebClientResponseException(
			HttpStatusCode statusCode, String reasonPhrase, @Nullable HttpHeaders headers,
			@Nullable byte[] body, @Nullable Charset charset, @Nullable HttpRequest request) {

		this(initMessage(statusCode, reasonPhrase, request),
				statusCode, reasonPhrase, headers, body, charset, request);
	}

	private static String initMessage(HttpStatusCode status, String reasonPhrase, @Nullable HttpRequest request) {
		return status.value() + " " + reasonPhrase +
				(request != null ? " from " + request.getMethod() + " " + request.getURI() : "");
	}

	/**
	 * Constructor with a prepared message.
	 */
	public WebClientResponseException(
			String message, int statusCode, String statusText,
			@Nullable HttpHeaders headers, @Nullable byte[] responseBody, @Nullable Charset charset) {

		this(message, statusCode, statusText, headers, responseBody, charset, null);
	}

	/**
	 * Constructor with a prepared message.
	 */
	public WebClientResponseException(
			String message, int statusCode, String statusText,
			@Nullable HttpHeaders headers, @Nullable byte[] responseBody, @Nullable Charset charset,
			@Nullable HttpRequest request) {

		this(message, HttpStatusCode.valueOf(statusCode), statusText, headers, responseBody, charset, request);
	}

	/**
	 * Constructor with a prepared message.
	 */
	public WebClientResponseException(
			String message, HttpStatusCode statusCode, String statusText, @Nullable HttpHeaders headers,
			@Nullable byte[] responseBody, @Nullable Charset charset, @Nullable HttpRequest request) {

		super(message);

		this.statusCode = statusCode;
		this.statusText = statusText;
		this.headers = copy(headers);
		this.responseBody = (responseBody != null ? responseBody : new byte[0]);
		this.responseCharset = charset;
		this.request = request;
	}

	/**
	 * Not all {@code HttpHeaders} implementations are serializable, so we
	 * make a copy to ensure that {@code WebClientResponseException} is.
	 */
	private static HttpHeaders copy(@Nullable HttpHeaders headers) {
		if (headers == null) {
			return HttpHeaders.EMPTY;
		}
		else {
			HttpHeaders result = new HttpHeaders();
			for (Map.Entry<String, List<String>> entry : headers.entrySet()) {
				for (String value : entry.getValue()) {
					result.add(entry.getKey(), value);
				}
			}
			return result;
		}
	}


	/**
	 * Return the HTTP status code value.
	 */
	public HttpStatusCode getStatusCode() {
		return this.statusCode;
	}

	/**
	 * Return the HTTP status text.
	 */
	public String getStatusText() {
		return this.statusText;
	}

	/**
	 * Return the HTTP response headers.
	 */
	public HttpHeaders getHeaders() {
		return this.headers;
	}

	/**
	 * Return the response body as a byte array.
	 */
	public byte[] getResponseBodyAsByteArray() {
		return this.responseBody;
	}

	/**
	 * Return the response content as a String using the charset of media type
	 * for the response, if available, or otherwise falling back on
	 * {@literal ISO-8859-1}. Use {@link #getResponseBodyAsString(Charset)} if
	 * you want to fall back on a different, default charset.
	 */
	public String getResponseBodyAsString() {
		return getResponseBodyAsString(StandardCharsets.ISO_8859_1);
	}

	/**
	 * Variant of {@link #getResponseBodyAsString()} that allows specifying the
	 * charset to fall back on, if a charset is not available from the media
	 * type for the response.
	 * @param defaultCharset the charset to use if the {@literal Content-Type}
	 * of the response does not specify one.
	 */
	public String getResponseBodyAsString(Charset defaultCharset) {
		return new String(this.responseBody,
				(this.responseCharset != null ? this.responseCharset : defaultCharset));
	}

	/*
	 * Decode the error content to the specified type.
	 * @param targetType the type to decode to
	 * @param <E> the expected target type
	 * @return the decoded content, or {@code null} if there is no content
	 * @throws IllegalStateException if a Decoder cannot be found
	 * @throws org.springframework.core.codec.DecodingException if decoding fails
	 */
//	@Nullable
//	public <E> E getResponseBodyAs(Class<E> targetType) {
//		return getResponseBodyAs(ResolvableType.forClass(targetType));
//	}

	/*
	 * Variant of {@link #getResponseBodyAs(Class)} with
	 * {@link ParameterizedTypeReference}.
	 */
//	@Nullable
//	public <E> E getResponseBodyAs(ParameterizedTypeReference<E> targetType) {
//		return getResponseBodyAs(ResolvableType.forType(targetType.getType()));
//	}

	@SuppressWarnings("unchecked")
//	@Nullable
//	private <E> E getResponseBodyAs(ResolvableType targetType) {
//		Assert.state(this.bodyDecodeFunction != null, "Decoder function not set");
//		return (E) this.bodyDecodeFunction.apply(targetType);
//	}

	/**
	 * Return the corresponding request.
	 */
	@Nullable
	public HttpRequest getRequest() {
		return this.request;
	}

	/*
	 * Provide a function to find a decoder the given target type.
	 * For use with {@link #getResponseBodyAs(Class)}.
	 * @param decoderFunction the function to find a decoder with
	 */
//	public void setBodyDecodeFunction(Function<ResolvableType, ?> decoderFunction) {
//		this.bodyDecodeFunction = decoderFunction;
//	}


	/**
	 * Create {@code WebClientResponseException} or an HTTP status specific subclass.
	 */
	public static WebClientResponseException create(HttpStatusCode statusCode, String statusText, HttpHeaders headers,
			byte[] body, @Nullable Charset charset) {

		return create(statusCode, statusText, headers, body, charset, null);
	}

	public static WebClientResponseException create(ClientHttpResponse clientResponse){
		try {
			HttpStatusCode statusCode = clientResponse.getStatusCode();
			String statusText = clientResponse.getStatusText();
			HttpHeaders headers = clientResponse.getHeaders();
			byte[] body = WebClientUtils.getBody(clientResponse);
			MediaType contentType = headers.getContentType();
			Charset charset = contentType != null ? contentType.getCharset() : null;

			return create(statusCode, statusText, headers, body, charset);
		}
		catch (IOException ex) {
			throw new UncheckedIOException("Could not retrieve response status: " + ex.getMessage(), ex);
		}
	}



	/**
	 * Create {@code WebClientResponseException} or an HTTP status specific subclass.
	 */
	public static WebClientResponseException create(
			HttpStatusCode statusCode, String statusText, HttpHeaders headers,
			byte[] body, @Nullable Charset charset, @Nullable HttpRequest request) {

		if (statusCode instanceof HttpStatus httpStatus) {
			return switch (httpStatus) {
				case BAD_REQUEST -> new BadRequest(statusText, headers, body, charset, request);
				case UNAUTHORIZED -> new Unauthorized(statusText, headers, body, charset, request);
				case FORBIDDEN -> new Forbidden(statusText, headers, body, charset, request);
				case NOT_FOUND -> new NotFound(statusText, headers, body, charset, request);
				case METHOD_NOT_ALLOWED -> new MethodNotAllowed(statusText, headers, body, charset, request);
				case NOT_ACCEPTABLE -> new NotAcceptable(statusText, headers, body, charset, request);
				case CONFLICT -> new Conflict(statusText, headers, body, charset, request);
				case GONE -> new Gone(statusText, headers, body, charset, request);
				case UNSUPPORTED_MEDIA_TYPE -> new UnsupportedMediaType(statusText, headers, body, charset, request);
				case TOO_MANY_REQUESTS -> new TooManyRequests(statusText, headers, body, charset, request);
				case UNPROCESSABLE_ENTITY -> new UnprocessableEntity(statusText, headers, body, charset, request);
				case INTERNAL_SERVER_ERROR -> new InternalServerError(statusText, headers, body, charset, request);
				case NOT_IMPLEMENTED -> new NotImplemented(statusText, headers, body, charset, request);
				case BAD_GATEWAY -> new BadGateway(statusText, headers, body, charset, request);
				case SERVICE_UNAVAILABLE ->  new ServiceUnavailable(statusText, headers, body, charset, request);
				case GATEWAY_TIMEOUT -> new GatewayTimeout(statusText, headers, body, charset, request);
				default -> new WebClientResponseException(statusCode, statusText, headers, body, charset, request);
			};
		}
		return new WebClientResponseException(statusCode, statusText, headers, body, charset, request);
	}


	// Subclasses for specific, client-side, HTTP status codes

	/**
	 * {@link WebClientResponseException} for status HTTP 400 Bad Request.
	 */
	@SuppressWarnings("serial")
	public static class BadRequest extends WebClientResponseException {

		BadRequest(
				String statusText, HttpHeaders headers, byte[] body, @Nullable Charset charset,
				@Nullable HttpRequest request) {

			super(HttpStatus.BAD_REQUEST.value(), statusText, headers, body, charset, request);
		}

	}

	/**
	 * {@link WebClientResponseException} for status HTTP 401 Unauthorized.
	 */
	@SuppressWarnings("serial")
	public static class Unauthorized extends WebClientResponseException {

		Unauthorized(
				String statusText, HttpHeaders headers, byte[] body, @Nullable Charset charset,
				@Nullable HttpRequest request) {

			super(HttpStatus.UNAUTHORIZED.value(), statusText, headers, body, charset, request);
		}
	}

	/**
	 * {@link WebClientResponseException} for status HTTP 403 Forbidden.
	 */
	@SuppressWarnings("serial")
	public static class Forbidden extends WebClientResponseException {

		Forbidden(
				String statusText, HttpHeaders headers, byte[] body, @Nullable Charset charset,
				@Nullable HttpRequest request) {

			super(HttpStatus.FORBIDDEN.value(), statusText, headers, body, charset, request);
		}
	}

	/**
	 * {@link WebClientResponseException} for status HTTP 404 Not Found.
	 */
	@SuppressWarnings("serial")
	public static class NotFound extends WebClientResponseException {

		NotFound(
				String statusText, HttpHeaders headers, byte[] body, @Nullable Charset charset,
				@Nullable HttpRequest request) {

			super(HttpStatus.NOT_FOUND.value(), statusText, headers, body, charset, request);
		}
	}

	/**
	 * {@link WebClientResponseException} for status HTTP 405 Method Not Allowed.
	 */
	@SuppressWarnings("serial")
	public static class MethodNotAllowed extends WebClientResponseException {

		MethodNotAllowed(
				String statusText, HttpHeaders headers, byte[] body, @Nullable Charset charset,
				@Nullable HttpRequest request) {

			super(HttpStatus.METHOD_NOT_ALLOWED.value(), statusText, headers, body, charset, request);
		}
	}

	/**
	 * {@link WebClientResponseException} for status HTTP 406 Not Acceptable.
	 */
	@SuppressWarnings("serial")
	public static class NotAcceptable extends WebClientResponseException {

		NotAcceptable(
				String statusText, HttpHeaders headers, byte[] body, @Nullable Charset charset,
				@Nullable HttpRequest request) {

			super(HttpStatus.NOT_ACCEPTABLE.value(), statusText, headers, body, charset, request);
		}
	}

	/**
	 * {@link WebClientResponseException} for status HTTP 409 Conflict.
	 */
	@SuppressWarnings("serial")
	public static class Conflict extends WebClientResponseException {

		Conflict(
				String statusText, HttpHeaders headers, byte[] body, @Nullable Charset charset,
				@Nullable HttpRequest request) {

			super(HttpStatus.CONFLICT.value(), statusText, headers, body, charset, request);
		}
	}

	/**
	 * {@link WebClientResponseException} for status HTTP 410 Gone.
	 */
	@SuppressWarnings("serial")
	public static class Gone extends WebClientResponseException {

		Gone(
				String statusText, HttpHeaders headers, byte[] body, @Nullable Charset charset,
				@Nullable HttpRequest request) {

			super(HttpStatus.GONE.value(), statusText, headers, body, charset, request);
		}
	}

	/**
	 * {@link WebClientResponseException} for status HTTP 415 Unsupported Media Type.
	 */
	@SuppressWarnings("serial")
	public static class UnsupportedMediaType extends WebClientResponseException {

		UnsupportedMediaType(
				String statusText, HttpHeaders headers, byte[] body, @Nullable Charset charset,
				@Nullable HttpRequest request) {

			super(HttpStatus.UNSUPPORTED_MEDIA_TYPE.value(), statusText, headers, body, charset, request);
		}
	}

	/**
	 * {@link WebClientResponseException} for status HTTP 422 Unprocessable Entity.
	 */
	@SuppressWarnings("serial")
	public static class UnprocessableEntity extends WebClientResponseException {

		UnprocessableEntity(
				String statusText, HttpHeaders headers, byte[] body, @Nullable Charset charset,
				@Nullable HttpRequest request) {

			super(HttpStatus.UNPROCESSABLE_ENTITY.value(), statusText, headers, body, charset, request);
		}
	}

	/**
	 * {@link WebClientResponseException} for status HTTP 429 Too Many Requests.
	 */
	@SuppressWarnings("serial")
	public static class TooManyRequests extends WebClientResponseException {

		TooManyRequests(
				String statusText, HttpHeaders headers, byte[] body, @Nullable Charset charset,
				@Nullable HttpRequest request) {

			super(HttpStatus.TOO_MANY_REQUESTS.value(), statusText, headers, body, charset, request);
		}
	}



	// Subclasses for specific, server-side, HTTP status codes

	/**
	 * {@link WebClientResponseException} for status HTTP 500 Internal Server Error.
	 */
	@SuppressWarnings("serial")
	public static class InternalServerError extends WebClientResponseException {

		InternalServerError(
				String statusText, HttpHeaders headers, byte[] body, @Nullable Charset charset,
				@Nullable HttpRequest request) {

			super(HttpStatus.INTERNAL_SERVER_ERROR.value(), statusText, headers, body, charset, request);
		}
	}

	/**
	 * {@link WebClientResponseException} for status HTTP 501 Not Implemented.
	 */
	@SuppressWarnings("serial")
	public static class NotImplemented extends WebClientResponseException {

		NotImplemented(
				String statusText, HttpHeaders headers, byte[] body, @Nullable Charset charset,
				@Nullable HttpRequest request) {

			super(HttpStatus.NOT_IMPLEMENTED.value(), statusText, headers, body, charset, request);
		}
	}

	/**
	 * {@link WebClientResponseException} for HTTP status 502 Bad Gateway.
	 */
	@SuppressWarnings("serial")
	public static class BadGateway extends WebClientResponseException {

		BadGateway(
				String statusText, HttpHeaders headers, byte[] body, @Nullable Charset charset,
				@Nullable HttpRequest request) {

			super(HttpStatus.BAD_GATEWAY.value(), statusText, headers, body, charset, request);
		}
	}

	/**
	 * {@link WebClientResponseException} for status HTTP 503 Service Unavailable.
	 */
	@SuppressWarnings("serial")
	public static class ServiceUnavailable extends WebClientResponseException {

		ServiceUnavailable(
				String statusText, HttpHeaders headers, byte[] body, @Nullable Charset charset,
				@Nullable HttpRequest request) {

			super(HttpStatus.SERVICE_UNAVAILABLE.value(), statusText, headers, body, charset, request);
		}
	}

	/**
	 * {@link WebClientResponseException} for status HTTP 504 Gateway Timeout.
	 */
	@SuppressWarnings("serial")
	public static class GatewayTimeout extends WebClientResponseException {

		GatewayTimeout(
				String statusText, HttpHeaders headers, byte[] body, @Nullable Charset charset,
				@Nullable HttpRequest request) {

			super(HttpStatus.GATEWAY_TIMEOUT.value(), statusText, headers, body, charset, request);
		}
	}

}
