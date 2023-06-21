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
public class RestClientResponseException extends RestClientException {

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


	/**
	 * Constructor with response data only, and a default message.
	 */
	public RestClientResponseException(
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
	public RestClientResponseException(
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
	 * make a copy to ensure that {@code RestClientResponseException} is.
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

	/**
	 * Return the corresponding request.
	 */
	@Nullable
	public HttpRequest getRequest() {
		return this.request;
	}


	/**
	 * Create {@code RestClientResponseException} or an HTTP status specific subclass.
	 */
	public static RestClientResponseException create(HttpStatusCode statusCode, String statusText, HttpHeaders headers,
			byte[] body, @Nullable Charset charset) {

		return create(statusCode, statusText, headers, body, charset, null);
	}

	public static RestClientResponseException create(ClientHttpResponse clientResponse){
		try {
			HttpStatusCode statusCode = clientResponse.getStatusCode();
			String statusText = clientResponse.getStatusText();
			HttpHeaders headers = clientResponse.getHeaders();
			byte[] body = RestClientUtils.getBody(clientResponse);
			MediaType contentType = headers.getContentType();
			Charset charset = contentType != null ? contentType.getCharset() : null;

			return create(statusCode, statusText, headers, body, charset);
		}
		catch (IOException ex) {
			throw new UncheckedIOException("Could not retrieve response status: " + ex.getMessage(), ex);
		}
	}



	/**
	 * Create {@code RestClientResponseException} or an HTTP status specific subclass.
	 */
	public static RestClientResponseException create(
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
				default -> new RestClientResponseException(statusCode, statusText, headers, body, charset, request);
			};
		}
		return new RestClientResponseException(statusCode, statusText, headers, body, charset, request);
	}


	// Subclasses for specific, client-side, HTTP status codes

	/**
	 * {@link RestClientResponseException} for status HTTP 400 Bad Request.
	 */
	@SuppressWarnings("serial")
	public static class BadRequest extends RestClientResponseException {

		BadRequest(
				String statusText, HttpHeaders headers, byte[] body, @Nullable Charset charset,
				@Nullable HttpRequest request) {

			super(HttpStatus.BAD_REQUEST, statusText, headers, body, charset, request);
		}

	}

	/**
	 * {@link RestClientResponseException} for status HTTP 401 Unauthorized.
	 */
	@SuppressWarnings("serial")
	public static class Unauthorized extends RestClientResponseException {

		Unauthorized(
				String statusText, HttpHeaders headers, byte[] body, @Nullable Charset charset,
				@Nullable HttpRequest request) {

			super(HttpStatus.UNAUTHORIZED, statusText, headers, body, charset, request);
		}
	}

	/**
	 * {@link RestClientResponseException} for status HTTP 403 Forbidden.
	 */
	@SuppressWarnings("serial")
	public static class Forbidden extends RestClientResponseException {

		Forbidden(
				String statusText, HttpHeaders headers, byte[] body, @Nullable Charset charset,
				@Nullable HttpRequest request) {

			super(HttpStatus.FORBIDDEN, statusText, headers, body, charset, request);
		}
	}

	/**
	 * {@link RestClientResponseException} for status HTTP 404 Not Found.
	 */
	@SuppressWarnings("serial")
	public static class NotFound extends RestClientResponseException {

		NotFound(
				String statusText, HttpHeaders headers, byte[] body, @Nullable Charset charset,
				@Nullable HttpRequest request) {

			super(HttpStatus.NOT_FOUND, statusText, headers, body, charset, request);
		}
	}

	/**
	 * {@link RestClientResponseException} for status HTTP 405 Method Not Allowed.
	 */
	@SuppressWarnings("serial")
	public static class MethodNotAllowed extends RestClientResponseException {

		MethodNotAllowed(
				String statusText, HttpHeaders headers, byte[] body, @Nullable Charset charset,
				@Nullable HttpRequest request) {

			super(HttpStatus.METHOD_NOT_ALLOWED, statusText, headers, body, charset, request);
		}
	}

	/**
	 * {@link RestClientResponseException} for status HTTP 406 Not Acceptable.
	 */
	@SuppressWarnings("serial")
	public static class NotAcceptable extends RestClientResponseException {

		NotAcceptable(
				String statusText, HttpHeaders headers, byte[] body, @Nullable Charset charset,
				@Nullable HttpRequest request) {

			super(HttpStatus.NOT_ACCEPTABLE, statusText, headers, body, charset, request);
		}
	}

	/**
	 * {@link RestClientResponseException} for status HTTP 409 Conflict.
	 */
	@SuppressWarnings("serial")
	public static class Conflict extends RestClientResponseException {

		Conflict(
				String statusText, HttpHeaders headers, byte[] body, @Nullable Charset charset,
				@Nullable HttpRequest request) {

			super(HttpStatus.CONFLICT, statusText, headers, body, charset, request);
		}
	}

	/**
	 * {@link RestClientResponseException} for status HTTP 410 Gone.
	 */
	@SuppressWarnings("serial")
	public static class Gone extends RestClientResponseException {

		Gone(
				String statusText, HttpHeaders headers, byte[] body, @Nullable Charset charset,
				@Nullable HttpRequest request) {

			super(HttpStatus.GONE, statusText, headers, body, charset, request);
		}
	}

	/**
	 * {@link RestClientResponseException} for status HTTP 415 Unsupported Media Type.
	 */
	@SuppressWarnings("serial")
	public static class UnsupportedMediaType extends RestClientResponseException {

		UnsupportedMediaType(
				String statusText, HttpHeaders headers, byte[] body, @Nullable Charset charset,
				@Nullable HttpRequest request) {

			super(HttpStatus.UNSUPPORTED_MEDIA_TYPE, statusText, headers, body, charset, request);
		}
	}

	/**
	 * {@link RestClientResponseException} for status HTTP 422 Unprocessable Entity.
	 */
	@SuppressWarnings("serial")
	public static class UnprocessableEntity extends RestClientResponseException {

		UnprocessableEntity(
				String statusText, HttpHeaders headers, byte[] body, @Nullable Charset charset,
				@Nullable HttpRequest request) {

			super(HttpStatus.UNPROCESSABLE_ENTITY, statusText, headers, body, charset, request);
		}
	}

	/**
	 * {@link RestClientResponseException} for status HTTP 429 Too Many Requests.
	 */
	@SuppressWarnings("serial")
	public static class TooManyRequests extends RestClientResponseException {

		TooManyRequests(
				String statusText, HttpHeaders headers, byte[] body, @Nullable Charset charset,
				@Nullable HttpRequest request) {

			super(HttpStatus.TOO_MANY_REQUESTS, statusText, headers, body, charset, request);
		}
	}



	// Subclasses for specific, server-side, HTTP status codes

	/**
	 * {@link RestClientResponseException} for status HTTP 500 Internal Server Error.
	 */
	@SuppressWarnings("serial")
	public static class InternalServerError extends RestClientResponseException {

		InternalServerError(
				String statusText, HttpHeaders headers, byte[] body, @Nullable Charset charset,
				@Nullable HttpRequest request) {

			super(HttpStatus.INTERNAL_SERVER_ERROR, statusText, headers, body, charset, request);
		}
	}

	/**
	 * {@link RestClientResponseException} for status HTTP 501 Not Implemented.
	 */
	@SuppressWarnings("serial")
	public static class NotImplemented extends RestClientResponseException {

		NotImplemented(
				String statusText, HttpHeaders headers, byte[] body, @Nullable Charset charset,
				@Nullable HttpRequest request) {

			super(HttpStatus.NOT_IMPLEMENTED, statusText, headers, body, charset, request);
		}
	}

	/**
	 * {@link RestClientResponseException} for HTTP status 502 Bad Gateway.
	 */
	@SuppressWarnings("serial")
	public static class BadGateway extends RestClientResponseException {

		BadGateway(
				String statusText, HttpHeaders headers, byte[] body, @Nullable Charset charset,
				@Nullable HttpRequest request) {

			super(HttpStatus.BAD_GATEWAY, statusText, headers, body, charset, request);
		}
	}

	/**
	 * {@link RestClientResponseException} for status HTTP 503 Service Unavailable.
	 */
	@SuppressWarnings("serial")
	public static class ServiceUnavailable extends RestClientResponseException {

		ServiceUnavailable(
				String statusText, HttpHeaders headers, byte[] body, @Nullable Charset charset,
				@Nullable HttpRequest request) {

			super(HttpStatus.SERVICE_UNAVAILABLE, statusText, headers, body, charset, request);
		}
	}

	/**
	 * {@link RestClientResponseException} for status HTTP 504 Gateway Timeout.
	 */
	@SuppressWarnings("serial")
	public static class GatewayTimeout extends RestClientResponseException {

		GatewayTimeout(
				String statusText, HttpHeaders headers, byte[] body, @Nullable Charset charset,
				@Nullable HttpRequest request) {

			super(HttpStatus.GATEWAY_TIMEOUT, statusText, headers, body, charset, request);
		}
	}

}
