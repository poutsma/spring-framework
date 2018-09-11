/*
 * Copyright 2002-2018 the original author or authors.
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

import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.charset.Charset;
import java.time.ZonedDateTime;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import org.springframework.lang.Nullable;
import org.springframework.util.LinkedCaseInsensitiveMap;
import org.springframework.util.MultiValueMap;

/**
 * @author Arjen Poutsma
 */
class ReadOnlyHttpHeaders extends HttpHeaders {

	private static final long serialVersionUID = -3760451553230436519L;

	private static final long DEFAULT_CONTENT_LENGTH = Long.MIN_VALUE;

	private long contentLength = DEFAULT_CONTENT_LENGTH;

	@Nullable
	private MediaType contentType;

	@Nullable
	private String eTag;

	ReadOnlyHttpHeaders(HttpHeaders headers) {
		super(unmodifiableCaseInsensitiveMap(headers));
	}

	private static Map<String, List<String>> unmodifiableCaseInsensitiveMap(Map<String, List<String>> map) {
		Map<String, List<String>> result = new LinkedCaseInsensitiveMap<>(map.size(), Locale.ENGLISH);
		result.forEach((key, valueList) -> result.put(key, Collections.unmodifiableList(valueList)));
		return Collections.unmodifiableMap(result);
	}

	// caching methods

	@Override
	public long getContentLength() {
		if (this.contentLength == DEFAULT_CONTENT_LENGTH) {
			this.contentLength = super.getContentLength();
		}
		return this.contentLength;
	}

	@Nullable
	@Override
	public MediaType getContentType() {
		if (this.contentType == null) {
			this.contentType = super.getContentType();
		}
		return this.contentType;
	}



	@Nullable
	@Override
	public String getETag() {
		if (this.eTag == null) {
			this.eTag = super.getETag();
		}
		return this.eTag;
	}

	// unsupported operations

	@Override
	public void setAccept(List<MediaType> acceptableMediaTypes) {
		throw new UnsupportedOperationException();
	}

	@Override
	public void setAcceptLanguage(List<Locale.LanguageRange> languages) {
		throw new UnsupportedOperationException();
	}

	@Override
	public void setAcceptLanguageAsLocales(List<Locale> locales) {
		throw new UnsupportedOperationException();
	}

	@Override
	public void setAccessControlAllowCredentials(boolean allowCredentials) {
		throw new UnsupportedOperationException();
	}

	@Override
	public void setAccessControlAllowHeaders(List<String> allowedHeaders) {
		throw new UnsupportedOperationException();
	}

	@Override
	public void setAccessControlAllowMethods(List<HttpMethod> allowedMethods) {
		throw new UnsupportedOperationException();
	}

	@Override
	public void setAccessControlAllowOrigin(@Nullable String allowedOrigin) {
		throw new UnsupportedOperationException();
	}

	@Override
	public void setAccessControlExposeHeaders(List<String> exposedHeaders) {
		throw new UnsupportedOperationException();
	}

	@Override
	public void setAccessControlMaxAge(long maxAge) {
		throw new UnsupportedOperationException();
	}

	@Override
	public void setAccessControlRequestHeaders(List<String> requestHeaders) {
		throw new UnsupportedOperationException();
	}

	@Override
	public void setAccessControlRequestMethod(@Nullable HttpMethod requestMethod) {
		throw new UnsupportedOperationException();
	}

	@Override
	public void setAcceptCharset(List<Charset> acceptableCharsets) {
		throw new UnsupportedOperationException();
	}

	@Override
	public void setAllow(Set<HttpMethod> allowedMethods) {
		throw new UnsupportedOperationException();
	}

	@Override
	public void setBasicAuth(String username, String password) {
		throw new UnsupportedOperationException();
	}

	@Override
	public void setBasicAuth(String username, String password, @Nullable Charset charset) {
		throw new UnsupportedOperationException();
	}

	@Override
	public void setBearerAuth(String token) {
		throw new UnsupportedOperationException();
	}

	@Override
	public void setCacheControl(CacheControl cacheControl) {
		throw new UnsupportedOperationException();
	}

	@Override
	public void setCacheControl(@Nullable String cacheControl) {
		throw new UnsupportedOperationException();
	}

	@Override
	public void setConnection(String connection) {
		throw new UnsupportedOperationException();
	}

	@Override
	public void setConnection(List<String> connection) {
		throw new UnsupportedOperationException();
	}

	@Override
	public void setContentDispositionFormData(String name, @Nullable String filename) {
		throw new UnsupportedOperationException();
	}

	@Override
	public void setContentDisposition(ContentDisposition contentDisposition) {
		throw new UnsupportedOperationException();
	}

	@Override
	public void setContentLanguage(@Nullable Locale locale) {
		throw new UnsupportedOperationException();
	}

	@Override
	public void setContentLength(long contentLength) {
		throw new UnsupportedOperationException();
	}

	@Override
	public void setContentType(@Nullable MediaType mediaType) {
		throw new UnsupportedOperationException();
	}

	@Override
	public void setDate(long date) {
		throw new UnsupportedOperationException();
	}

	@Override
	public void setETag(@Nullable String etag) {
		throw new UnsupportedOperationException();
	}

	@Override
	public void setExpires(ZonedDateTime expires) {
		throw new UnsupportedOperationException();
	}

	@Override
	public void setExpires(long expires) {
		throw new UnsupportedOperationException();
	}

	@Override
	public void setHost(@Nullable InetSocketAddress host) {
		throw new UnsupportedOperationException();
	}

	@Override
	public void setIfMatch(String ifMatch) {
		throw new UnsupportedOperationException();
	}

	@Override
	public void setIfMatch(List<String> ifMatchList) {
		throw new UnsupportedOperationException();
	}

	@Override
	public void setIfModifiedSince(long ifModifiedSince) {
		throw new UnsupportedOperationException();
	}

	@Override
	public void setIfNoneMatch(String ifNoneMatch) {
		throw new UnsupportedOperationException();
	}

	@Override
	public void setIfNoneMatch(List<String> ifNoneMatchList) {
		throw new UnsupportedOperationException();
	}

	@Override
	public void setIfUnmodifiedSince(long ifUnmodifiedSince) {
		throw new UnsupportedOperationException();
	}

	@Override
	public void setLastModified(long lastModified) {
		throw new UnsupportedOperationException();
	}

	@Override
	public void setLocation(@Nullable URI location) {
		throw new UnsupportedOperationException();
	}

	@Override
	public void setOrigin(@Nullable String origin) {
		throw new UnsupportedOperationException();
	}

	@Override
	public void setPragma(@Nullable String pragma) {
		throw new UnsupportedOperationException();
	}

	@Override
	public void setRange(List<HttpRange> ranges) {
		throw new UnsupportedOperationException();
	}

	@Override
	public void setUpgrade(@Nullable String upgrade) {
		throw new UnsupportedOperationException();
	}

	@Override
	public void setVary(List<String> requestHeaders) {
		throw new UnsupportedOperationException();
	}

	@Override
	public void setZonedDateTime(String headerName, ZonedDateTime date) {
		throw new UnsupportedOperationException();
	}

	@Override
	public void setDate(String headerName, long date) {
		throw new UnsupportedOperationException();
	}

	@Override
	public void add(String headerName, @Nullable String headerValue) {
		throw new UnsupportedOperationException();
	}

	@Override
	public void addAll(String key, List<? extends String> values) {
		throw new UnsupportedOperationException();
	}

	@Override
	public void addAll(MultiValueMap<String, String> values) {
		throw new UnsupportedOperationException();
	}

	@Override
	public void set(String headerName, @Nullable String headerValue) {
		throw new UnsupportedOperationException();
	}

	@Override
	public void setAll(Map<String, String> values) {
		throw new UnsupportedOperationException();
	}

	@Override
	public List<String> put(String key, List<String> value) {
		throw new UnsupportedOperationException();
	}

	@Override
	public void putAll(Map<? extends String, ? extends List<String>> map) {
		throw new UnsupportedOperationException();
	}

	@Override
	public void clear() {
		throw new UnsupportedOperationException();
	}


}
