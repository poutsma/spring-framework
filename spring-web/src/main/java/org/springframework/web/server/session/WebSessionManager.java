/*
 * Copyright 2002-2016 the original author or authors.
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

package org.springframework.web.server.session;

import reactor.core.publisher.Mono;

import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebSession;

/**
 * Main class for access to the {@link WebSession} for an HTTP request.
 *
 * @author Rossen Stoyanchev
 * @since 5.0
 * @see WebSessionIdResolver
 * @see WebSessionStore
 */
public interface WebSessionManager {

	/**
	 * Return the {@link WebSession} associated with the given exchange, or if
	 * the exchange does not have a (unexpired) session, create one.
	 * @param exchange the current exchange
	 * @return promise for the WebSession that will not be empty
	 */
	default Mono<WebSession> getSession(ServerWebExchange exchange) {
		return getSession(exchange, true);
	}

	/**
	 * Return the {@link WebSession} associated with the given exchange or, if
	 * there is no current, unexpired session and {@code create} is {@code true},
	 * return a new session.
	 *
	 * <p>If create is {@code false} and the exchange has no valid
	 * {@code WebSession}, this method returns an empty {@code Mono}.
	 * @param exchange the current exchange
	 * @param create {@code true} to create a new session if necessary;
	 * {@code false} to return an empty mono if there is no current session
	 * @return promise for the WebSession, or an empty promise if {@code create}
	 * is {@code false} and the exchange has no valid session
	 */
	Mono<WebSession> getSession(ServerWebExchange exchange, boolean create);

}
