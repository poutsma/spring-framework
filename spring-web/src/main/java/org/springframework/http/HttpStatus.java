/*
 * Copyright 2002-2021 the original author or authors.
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

package org.springframework.http;

import java.io.Serializable;
import java.util.Collection;

import org.jetbrains.annotations.NotNull;

import org.springframework.lang.Nullable;
import org.springframework.util.Assert;
import org.springframework.util.ConcurrentLruCache;
import org.springframework.util.StringUtils;

/**
 * Enumeration of HTTP status codes.
 *
 * <p>The HTTP status code series can be retrieved via {@link #series()}.
 *
 * @author Arjen Poutsma
 * @author Sebastien Deleuze
 * @author Brian Clozel
 * @since 3.0
 * @see HttpStatus.Series
 * @see <a href="https://www.iana.org/assignments/http-status-codes">HTTP Status Code Registry</a>
 * @see <a href="https://en.wikipedia.org/wiki/List_of_HTTP_status_codes">List of HTTP status codes - Wikipedia</a>
 */
public final class HttpStatus implements Comparable<HttpStatus>, Serializable {

	// 1xx Informational

	/**
	 * {@code 100 Continue}.
	 * @see <a href="https://tools.ietf.org/html/rfc7231#section-6.2.1">HTTP/1.1: Semantics and Content, section 6.2.1</a>
	 */
	public static final HttpStatus CONTINUE;

	/**
	 * The integer equivalent of {@link #CONTINUE}.
	 * @see <a href="https://tools.ietf.org/html/rfc7231#section-6.2.1">HTTP/1.1: Semantics and Content, section 6.2.1</a>
	 */
	public static final int CONTINUE_VALUE = 100;

	/**
	 * {@code 101 Switching Protocols}.
	 * @see <a href="https://tools.ietf.org/html/rfc7231#section-6.2.2">HTTP/1.1: Semantics and Content, section 6.2.2</a>
	 */
	public static final HttpStatus SWITCHING_PROTOCOLS;
	/**
	 * The integer equivalent of {@link #SWITCHING_PROTOCOLS}.
	 * @see <a href="https://tools.ietf.org/html/rfc7231#section-6.2.2">HTTP/1.1: Semantics and Content, section 6.2.2</a>
	 */
	public static final int SWITCHING_PROTOCOLS_VALUE = 101;

	/**
	 * {@code 102 Processing}.
	 * @see <a href="https://tools.ietf.org/html/rfc2518#section-10.1">WebDAV</a>
	 */
	public static final HttpStatus PROCESSING;

	/**
	 * The integer equivalent of {@link #PROCESSING}.
	 * @see <a href="https://tools.ietf.org/html/rfc2518#section-10.1">WebDAV</a>
	 */
	public static final int PROCESSING_VALUE = 102;

	/**
	 * {@code 103 Checkpoint}.
	 * @see <a href="https://code.google.com/p/gears/wiki/ResumableHttpRequestsProposal">A proposal for supporting
	 * resumable POST/PUT HTTP requests in HTTP/1.0</a>
	 */
	public static final HttpStatus CHECKPOINT;

	/**
	 * The integer equivalent of {@link #CHECKPOINT}.
	 * @see <a href="https://code.google.com/p/gears/wiki/ResumableHttpRequestsProposal">A proposal for supporting
	 * resumable POST/PUT HTTP requests in HTTP/1.0</a>
	 */
	public static final int CHECKPOINT_VALUE = 103;

	// 2xx Success

	/**
	 * {@code 200 OK}.
	 * @see <a href="https://tools.ietf.org/html/rfc7231#section-6.3.1">HTTP/1.1: Semantics and Content, section 6.3.1</a>
	 */
	public static final HttpStatus OK;

	/**
	 * The integer equivalent of {@link #OK}.
	 * @see <a href="https://tools.ietf.org/html/rfc7231#section-6.3.1">HTTP/1.1: Semantics and Content, section 6.3.1</a>
	 */
	public static final int OK_VALUE = 200;

	/**
	 * {@code 201 Created}.
	 * @see <a href="https://tools.ietf.org/html/rfc7231#section-6.3.2">HTTP/1.1: Semantics and Content, section 6.3.2</a>
	 */
	public static final HttpStatus CREATED;

	/**
	 * The integer equivalent of {@link #CREATED}.
	 * @see <a href="https://tools.ietf.org/html/rfc7231#section-6.3.2">HTTP/1.1: Semantics and Content, section 6.3.2</a>
	 */
	public static final int CREATED_VALUE = 201;

	/**
	 * {@code 202 Accepted}.
	 * @see <a href="https://tools.ietf.org/html/rfc7231#section-6.3.3">HTTP/1.1: Semantics and Content, section 6.3.3</a>
	 */
	public static final HttpStatus ACCEPTED;

	/**
	 * The integer equivalent of {@link #ACCEPTED}.
	 * @see <a href="https://tools.ietf.org/html/rfc7231#section-6.3.3">HTTP/1.1: Semantics and Content, section 6.3.3</a>
	 */
	public static final int ACCEPTED_VALUE = 202;

	/**
	 * {@code 203 Non-Authoritative Information}.
	 * @see <a href="https://tools.ietf.org/html/rfc7231#section-6.3.4">HTTP/1.1: Semantics and Content, section 6.3.4</a>
	 */
	public static final HttpStatus NON_AUTHORITATIVE_INFORMATION;

	/**
	 * The integer equivalent of {@link #NON_AUTHORITATIVE_INFORMATION}.
	 * @see <a href="https://tools.ietf.org/html/rfc7231#section-6.3.4">HTTP/1.1: Semantics and Content, section 6.3.4</a>
	 */
	public static final int NON_AUTHORITATIVE_INFORMATION_VALUE = 203;

	/**
	 * {@code 204 No Content}.
	 * @see <a href="https://tools.ietf.org/html/rfc7231#section-6.3.5">HTTP/1.1: Semantics and Content, section 6.3.5</a>
	 */
	public static final HttpStatus NO_CONTENT;

	/**
	 * The integer equivalent of {@link #NO_CONTENT}.
	 * @see <a href="https://tools.ietf.org/html/rfc7231#section-6.3.5">HTTP/1.1: Semantics and Content, section 6.3.5</a>
	 */
	public static final int NO_CONTENT_VALUE = 204;

	/**
	 * {@code 205 Reset Content}.
	 * @see <a href="https://tools.ietf.org/html/rfc7231#section-6.3.6">HTTP/1.1: Semantics and Content, section 6.3.6</a>
	 */
	public static final HttpStatus RESET_CONTENT;

	/**
	 * The integer equivalent of {@link #RESET_CONTENT}.
	 * @see <a href="https://tools.ietf.org/html/rfc7231#section-6.3.6">HTTP/1.1: Semantics and Content, section 6.3.6</a>
	 */
	public static final int RESET_CONTENT_VALUE = 205;

	/**
	 * {@code 206 Partial Content}.
	 * @see <a href="https://tools.ietf.org/html/rfc7233#section-4.1">HTTP/1.1: Range Requests, section 4.1</a>
	 */
	public static final HttpStatus PARTIAL_CONTENT;

	/**
	 * The integer equivalent of {@link #PARTIAL_CONTENT}.
	 * @see <a href="https://tools.ietf.org/html/rfc7233#section-4.1">HTTP/1.1: Range Requests, section 4.1</a>
	 */
	public static final int PARTIAL_CONTENT_VALUE = 206;

	/**
	 * {@code 207 Multi-Status}.
	 * @see <a href="https://tools.ietf.org/html/rfc4918#section-13">WebDAV</a>
	 */
	public static final HttpStatus MULTI_STATUS;

	/**
	 * The integer equivalent of {@link #MULTI_STATUS}.
	 * @see <a href="https://tools.ietf.org/html/rfc4918#section-13">WebDAV</a>
	 */
	public static final int MULTI_STATUS_VALUE = 207;

	/**
	 * {@code 208 Already Reported}.
	 * @see <a href="https://tools.ietf.org/html/rfc5842#section-7.1">WebDAV Binding Extensions</a>
	 */
	public static final HttpStatus ALREADY_REPORTED;

	/**
	 * The integer equivalent of {@link #ALREADY_REPORTED}.
	 * @see <a href="https://tools.ietf.org/html/rfc5842#section-7.1">WebDAV Binding Extensions</a>
	 */
	public static final int ALREADY_REPORTED_VALUE = 208;

	/**
	 * {@code 226 IM Used}.
	 * @see <a href="https://tools.ietf.org/html/rfc3229#section-10.4.1">Delta encoding in HTTP</a>
	 */
	public static final HttpStatus IM_USED;

	/**
	 * The integer equivalent of {@link #IM_USED}.
	 * @see <a href="https://tools.ietf.org/html/rfc3229#section-10.4.1">Delta encoding in HTTP</a>
	 */
	public static final int IM_USED_VALUE = 226;

	// 3xx Redirection

	/**
	 * {@code 300 Multiple Choices}.
	 * @see <a href="https://tools.ietf.org/html/rfc7231#section-6.4.1">HTTP/1.1: Semantics and Content, section 6.4.1</a>
	 */
	public static final HttpStatus MULTIPLE_CHOICES;

	/**
	 * The integer equivalent of {@link #MULTIPLE_CHOICES}.
	 * @see <a href="https://tools.ietf.org/html/rfc7231#section-6.4.1">HTTP/1.1: Semantics and Content, section 6.4.1</a>
	 */
	public static final int MULTIPLE_CHOICES_VALUE = 300;

	/**
	 * {@code 301 Moved Permanently}.
	 * @see <a href="https://tools.ietf.org/html/rfc7231#section-6.4.2">HTTP/1.1: Semantics and Content, section 6.4.2</a>
	 */
	public static final HttpStatus MOVED_PERMANENTLY;

	/**
	 * The integer equivalent of {@link #MOVED_PERMANENTLY}.
	 * @see <a href="https://tools.ietf.org/html/rfc7231#section-6.4.2">HTTP/1.1: Semantics and Content, section 6.4.2</a>
	 */
	public static final int MOVED_PERMANENTLY_VALUE = 301;

	/**
	 * {@code 302 Found}.
	 * @see <a href="https://tools.ietf.org/html/rfc7231#section-6.4.3">HTTP/1.1: Semantics and Content, section 6.4.3</a>
	 */
	public static final HttpStatus FOUND;

	/**
	 * The integer equivalent of {@link #FOUND}.
	 * @see <a href="https://tools.ietf.org/html/rfc7231#section-6.4.3">HTTP/1.1: Semantics and Content, section 6.4.3</a>
	 */
	public static final int FOUND_VALUE = 302;

	/**
	 * {@code 303 See Other}.
	 * @see <a href="https://tools.ietf.org/html/rfc7231#section-6.4.4">HTTP/1.1: Semantics and Content, section 6.4.4</a>
	 */
	public static final HttpStatus SEE_OTHER;

	/**
	 * The integer equivalent of {@link #SEE_OTHER}.
	 * @see <a href="https://tools.ietf.org/html/rfc7231#section-6.4.4">HTTP/1.1: Semantics and Content, section 6.4.4</a>
	 */
	public static final int SEE_OTHER_VALUE = 303;

	/**
	 * {@code 304 Not Modified}.
	 * @see <a href="https://tools.ietf.org/html/rfc7232#section-4.1">HTTP/1.1: Conditional Requests, section 4.1</a>
	 */
	public static final HttpStatus NOT_MODIFIED;

	/**
	 * The integer equivalent of {@link #NOT_MODIFIED}.
	 * @see <a href="https://tools.ietf.org/html/rfc7232#section-4.1">HTTP/1.1: Conditional Requests, section 4.1</a>
	 */
	public static final int NOT_MODIFIED_VALUE = 304;

	/**
	 * {@code 307 Temporary Redirect}.
	 * @see <a href="https://tools.ietf.org/html/rfc7231#section-6.4.7">HTTP/1.1: Semantics and Content, section 6.4.7</a>
	 */
	public static final HttpStatus TEMPORARY_REDIRECT;

	/**
	 * The integer equivalent of {@link #TEMPORARY_REDIRECT}.
	 * @see <a href="https://tools.ietf.org/html/rfc7231#section-6.4.7">HTTP/1.1: Semantics and Content, section 6.4.7</a>
	 */
	public static final int TEMPORARY_REDIRECT_VALUE = 307;

	/**
	 * {@code 308 Permanent Redirect}.
	 * @see <a href="https://tools.ietf.org/html/rfc7238">RFC 7238</a>
	 */
	public static final HttpStatus PERMANENT_REDIRECT;

	/**
	 * The integer equivalent of {@link #PERMANENT_REDIRECT}.
	 * @see <a href="https://tools.ietf.org/html/rfc7238">RFC 7238</a>
	 */
	public static final int PERMANENT_REDIRECT_VALUE = 308;

	// --- 4xx Client Error ---

	/**
	 * {@code 400 Bad Request}.
	 * @see <a href="https://tools.ietf.org/html/rfc7231#section-6.5.1">HTTP/1.1: Semantics and Content, section 6.5.1</a>
	 */
	public static final HttpStatus BAD_REQUEST;

	/**
	 * The integer equivalent of {@link #BAD_REQUEST}.
	 * @see <a href="https://tools.ietf.org/html/rfc7231#section-6.5.1">HTTP/1.1: Semantics and Content, section 6.5.1</a>
	 */
	public static final int BAD_REQUEST_VALUE = 400;

	/**
	 * {@code 401 Unauthorized}.
	 * @see <a href="https://tools.ietf.org/html/rfc7235#section-3.1">HTTP/1.1: Authentication, section 3.1</a>
	 */
	public static final HttpStatus UNAUTHORIZED;

	/**
	 * {@code 401 Unauthorized}.
	 * @see <a href="https://tools.ietf.org/html/rfc7235#section-3.1">HTTP/1.1: Authentication, section 3.1</a>
	 */
	public static final int UNAUTHORIZED_VALUE = 401;

	/**
	 * {@code 402 Payment Required}.
	 * @see <a href="https://tools.ietf.org/html/rfc7231#section-6.5.2">HTTP/1.1: Semantics and Content, section 6.5.2</a>
	 */
	public static final HttpStatus PAYMENT_REQUIRED;

	/**
	 * The integer equivalent of {@link #PAYMENT_REQUIRED}.
	 * @see <a href="https://tools.ietf.org/html/rfc7231#section-6.5.2">HTTP/1.1: Semantics and Content, section 6.5.2</a>
	 */
	public static final int PAYMENT_REQUIRED_VALUE = 402;

	/**
	 * {@code 403 Forbidden}.
	 * @see <a href="https://tools.ietf.org/html/rfc7231#section-6.5.3">HTTP/1.1: Semantics and Content, section 6.5.3</a>
	 */
	public static final HttpStatus FORBIDDEN;

	/**
	 * The integer equivalent of {@link #FORBIDDEN}.
	 * @see <a href="https://tools.ietf.org/html/rfc7231#section-6.5.3">HTTP/1.1: Semantics and Content, section 6.5.3</a>
	 */
	public static final int FORBIDDEN_VALUE = 403;

	/**
	 * {@code 404 Not Found}.
	 * @see <a href="https://tools.ietf.org/html/rfc7231#section-6.5.4">HTTP/1.1: Semantics and Content, section 6.5.4</a>
	 */
	public static final HttpStatus NOT_FOUND;

	/**
	 * The integer equivalent of {@link #NOT_FOUND}.
	 * @see <a href="https://tools.ietf.org/html/rfc7231#section-6.5.4">HTTP/1.1: Semantics and Content, section 6.5.4</a>
	 */
	public static final int NOT_FOUND_VALUE = 404;

	/**
	 * {@code 405 Method Not Allowed}.
	 * @see <a href="https://tools.ietf.org/html/rfc7231#section-6.5.5">HTTP/1.1: Semantics and Content, section 6.5.5</a>
	 */
	public static final HttpStatus METHOD_NOT_ALLOWED;

	/**
	 * The integer equivalent of {@link #METHOD_NOT_ALLOWED}.
	 * @see <a href="https://tools.ietf.org/html/rfc7231#section-6.5.5">HTTP/1.1: Semantics and Content, section 6.5.5</a>
	 */
	public static final int METHOD_NOT_ALLOWED_VALUE = 405;

	/**
	 * {@code 406 Not Acceptable}.
	 * @see <a href="https://tools.ietf.org/html/rfc7231#section-6.5.6">HTTP/1.1: Semantics and Content, section 6.5.6</a>
	 */
	public static final HttpStatus NOT_ACCEPTABLE;

	/**
	 * The integer equivalent of {@link #NOT_ACCEPTABLE}.
	 * @see <a href="https://tools.ietf.org/html/rfc7231#section-6.5.6">HTTP/1.1: Semantics and Content, section 6.5.6</a>
	 */
	public static final int NOT_ACCEPTABLE_VALUE = 406;

	/**
	 * {@code 407 Proxy Authentication Required}.
	 * @see <a href="https://tools.ietf.org/html/rfc7235#section-3.2">HTTP/1.1: Authentication, section 3.2</a>
	 */
	public static final HttpStatus PROXY_AUTHENTICATION_REQUIRED;

	/**
	 * The integer equivalent of {@link #PROXY_AUTHENTICATION_REQUIRED}.
	 * @see <a href="https://tools.ietf.org/html/rfc7235#section-3.2">HTTP/1.1: Authentication, section 3.2</a>
	 */
	public static final int PROXY_AUTHENTICATION_REQUIRED_VALUE = 407;

	/**
	 * {@code 408 Request Timeout}.
	 * @see <a href="https://tools.ietf.org/html/rfc7231#section-6.5.7">HTTP/1.1: Semantics and Content, section 6.5.7</a>
	 */
	public static final HttpStatus REQUEST_TIMEOUT;

	/**
	 * The integer equivalent of {@link #REQUEST_TIMEOUT}.
	 * @see <a href="https://tools.ietf.org/html/rfc7231#section-6.5.7">HTTP/1.1: Semantics and Content, section 6.5.7</a>
	 */
	public static final int REQUEST_TIMEOUT_VALUE = 408;

	/**
	 * {@code 409 Conflict}.
	 * @see <a href="https://tools.ietf.org/html/rfc7231#section-6.5.8">HTTP/1.1: Semantics and Content, section 6.5.8</a>
	 */
	public static final HttpStatus CONFLICT;

	/**
	 * The integer equivalent of {@link #CONFLICT}.
	 * @see <a href="https://tools.ietf.org/html/rfc7231#section-6.5.8">HTTP/1.1: Semantics and Content, section 6.5.8</a>
	 */
	public static final int CONFLICT_VALUE = 409;

	/**
	 * {@code 410 Gone}.
	 * @see <a href="https://tools.ietf.org/html/rfc7231#section-6.5.9">
	 *     HTTP/1.1: Semantics and Content, section 6.5.9</a>
	 */
	public static final HttpStatus GONE;

	/**
	 * The integer equivalent of {@link #GONE}.
	 * @see <a href="https://tools.ietf.org/html/rfc7231#section-6.5.9">
	 *     HTTP/1.1: Semantics and Content, section 6.5.9</a>
	 */
	public static final int GONE_VALUE = 410;

	/**
	 * {@code 411 Length Required}.
	 * @see <a href="https://tools.ietf.org/html/rfc7231#section-6.5.10">
	 *     HTTP/1.1: Semantics and Content, section 6.5.10</a>
	 */
	public static final HttpStatus LENGTH_REQUIRED;

	/**
	 * The integer equivalent of {@link #LENGTH_REQUIRED}.
	 * @see <a href="https://tools.ietf.org/html/rfc7231#section-6.5.10">
	 *     HTTP/1.1: Semantics and Content, section 6.5.10</a>
	 */
	public static final int LENGTH_REQUIRED_VALUE = 411;

	/**
	 * {@code 412 Precondition failed}.
	 * @see <a href="https://tools.ietf.org/html/rfc7232#section-4.2">
	 *     HTTP/1.1: Conditional Requests, section 4.2</a>
	 */
	public static final HttpStatus PRECONDITION_FAILED;

	/**
	 * The integer equivalent of {@link #PRECONDITION_FAILED}.
	 * @see <a href="https://tools.ietf.org/html/rfc7232#section-4.2">
	 *     HTTP/1.1: Conditional Requests, section 4.2</a>
	 */
	public static final int PRECONDITION_FAILED_VALUE = 412;

	/**
	 * {@code 413 Payload Too Large}.
	 * @see <a href="https://tools.ietf.org/html/rfc7231#section-6.5.11">
	 *     HTTP/1.1: Semantics and Content, section 6.5.11</a>
	 */
	public static final HttpStatus PAYLOAD_TOO_LARGE;

	/**
	 * The integer equivalent of {@link #PAYLOAD_TOO_LARGE}.
	 * @see <a href="https://tools.ietf.org/html/rfc7231#section-6.5.11">
	 *     HTTP/1.1: Semantics and Content, section 6.5.11</a>
	 */
	public static final int PAYLOAD_TOO_LARGE_VALUE = 413;

	/**
	 * {@code 414 URI Too Long}.
	 * @since 4.1
	 * @see <a href="https://tools.ietf.org/html/rfc7231#section-6.5.12">
	 *     HTTP/1.1: Semantics and Content, section 6.5.12</a>
	 */
	public static final HttpStatus URI_TOO_LONG;

	/**
	 * The integer equivalent of {@link #URI_TOO_LONG}.
	 * @since 4.1
	 * @see <a href="https://tools.ietf.org/html/rfc7231#section-6.5.12">
	 *     HTTP/1.1: Semantics and Content, section 6.5.12</a>
	 */
	public static final int URI_TOO_LONG_VALUE = 414;

	/**
	 * {@code 415 Unsupported Media Type}.
	 * @see <a href="https://tools.ietf.org/html/rfc7231#section-6.5.13">
	 *     HTTP/1.1: Semantics and Content, section 6.5.13</a>
	 */
	public static final HttpStatus UNSUPPORTED_MEDIA_TYPE;

	/**
	 * The integer equivalent of {@link #UNSUPPORTED_MEDIA_TYPE}.
	 * @see <a href="https://tools.ietf.org/html/rfc7231#section-6.5.13">
	 *     HTTP/1.1: Semantics and Content, section 6.5.13</a>
	 */
	public static final int UNSUPPORTED_MEDIA_TYPE_VALUE = 415;

	/**
	 * {@code 416 Requested Range Not Satisfiable}.
	 * @see <a href="https://tools.ietf.org/html/rfc7233#section-4.4">HTTP/1.1: Range Requests, section 4.4</a>
	 */
	public static final HttpStatus REQUESTED_RANGE_NOT_SATISFIABLE;

	/**
	 * The integer equivalent of {@link #REQUESTED_RANGE_NOT_SATISFIABLE}.
	 * @see <a href="https://tools.ietf.org/html/rfc7233#section-4.4">HTTP/1.1: Range Requests, section 4.4</a>
	 */
	public static final int REQUESTED_RANGE_NOT_SATISFIABLE_VALUE = 416;

	/**
	 * {@code 417 Expectation Failed}.
	 * @see <a href="https://tools.ietf.org/html/rfc7231#section-6.5.14">
	 *     HTTP/1.1: Semantics and Content, section 6.5.14</a>
	 */
	public static final HttpStatus EXPECTATION_FAILED;

	/**
	 * The integer equivalent of {@link #EXPECTATION_FAILED}.
	 * @see <a href="https://tools.ietf.org/html/rfc7231#section-6.5.14">
	 *     HTTP/1.1: Semantics and Content, section 6.5.14</a>
	 */
	public static final int EXPECTATION_FAILED_VALUE = 417;

	/**
	 * {@code 418 I'm a teapot}.
	 * @see <a href="https://tools.ietf.org/html/rfc2324#section-2.3.2">HTCPCP/1.0</a>
	 */
	public static final HttpStatus I_AM_A_TEAPOT;

	/**
	 * The integer equivalent of {@link #I_AM_A_TEAPOT}.
	 * @see <a href="https://tools.ietf.org/html/rfc2324#section-2.3.2">HTCPCP/1.0</a>
	 */
	public static final int I_AM_A_TEAPOT_VALUE = 418;

	/**
	 * {@code 422 Unprocessable Entity}.
	 * @see <a href="https://tools.ietf.org/html/rfc4918#section-11.2">WebDAV</a>
	 */
	public static final HttpStatus UNPROCESSABLE_ENTITY;

	/**
	 * The integer equivalent of {@link #UNPROCESSABLE_ENTITY}.
	 * @see <a href="https://tools.ietf.org/html/rfc4918#section-11.2">WebDAV</a>
	 */
	public static final int UNPROCESSABLE_ENTITY_VALUE = 422;

	/**
	 * {@code 423 Locked}.
	 * @see <a href="https://tools.ietf.org/html/rfc4918#section-11.3">WebDAV</a>
	 */
	public static final HttpStatus LOCKED;

	/**
	 * The integer equivalent of {@link #LOCKED}.
	 * @see <a href="https://tools.ietf.org/html/rfc4918#section-11.3">WebDAV</a>
	 */
	public static final int LOCKED_VALUE = 423;

	/**
	 * {@code 424 Failed Dependency}.
	 * @see <a href="https://tools.ietf.org/html/rfc4918#section-11.4">WebDAV</a>
	 */
	public static final HttpStatus FAILED_DEPENDENCY;

	/**
	 * The integer equivalent of {@link #FAILED_DEPENDENCY}.
	 * @see <a href="https://tools.ietf.org/html/rfc4918#section-11.4">WebDAV</a>
	 */
	public static final int FAILED_DEPENDENCY_VALUE = 424;

	/**
	 * {@code 425 Too Early}.
	 * @see <a href="https://tools.ietf.org/html/rfc8470">RFC 8470</a>
	 */
	public static final HttpStatus TOO_EARLY;

	/**
	 * The integer equivalent of {@link #TOO_EARLY}
	 * @see <a href="https://tools.ietf.org/html/rfc8470">RFC 8470</a>
	 */
	public static final int TOO_EARLY_VALUE = 425;

	/**
	 * {@code 426 Upgrade Required}.
	 * @see <a href="https://tools.ietf.org/html/rfc2817#section-6">Upgrading to TLS Within HTTP/1.1</a>
	 */
	public static final HttpStatus UPGRADE_REQUIRED;

	/**
	 * The integer equivalent of {@link #UPGRADE_REQUIRED}.
	 * @see <a href="https://tools.ietf.org/html/rfc2817#section-6">Upgrading to TLS Within HTTP/1.1</a>
	 */
	public static final int UPGRADE_REQUIRED_VALUE = 426;

	/**
	 * {@code 428 Precondition Required}.
	 * @see <a href="https://tools.ietf.org/html/rfc6585#section-3">Additional HTTP Status Codes</a>
	 */
	public static final HttpStatus PRECONDITION_REQUIRED;

	/**
	 * The integer equivalent of {@link #PRECONDITION_REQUIRED}.
	 * @see <a href="https://tools.ietf.org/html/rfc6585#section-3">Additional HTTP Status Codes</a>
	 */
	public static final int PRECONDITION_REQUIRED_VALUE = 428;

	/**
	 * {@code 429 Too Many Requests}.
	 * @see <a href="https://tools.ietf.org/html/rfc6585#section-4">Additional HTTP Status Codes</a>
	 */
	public static final HttpStatus TOO_MANY_REQUESTS;

	/**
	 * The integer equivalent of {@link #TOO_MANY_REQUESTS}.
	 * @see <a href="https://tools.ietf.org/html/rfc6585#section-4">Additional HTTP Status Codes</a>
	 */
	public static final int TOO_MANY_REQUESTS_VALUE = 429;

	/**
	 * {@code 431 Request Header Fields Too Large}.
	 * @see <a href="https://tools.ietf.org/html/rfc6585#section-5">Additional HTTP Status Codes</a>
	 */
	public static final HttpStatus REQUEST_HEADER_FIELDS_TOO_LARGE;

	/**
	 * {@code 431 Request Header Fields Too Large}.
	 * @see <a href="https://tools.ietf.org/html/rfc6585#section-5">Additional HTTP Status Codes</a>
	 */
	public static final int REQUEST_HEADER_FIELDS_TOO_LARGE_VALUE = 431;

	/**
	 * {@code 451 Unavailable For Legal Reasons}.
	 * @see <a href="https://tools.ietf.org/html/draft-ietf-httpbis-legally-restricted-status-04">
	 * An HTTP Status Code to Report Legal Obstacles</a>
	 * @since 4.3
	 */
	public static final HttpStatus UNAVAILABLE_FOR_LEGAL_REASONS;

	/**
	 * {@code 451 Unavailable For Legal Reasons}.
	 * @see <a href="https://tools.ietf.org/html/draft-ietf-httpbis-legally-restricted-status-04">
	 * An HTTP Status Code to Report Legal Obstacles</a>
	 * @since 4.3
	 */
	public static final int UNAVAILABLE_FOR_LEGAL_REASONS_VALUE = 451;


	// --- 5xx Server Error ---

	/**
	 * {@code 500 Internal Server Error}.
	 * @see <a href="https://tools.ietf.org/html/rfc7231#section-6.6.1">HTTP/1.1: Semantics and Content, section 6.6.1</a>
	 */
	public static final HttpStatus INTERNAL_SERVER_ERROR;

	/**
	 * The integer equivalent of {@link #INTERNAL_SERVER_ERROR}.
	 * @see <a href="https://tools.ietf.org/html/rfc7231#section-6.6.1">HTTP/1.1: Semantics and Content, section 6.6.1</a>
	 */
	public static final int INTERNAL_SERVER_ERROR_VALUE = 500;

	/**
	 * {@code 501 Not Implemented}.
	 * @see <a href="https://tools.ietf.org/html/rfc7231#section-6.6.2">HTTP/1.1: Semantics and Content, section 6.6.2</a>
	 */
	public static final HttpStatus NOT_IMPLEMENTED;

	/**
	 * The integer equivalent of {@link #NOT_IMPLEMENTED}.
	 * {@code 501 Not Implemented}.
	 * @see <a href="https://tools.ietf.org/html/rfc7231#section-6.6.2">HTTP/1.1: Semantics and Content, section 6.6.2</a>
	 */
	public static final int NOT_IMPLEMENTED_VALUE = 501;

	/**
	 * {@code 502 Bad Gateway}.
	 * @see <a href="https://tools.ietf.org/html/rfc7231#section-6.6.3">HTTP/1.1: Semantics and Content, section 6.6.3</a>
	 */
	public static final HttpStatus BAD_GATEWAY;

	/**
	 * The integer equivalent of {@link #BAD_GATEWAY}.
	 * {@code 502 Bad Gateway}.
	 * @see <a href="https://tools.ietf.org/html/rfc7231#section-6.6.3">HTTP/1.1: Semantics and Content, section 6.6.3</a>
	 */
	public static final int BAD_GATEWAY_VALUE = 502;

	/**
	 * {@code 503 Service Unavailable}.
	 * @see <a href="https://tools.ietf.org/html/rfc7231#section-6.6.4">HTTP/1.1: Semantics and Content, section 6.6.4</a>
	 */
	public static final HttpStatus SERVICE_UNAVAILABLE;

	/**
	 * The integer equivalent of {@link #SERVICE_UNAVAILABLE}.
	 * @see <a href="https://tools.ietf.org/html/rfc7231#section-6.6.4">HTTP/1.1: Semantics and Content, section 6.6.4</a>
	 */
	public static final int SERVICE_UNAVAILABLE_VALUE = 503;

	/**
	 * {@code 504 Gateway Timeout}.
	 * @see <a href="https://tools.ietf.org/html/rfc7231#section-6.6.5">HTTP/1.1: Semantics and Content, section 6.6.5</a>
	 */
	public static final HttpStatus GATEWAY_TIMEOUT;

	/**
	 * The integer equivalent of {@link #GATEWAY_TIMEOUT}.
	 * @see <a href="https://tools.ietf.org/html/rfc7231#section-6.6.5">HTTP/1.1: Semantics and Content, section 6.6.5</a>
	 */
	public static final int GATEWAY_TIMEOUT_VALUE = 504;

	/**
	 * {@code 505 HTTP Version Not Supported}.
	 * @see <a href="https://tools.ietf.org/html/rfc7231#section-6.6.6">HTTP/1.1: Semantics and Content, section 6.6.6</a>
	 */
	public static final HttpStatus HTTP_VERSION_NOT_SUPPORTED;

	/**
	 * The integer equivalent of {@link #HTTP_VERSION_NOT_SUPPORTED}.
	 * @see <a href="https://tools.ietf.org/html/rfc7231#section-6.6.6">HTTP/1.1: Semantics and Content, section 6.6.6</a>
	 */
	public static final int HTTP_VERSION_NOT_SUPPORTED_VALUE = 505;

	/**
	 * {@code 506 Variant Also Negotiates}
	 * @see <a href="https://tools.ietf.org/html/rfc2295#section-8.1">Transparent Content Negotiation</a>
	 */
	public static final HttpStatus VARIANT_ALSO_NEGOTIATES;

	/**
	 * The integer equivalent of {@link #VARIANT_ALSO_NEGOTIATES}.
	 * @see <a href="https://tools.ietf.org/html/rfc2295#section-8.1">Transparent Content Negotiation</a>
	 */
	public static final int VARIANT_ALSO_NEGOTIATES_VALUE = 506;

	/**
	 * {@code 507 Insufficient Storage}
	 * @see <a href="https://tools.ietf.org/html/rfc4918#section-11.5">WebDAV</a>
	 */
	public static final HttpStatus INSUFFICIENT_STORAGE;

	/**
	 * The integer equivalent of {@link #INSUFFICIENT_STORAGE}.
	 * @see <a href="https://tools.ietf.org/html/rfc4918#section-11.5">WebDAV</a>
	 */
	public static final int INSUFFICIENT_STORAGE_VALUE = 507;

	/**
	 * {@code 508 Loop Detected}
	 * @see <a href="https://tools.ietf.org/html/rfc5842#section-7.2">WebDAV Binding Extensions</a>
 	 */
	public static final HttpStatus LOOP_DETECTED;

	/**
	 * The integer equivalent of {@link #LOOP_DETECTED}.
	 * @see <a href="https://tools.ietf.org/html/rfc5842#section-7.2">WebDAV Binding Extensions</a>
 	 */
	public static final int LOOP_DETECTED_VALUE = 508;

	/**
	 * {@code 509 Bandwidth Limit Exceeded}
 	 */
	public static final HttpStatus BANDWIDTH_LIMIT_EXCEEDED;

	/**
	 * The integer equivalent of {@link #BANDWIDTH_LIMIT_EXCEEDED}.
 	 */
	public static final int BANDWIDTH_LIMIT_EXCEEDED_VALUE = 509;

	/**
	 * {@code 510 Not Extended}
	 * @see <a href="https://tools.ietf.org/html/rfc2774#section-7">HTTP Extension Framework</a>
	 */
	public static final HttpStatus NOT_EXTENDED;

	/**
	 * The integer equivalent of {@link #NOT_EXTENDED}.
	 * @see <a href="https://tools.ietf.org/html/rfc2774#section-7">HTTP Extension Framework</a>
	 */
	public static final int NOT_EXTENDED_VALUE = 510;

	/**
	 * {@code 511 Network Authentication Required}.
	 * @see <a href="https://tools.ietf.org/html/rfc6585#section-6">Additional HTTP Status Codes</a>
	 */
	public static final HttpStatus NETWORK_AUTHENTICATION_REQUIRED;

	/**
	 * {@code 511 Network Authentication Required}.
	 * @see <a href="https://tools.ietf.org/html/rfc6585#section-6">Additional HTTP Status Codes</a>
	 */
	public static final int NETWORK_AUTHENTICATION_REQUIRED_VALUE = 511;


	private static final long serialVersionUID = 7017664779360718111L;

	private static HttpStatus[] values;

	private static final ConcurrentLruCache<Integer, HttpStatus> cachedCodes =
			new ConcurrentLruCache<>(100, HttpStatus::new);


	static {
		CONTINUE = cachedCodes.get(CONTINUE_VALUE);
		SWITCHING_PROTOCOLS = cachedCodes.get(SWITCHING_PROTOCOLS_VALUE);
		PROCESSING = cachedCodes.get(PROCESSING_VALUE);
		CHECKPOINT = cachedCodes.get(CHECKPOINT_VALUE);

		OK = cachedCodes.get(OK_VALUE);
		CREATED = cachedCodes.get(CREATED_VALUE);
		ACCEPTED = cachedCodes.get(ACCEPTED_VALUE);
		NON_AUTHORITATIVE_INFORMATION = cachedCodes.get(NON_AUTHORITATIVE_INFORMATION_VALUE);
		NO_CONTENT = cachedCodes.get(NO_CONTENT_VALUE);
		RESET_CONTENT = cachedCodes.get(RESET_CONTENT_VALUE);
		PARTIAL_CONTENT = cachedCodes.get(PARTIAL_CONTENT_VALUE);
		MULTI_STATUS = cachedCodes.get(MULTI_STATUS_VALUE);
		ALREADY_REPORTED = cachedCodes.get(ALREADY_REPORTED_VALUE);
		IM_USED = cachedCodes.get(IM_USED_VALUE);

		MULTIPLE_CHOICES = cachedCodes.get(MULTIPLE_CHOICES_VALUE);
		MOVED_PERMANENTLY = cachedCodes.get(MOVED_PERMANENTLY_VALUE);
		FOUND = cachedCodes.get(FOUND_VALUE);
		SEE_OTHER = cachedCodes.get(SEE_OTHER_VALUE);
		NOT_MODIFIED = cachedCodes.get(NOT_MODIFIED_VALUE);
		TEMPORARY_REDIRECT = cachedCodes.get(TEMPORARY_REDIRECT_VALUE);
		PERMANENT_REDIRECT = cachedCodes.get(PERMANENT_REDIRECT_VALUE);

		BAD_REQUEST = cachedCodes.get(BAD_REQUEST_VALUE);
		UNAUTHORIZED = cachedCodes.get(UNAUTHORIZED_VALUE);
		PAYMENT_REQUIRED = cachedCodes.get(PAYMENT_REQUIRED_VALUE);
		FORBIDDEN = cachedCodes.get(FORBIDDEN_VALUE);
		NOT_FOUND = cachedCodes.get(NOT_FOUND_VALUE);
		METHOD_NOT_ALLOWED = cachedCodes.get(METHOD_NOT_ALLOWED_VALUE);
		NOT_ACCEPTABLE = cachedCodes.get(NOT_ACCEPTABLE_VALUE);
		PROXY_AUTHENTICATION_REQUIRED = cachedCodes.get(PROXY_AUTHENTICATION_REQUIRED_VALUE);
		REQUEST_TIMEOUT = cachedCodes.get(REQUEST_TIMEOUT_VALUE);
		CONFLICT = cachedCodes.get(CONFLICT_VALUE);
		GONE = cachedCodes.get(GONE_VALUE);
		LENGTH_REQUIRED = cachedCodes.get(LENGTH_REQUIRED_VALUE);
		PRECONDITION_FAILED = cachedCodes.get(PRECONDITION_FAILED_VALUE);
		PAYLOAD_TOO_LARGE = cachedCodes.get(PAYLOAD_TOO_LARGE_VALUE);
		URI_TOO_LONG = cachedCodes.get(URI_TOO_LONG_VALUE);
		UNSUPPORTED_MEDIA_TYPE = cachedCodes.get(UNSUPPORTED_MEDIA_TYPE_VALUE);
		REQUESTED_RANGE_NOT_SATISFIABLE = cachedCodes.get(REQUESTED_RANGE_NOT_SATISFIABLE_VALUE);
		EXPECTATION_FAILED = cachedCodes.get(EXPECTATION_FAILED_VALUE);
		I_AM_A_TEAPOT = cachedCodes.get(I_AM_A_TEAPOT_VALUE);
		UNPROCESSABLE_ENTITY = cachedCodes.get(UNPROCESSABLE_ENTITY_VALUE);
		LOCKED = cachedCodes.get(LOCKED_VALUE);
		FAILED_DEPENDENCY = cachedCodes.get(FAILED_DEPENDENCY_VALUE);
		TOO_EARLY = cachedCodes.get(TOO_EARLY_VALUE);
		UPGRADE_REQUIRED = cachedCodes.get(UPGRADE_REQUIRED_VALUE);
		PRECONDITION_REQUIRED = cachedCodes.get(PRECONDITION_REQUIRED_VALUE);
		TOO_MANY_REQUESTS = cachedCodes.get(TOO_MANY_REQUESTS_VALUE);
		REQUEST_HEADER_FIELDS_TOO_LARGE = cachedCodes.get(REQUEST_HEADER_FIELDS_TOO_LARGE_VALUE);
		UNAVAILABLE_FOR_LEGAL_REASONS = cachedCodes.get(UNAVAILABLE_FOR_LEGAL_REASONS_VALUE);

		INTERNAL_SERVER_ERROR = cachedCodes.get(INTERNAL_SERVER_ERROR_VALUE);
		NOT_IMPLEMENTED = cachedCodes.get(NOT_IMPLEMENTED_VALUE);
		BAD_GATEWAY = cachedCodes.get(BAD_GATEWAY_VALUE);
		SERVICE_UNAVAILABLE = cachedCodes.get(SERVICE_UNAVAILABLE_VALUE);
		GATEWAY_TIMEOUT = cachedCodes.get(GATEWAY_TIMEOUT_VALUE);
		HTTP_VERSION_NOT_SUPPORTED = cachedCodes.get(HTTP_VERSION_NOT_SUPPORTED_VALUE);
		VARIANT_ALSO_NEGOTIATES = cachedCodes.get(VARIANT_ALSO_NEGOTIATES_VALUE);
		INSUFFICIENT_STORAGE = cachedCodes.get(INSUFFICIENT_STORAGE_VALUE);
		LOOP_DETECTED = cachedCodes.get(LOOP_DETECTED_VALUE);
		BANDWIDTH_LIMIT_EXCEEDED = cachedCodes.get(BANDWIDTH_LIMIT_EXCEEDED_VALUE);
		NOT_EXTENDED = cachedCodes.get(NOT_EXTENDED_VALUE);
		NETWORK_AUTHENTICATION_REQUIRED = cachedCodes.get(NETWORK_AUTHENTICATION_REQUIRED_VALUE);
	}

	private final int value;


	private HttpStatus(int value) {
		this.value = value;
	}


	public static HttpStatus[] values() {
		Collection<HttpStatus> cachedValues = cachedCodes.cachedValues();
		return cachedValues.toArray(new HttpStatus[cachedValues.size()]);
	}

	/**
	 * Return an {@code HttpStatus} object for the given integer value.
	 * @param code the status code as integer
	 * @return the corresponding {@code HttpStatus}
	 * @throws IllegalArgumentException if {@code code} is not a three-digit
	 * positive number
	 */
	public static HttpStatus valueOf(int code) {
		Assert.isTrue(code >= 100 && code <= 999, "Code '" + code + "' should be a thee-digit positive integer");

		return cachedCodes.get(code);
	}
	
	/**
	 * Resolve the given status code to an {@code HttpStatus}, if possible.
	 * @param statusCode the HTTP status code (potentially non-standard)
	 * @return the corresponding {@code HttpStatus}, or {@code null} if not found
	 * @since 5.0
	 * @deprecated in favor of {@link #valueOf(int)} 
	 */
	@Nullable
	@Deprecated
	public static HttpStatus resolve(int statusCode) {
		return valueOf(statusCode);
	}


	/**
	 * Return the integer value of this status code.
	 */
	public int value() {
		return this.value;
	}

	/**
	 * Return the HTTP status series of this status code.
	 * @see HttpStatus.Series
	 */
	public Series series() {
		return switch (this.value / 100) {
			case 1 -> Series.INFORMATIONAL;
			case 2 -> Series.SUCCESSFUL;
			case 3 -> Series.REDIRECTION;
			case 4 -> Series.CLIENT_ERROR;
			case 5 -> Series.SERVER_ERROR;
			default -> throw new IllegalStateException("Status code " + this.value + " is not in the 1xx to 5xx range");
		};
	}

	/**
	 * Return the reason phrase of this status code.
	 */
	public String getReasonPhrase() {
		return switch (this.value) {
			// 1xx
			case CONTINUE_VALUE -> "Continue";
			case SWITCHING_PROTOCOLS_VALUE -> "Switching Protocols";
			case PROCESSING_VALUE -> "Processing";
			case CHECKPOINT_VALUE -> "Checkpoint";
			// 2xx
			case OK_VALUE -> "OK";
			case CREATED_VALUE -> "Created";
			case ACCEPTED_VALUE -> "Accepted";
			case NON_AUTHORITATIVE_INFORMATION_VALUE -> "Non-Authoritative Information";
			case NO_CONTENT_VALUE -> "No Content";
			case RESET_CONTENT_VALUE -> "Reset Content";
			case PARTIAL_CONTENT_VALUE -> "Partial Content";
			case MULTI_STATUS_VALUE -> "Multi-Status";
			case ALREADY_REPORTED_VALUE -> "Already Reported";
			case IM_USED_VALUE -> "IM Used";
			// 3xx
			case MULTIPLE_CHOICES_VALUE -> "Multiple Choices";
			case MOVED_PERMANENTLY_VALUE -> "Moved Permanently";
			case FOUND_VALUE -> "Found";
			case SEE_OTHER_VALUE -> "See Other";
			case NOT_MODIFIED_VALUE -> "Not Modified";
			case TEMPORARY_REDIRECT_VALUE -> "Temporary Redirect";
			case PERMANENT_REDIRECT_VALUE -> "Permanent Redirect";
			// 4xx
			case BAD_REQUEST_VALUE -> "Bad Request";
			case UNAUTHORIZED_VALUE -> "Unauthorized";
			case PAYMENT_REQUIRED_VALUE -> "Payment Required";
			case FORBIDDEN_VALUE -> "Forbidden";
			case NOT_FOUND_VALUE -> "Not Found";
			case METHOD_NOT_ALLOWED_VALUE -> "Method Not Allowed";
			case NOT_ACCEPTABLE_VALUE -> "Not Acceptable";
			case PROXY_AUTHENTICATION_REQUIRED_VALUE -> "Proxy Authentication Required";
			case REQUEST_TIMEOUT_VALUE -> "Request Timeout";
			case CONFLICT_VALUE -> "Conflict";
			case GONE_VALUE -> "Gone";
			case LENGTH_REQUIRED_VALUE -> "Length Required";
			case PRECONDITION_FAILED_VALUE -> "Precondition Failed";
			case PAYLOAD_TOO_LARGE_VALUE -> "Payload Too Large";
			case URI_TOO_LONG_VALUE -> "URI Too Long";
			case UNSUPPORTED_MEDIA_TYPE_VALUE -> "Unsupported Media Type";
			case REQUESTED_RANGE_NOT_SATISFIABLE_VALUE -> "Requested range not satisfiable";
			case EXPECTATION_FAILED_VALUE -> "Expectation Failed";
			case I_AM_A_TEAPOT_VALUE -> "I am a teapot";
			case UNPROCESSABLE_ENTITY_VALUE -> "Unprocessable Entity";
			case LOCKED_VALUE -> "Locked";
			case FAILED_DEPENDENCY_VALUE -> "Failed Dependency";
			case TOO_EARLY_VALUE -> "Too Early";
			case UPGRADE_REQUIRED_VALUE -> "Upgrade Required";
			case PRECONDITION_REQUIRED_VALUE -> "Precondition Required";
			case TOO_MANY_REQUESTS_VALUE -> "Too Many Requests";
			case REQUEST_HEADER_FIELDS_TOO_LARGE_VALUE -> "Request Header Fields Too Large";
			case UNAVAILABLE_FOR_LEGAL_REASONS_VALUE -> "Unavailable For Legal Reasons";
			// 5xx
			case INTERNAL_SERVER_ERROR_VALUE -> "Internal Server Error";
			case NOT_IMPLEMENTED_VALUE -> "Not Implemented";
			case BAD_GATEWAY_VALUE -> "Bad Gateway";
			case SERVICE_UNAVAILABLE_VALUE -> "Service Unavailable";
			case GATEWAY_TIMEOUT_VALUE -> "Gateway Timeout";
			case HTTP_VERSION_NOT_SUPPORTED_VALUE -> "HTTP Version not supported";
			case VARIANT_ALSO_NEGOTIATES_VALUE -> "Variant Also Negotiates";
			case INSUFFICIENT_STORAGE_VALUE -> "Insufficient Storage";
			case LOOP_DETECTED_VALUE -> "Loop Detected";
			case BANDWIDTH_LIMIT_EXCEEDED_VALUE -> "Bandwidth Limit Exceeded";
			case NOT_EXTENDED_VALUE -> "Not Extended";
			case NETWORK_AUTHENTICATION_REQUIRED_VALUE -> "Network Authentication Required";

			default -> "";
		};
	}

	/**
	 * Whether this status code is in the HTTP series
	 * {@link org.springframework.http.HttpStatus.Series#INFORMATIONAL}.
	 * <p>This is a shortcut for checking the value of {@link #series()}.
	 * @since 4.0
	 * @see #series()
	 */
	public boolean is1xxInformational() {
		return this.value / 100 == 1;
	}

	/**
	 * Whether this status code is in the HTTP series
	 * {@link org.springframework.http.HttpStatus.Series#SUCCESSFUL}.
	 * <p>This is a shortcut for checking the value of {@link #series()}.
	 * @since 4.0
	 * @see #series()
	 */
	public boolean is2xxSuccessful() {
		return this.value / 100 == 2;
	}

	/**
	 * Whether this status code is in the HTTP series
	 * {@link org.springframework.http.HttpStatus.Series#REDIRECTION}.
	 * <p>This is a shortcut for checking the value of {@link #series()}.
	 * @since 4.0
	 * @see #series()
	 */
	public boolean is3xxRedirection() {
		return this.value / 100 == 3;
	}

	/**
	 * Whether this status code is in the HTTP series
	 * {@link org.springframework.http.HttpStatus.Series#CLIENT_ERROR}.
	 * <p>This is a shortcut for checking the value of {@link #series()}.
	 * @since 4.0
	 * @see #series()
	 */
	public boolean is4xxClientError() {
		return this.value / 100 == 4;
	}

	/**
	 * Whether this status code is in the HTTP series
	 * {@link org.springframework.http.HttpStatus.Series#SERVER_ERROR}.
	 * <p>This is a shortcut for checking the value of {@link #series()}.
	 * @since 4.0
	 * @see #series()
	 */
	public boolean is5xxServerError() {
		return this.value / 100 == 5;
	}

	/**
	 * Whether this status code is in the HTTP series
	 * {@link org.springframework.http.HttpStatus.Series#CLIENT_ERROR} or
	 * {@link org.springframework.http.HttpStatus.Series#SERVER_ERROR}.
	 * <p>This is a shortcut for checking the value of {@link #series()}.
	 * @since 5.0
	 * @see #is4xxClientError()
	 * @see #is5xxServerError()
	 */
	public boolean isError() {
		return (is4xxClientError() || is5xxServerError());
	}

	@Override
	public int compareTo(@NotNull HttpStatus o) {
		return Integer.compare(this.value, o.value);
	}

	@Override
	public int hashCode() {
		return this.value;
	}

	@Override
	public boolean equals(Object o) {
		if (this == o) {
			return true;
		}
		else if (o instanceof HttpStatus other) {
			return this.value == other.value;
		}
		return false;
	}

	/**
	 * Return a string representation of this status code.
	 */
	@Override
	public String toString() {
		String reasonPhrase = getReasonPhrase();
		if (StringUtils.hasLength(reasonPhrase)) {
			return this.value + " " + reasonPhrase;
		}
		else {
			return Integer.toString(this.value);
		}
	}


	/**
	 * Enumeration of HTTP status series.
	 * <p>Retrievable via {@link HttpStatus#series()}.
	 */
	public enum Series {

		INFORMATIONAL(1),
		SUCCESSFUL(2),
		REDIRECTION(3),
		CLIENT_ERROR(4),
		SERVER_ERROR(5);

		private final int value;

		Series(int value) {
			this.value = value;
		}

		/**
		 * Return the integer value of this status series. Ranges from 1 to 5.
		 */
		public int value() {
			return this.value;
		}

		/**
		 * Return the {@code Series} enum constant for the supplied {@code HttpStatus}.
		 * @param status a standard HTTP status enum constant
		 * @return the {@code Series} enum constant for the supplied {@code HttpStatus}
		 * @deprecated as of 5.3, in favor of invoking {@link HttpStatus#series()} directly
		 */
		@Deprecated
		public static Series valueOf(HttpStatus status) {
			return status.series();
		}

		/**
		 * Return the {@code Series} enum constant for the supplied status code.
		 * @param statusCode the HTTP status code (potentially non-standard)
		 * @return the {@code Series} enum constant for the supplied status code
		 * @throws IllegalArgumentException if this enum has no corresponding constant
		 */
		public static Series valueOf(int statusCode) {
			Series series = resolve(statusCode);
			if (series == null) {
				throw new IllegalArgumentException("No matching constant for [" + statusCode + "]");
			}
			return series;
		}

		/**
		 * Resolve the given status code to an {@code HttpStatus.Series}, if possible.
		 * @param statusCode the HTTP status code (potentially non-standard)
		 * @return the corresponding {@code Series}, or {@code null} if not found
		 * @since 5.1.3
		 */
		@Nullable
		public static Series resolve(int statusCode) {
			int seriesCode = statusCode / 100;
			for (Series series : values()) {
				if (series.value == seriesCode) {
					return series;
				}
			}
			return null;
		}
	}

}
