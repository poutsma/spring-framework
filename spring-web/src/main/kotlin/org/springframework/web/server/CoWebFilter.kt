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

package org.springframework.web.server

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.reactor.awaitSingle
import kotlinx.coroutines.reactor.mono
import reactor.core.publisher.Mono

/**
 * @author Arjen Poutsma
 */
abstract class CoWebFilter : WebFilter {

	final override fun filter(exchange: ServerWebExchange, chain: WebFilterChain): Mono<Void> {
		return mono(Dispatchers.Unconfined) {
			filterInternal(exchange, CoWebFilterChainImpl(chain))
		}.cast(Void.TYPE)
	}

	abstract suspend fun filterInternal(exchange: ServerWebExchange, chain: CoWebFilterChain)

}

interface CoWebFilterChain {

	suspend fun filter(exchange: ServerWebExchange)

}

private class CoWebFilterChainImpl(val chain: WebFilterChain) : CoWebFilterChain {
	override suspend fun filter(exchange: ServerWebExchange) {
		return chain.filter(exchange).cast(Unit.javaClass).awaitSingle()
	}

}