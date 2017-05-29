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

package org.springframework.web.client;

import java.io.IOException;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import org.springframework.beans.factory.InitializingBean;
import org.springframework.http.HttpStatus;
import org.springframework.http.client.ClientHttpResponse;
import org.springframework.http.converter.HttpMessageConverter;
import org.springframework.util.Assert;

/**
 * Implementation of {@link ResponseErrorHandler} that uses {@link HttpMessageConverter} to
 * convert HTTP error responses to {@link RestClientException}.
 * <p>To use this error handler, you must specify a
 * {@linkplain #setStatusMapping(Map) status mapping} and/or a
 * {@linkplain #setSeriesMapping(Map) series mapping}. If either of these mappings has a match
 * for the {@linkplain ClientHttpResponse#getStatusCode() status code} of a given
 * {@code ClientHttpResponse}, {@link #hasError(ClientHttpResponse)} will return
 * {@code true} and {@link #handleError(ClientHttpResponse)} will attempt to use the
 * {@linkplain #setMessageConverters(List) configured message converters} to convert the response
 * into the mapped subclass of {@link RestClientException}. Note that the
 * {@linkplain #setStatusMapping(Map) status mapping} takes precedence over
 * {@linkplain #setSeriesMapping(Map) series mapping}.
 * <p>If there is no match, this error handler will default to the behavior of
 * {@link DefaultResponseErrorHandler}.
 *
 * @author Simon Galperin
 * @author Arjen Poutsma
 * @see RestTemplate#setErrorHandler(ResponseErrorHandler)
 * @since 5.0
 */
public class ExtractingResponseErrorHandler extends DefaultResponseErrorHandler
		implements InitializingBean {

	private List<HttpMessageConverter<?>> messageConverters;

	private Map<HttpStatus, Class<? extends RestClientException>> statusMapping;

	private Map<HttpStatus.Series, Class<? extends RestClientException>> seriesMapping;

	/**
	 * Create a new, empty {@code ExtractingResponseErrorHandler}.
	 * <p>Note that {@link #setMessageConverters(List)} must be called when using this constructor.
	 */
	public ExtractingResponseErrorHandler() {
	}

	/**
	 * Create a new {@code ExtractingResponseErrorHandler} with the given
	 * {@link HttpMessageConverter} instances.
	 * @param messageConverters the message converters to use
	 */
	public ExtractingResponseErrorHandler(List<HttpMessageConverter<?>> messageConverters) {
		setMessageConverters(messageConverters);
	}

	/**
	 * Sets the message converters to use by this extractor.
	 */
	public void setMessageConverters(List<HttpMessageConverter<?>> messageConverters) {
		this.messageConverters = messageConverters;
	}

	/**
	 * Set the mapping from HTTP status code to {@code RestClientException} subclass.
	 * If this mapping has a match
	 * for the {@linkplain ClientHttpResponse#getStatusCode() status code} of a given
	 * {@code ClientHttpResponse}, {@link #hasError(ClientHttpResponse)} will return
	 * {@code true} and {@link #handleError(ClientHttpResponse)} will attempt to use the
	 * {@linkplain #setMessageConverters(List) configured message converters} to convert the
	 * response into the mapped subclass of {@link RestClientException}.
	 */
	public void setStatusMapping(
			Map<HttpStatus, Class<? extends RestClientException>> statusMapping) {
		this.statusMapping = statusMapping;
	}

	/**
	 * Set the mapping from HTTP status series to {@code RestClientException} subclass.
	 * If this mapping has a match
	 * for the {@linkplain ClientHttpResponse#getStatusCode() status code} of a given
	 * {@code ClientHttpResponse}, {@link #hasError(ClientHttpResponse)} will return
	 * {@code true} and {@link #handleError(ClientHttpResponse)} will attempt to use the
	 * {@linkplain #setMessageConverters(List) configured message converters} to convert the
	 * response into the mapped subclass of {@link RestClientException}.
	 */
	public void setSeriesMapping(
			Map<HttpStatus.Series, Class<? extends RestClientException>> seriesMapping) {
		this.seriesMapping = seriesMapping;
	}

	@Override
	public void afterPropertiesSet() throws Exception {
		Assert.notEmpty(this.messageConverters, "'messageConverters' is required");
		if (this.statusMapping == null) {
			this.statusMapping = Collections.emptyMap();
		}
		if (this.seriesMapping == null) {
			this.seriesMapping = Collections.emptyMap();
		}
	}

	@Override
	protected boolean hasError(HttpStatus statusCode) {
		return this.statusMapping.containsKey(statusCode) ||
				this.seriesMapping.containsKey(statusCode.series()) ||
						super.hasError(statusCode);
	}

	@Override
	public void handleError(ClientHttpResponse response) throws IOException {
		HttpStatus statusCode = response.getStatusCode();
		Class<? extends RestClientException> exceptionClass = this.statusMapping.get(statusCode);
		if (exceptionClass == null) {
			exceptionClass = this.seriesMapping.get(statusCode.series());
		}
		if (exceptionClass != null) {
			HttpMessageConverterExtractor<? extends RestClientException> extractor =
					new HttpMessageConverterExtractor<>(exceptionClass, this.messageConverters);
			RestClientException exception = extractor.extractData(response);
			if (exception != null) {
				throw exception;
			}

		}
		super.handleError(response);
	}
}
