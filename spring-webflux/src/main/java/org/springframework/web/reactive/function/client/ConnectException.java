/*
 * Copyright 2002-2020 the original author or authors.
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

package org.springframework.web.reactive.function.client;

/**
 * Exception that signals that an error occurred while attempting to connect a
 * server.
 *
 * @author Arjen Poutsma
 * @since 5.3
 */
public class ConnectException extends WebClientException {

	private static final long serialVersionUID = -5139991985321385005L;


	/**
	 * Construct a new instance of {@code ConnectException} with the given
	 * message.
	 * @param msg the message
	 */
	public ConnectException(String msg) {
		super(msg);
	}

	/**
	 * Construct a new instance of {@code ConnectException} with the given
	 * message and exception.
	 * @param msg the message
	 * @param ex the exception
	 */
	public ConnectException(String msg, Throwable ex) {
		super(msg, ex);
	}
}
