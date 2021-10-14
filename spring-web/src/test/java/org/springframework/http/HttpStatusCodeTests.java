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

package org.springframework.http;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * @author Arjen Poutsma
 */
class HttpStatusCodeTests {

	@Test
	public void comparison() {
		HttpStatusCode code1 = HttpStatusCode.valueOf(600);
		HttpStatusCode code2 = HttpStatusCode.valueOf(600);
		HttpStatusCode code3 = HttpStatusCode.valueOf(700);

		assertThat(code1).isEqualTo(code2);
		assertThat(code1).isNotEqualTo(code3);

		assertThat(code1.hashCode()).isEqualTo(code2.hashCode());

		assertThat(code1.compareTo(code2)).isEqualTo(0);
		assertThat(code1.compareTo(code3)).isNotEqualTo(0);
	}

	@Test
	void valueOf() {
		HttpStatusCode notFound = HttpStatusCode.valueOf(404);
		assertThat(notFound).isSameAs(HttpStatusCode.NOT_FOUND);

		HttpStatusCode code = HttpStatusCode.valueOf(600);
		HttpStatusCode other = HttpStatusCode.valueOf(600);
		assertThat(code).isSameAs(other);
	}

	@Test
	void value() {
		HttpStatusCode code = HttpStatusCode.valueOf(600);
		assertThat(code.value()).isEqualTo(600);
	}

	@Test
	void is1xxInformational() {
		assertThat(HttpStatusCode.CONTINUE.is1xxInformational()).isTrue();
		assertThat(HttpStatusCode.SWITCHING_PROTOCOLS.is1xxInformational()).isTrue();
		assertThat(HttpStatusCode.PROCESSING.is1xxInformational()).isTrue();
		assertThat(HttpStatusCode.CHECKPOINT.is1xxInformational()).isTrue();

		assertThat(HttpStatusCode.OK.is1xxInformational()).isFalse();
	}

	@Test
	void is2xxSuccessful() {
		assertThat(HttpStatusCode.OK.is2xxSuccessful()).isTrue();
		assertThat(HttpStatusCode.CREATED.is2xxSuccessful()).isTrue();
		assertThat(HttpStatusCode.ACCEPTED.is2xxSuccessful()).isTrue();
		assertThat(HttpStatusCode.NON_AUTHORITATIVE_INFORMATION.is2xxSuccessful()).isTrue();
		assertThat(HttpStatusCode.NO_CONTENT.is2xxSuccessful()).isTrue();
		assertThat(HttpStatusCode.RESET_CONTENT.is2xxSuccessful()).isTrue();
		assertThat(HttpStatusCode.PARTIAL_CONTENT.is2xxSuccessful()).isTrue();
		assertThat(HttpStatusCode.MULTI_STATUS.is2xxSuccessful()).isTrue();
		assertThat(HttpStatusCode.ALREADY_REPORTED.is2xxSuccessful()).isTrue();
		assertThat(HttpStatusCode.IM_USED.is2xxSuccessful()).isTrue();

		assertThat(HttpStatusCode.MULTIPLE_CHOICES.is2xxSuccessful()).isFalse();
	}

	@Test
	void is3xxRedirection() {
		assertThat(HttpStatusCode.MULTIPLE_CHOICES.is3xxRedirection()).isTrue();
		assertThat(HttpStatusCode.MOVED_PERMANENTLY.is3xxRedirection()).isTrue();
		assertThat(HttpStatusCode.FOUND.is3xxRedirection()).isTrue();
		assertThat(HttpStatusCode.SEE_OTHER.is3xxRedirection()).isTrue();
		assertThat(HttpStatusCode.NOT_MODIFIED.is3xxRedirection()).isTrue();
		assertThat(HttpStatusCode.TEMPORARY_REDIRECT.is3xxRedirection()).isTrue();
		assertThat(HttpStatusCode.PERMANENT_REDIRECT.is3xxRedirection()).isTrue();

		assertThat(HttpStatusCode.BAD_REQUEST.is3xxRedirection()).isFalse();
	}

	@Test
	void is4xxClientError() {
		assertThat(HttpStatusCode.BAD_REQUEST.is4xxClientError()).isTrue();
		assertThat(HttpStatusCode.UNAUTHORIZED.is4xxClientError()).isTrue();
		assertThat(HttpStatusCode.PAYMENT_REQUIRED.is4xxClientError()).isTrue();
		assertThat(HttpStatusCode.FORBIDDEN.is4xxClientError()).isTrue();
		assertThat(HttpStatusCode.NOT_FOUND.is4xxClientError()).isTrue();
		assertThat(HttpStatusCode.METHOD_NOT_ALLOWED.is4xxClientError()).isTrue();
		assertThat(HttpStatusCode.NOT_ACCEPTABLE.is4xxClientError()).isTrue();
		assertThat(HttpStatusCode.PROXY_AUTHENTICATION_REQUIRED.is4xxClientError()).isTrue();
		assertThat(HttpStatusCode.REQUEST_TIMEOUT.is4xxClientError()).isTrue();
		assertThat(HttpStatusCode.CONFLICT.is4xxClientError()).isTrue();
		assertThat(HttpStatusCode.GONE.is4xxClientError()).isTrue();
		assertThat(HttpStatusCode.LENGTH_REQUIRED.is4xxClientError()).isTrue();
		assertThat(HttpStatusCode.PRECONDITION_FAILED.is4xxClientError()).isTrue();
		assertThat(HttpStatusCode.PAYLOAD_TOO_LARGE.is4xxClientError()).isTrue();
		assertThat(HttpStatusCode.URI_TOO_LONG.is4xxClientError()).isTrue();
		assertThat(HttpStatusCode.UNSUPPORTED_MEDIA_TYPE.is4xxClientError()).isTrue();
		assertThat(HttpStatusCode.REQUESTED_RANGE_NOT_SATISFIABLE.is4xxClientError()).isTrue();
		assertThat(HttpStatusCode.EXPECTATION_FAILED.is4xxClientError()).isTrue();
		assertThat(HttpStatusCode.I_AM_A_TEAPOT.is4xxClientError()).isTrue();
		assertThat(HttpStatusCode.UNPROCESSABLE_ENTITY.is4xxClientError()).isTrue();
		assertThat(HttpStatusCode.LOCKED.is4xxClientError()).isTrue();
		assertThat(HttpStatusCode.FAILED_DEPENDENCY.is4xxClientError()).isTrue();
		assertThat(HttpStatusCode.TOO_EARLY.is4xxClientError()).isTrue();
		assertThat(HttpStatusCode.UPGRADE_REQUIRED.is4xxClientError()).isTrue();
		assertThat(HttpStatusCode.PRECONDITION_REQUIRED.is4xxClientError()).isTrue();
		assertThat(HttpStatusCode.TOO_MANY_REQUESTS.is4xxClientError()).isTrue();
		assertThat(HttpStatusCode.REQUEST_HEADER_FIELDS_TOO_LARGE.is4xxClientError()).isTrue();
		assertThat(HttpStatusCode.UNAVAILABLE_FOR_LEGAL_REASONS.is4xxClientError()).isTrue();

		assertThat(HttpStatusCode.INTERNAL_SERVER_ERROR.is4xxClientError()).isFalse();
	}

	@Test
	void is5xxServerError() {
		assertThat(HttpStatusCode.INTERNAL_SERVER_ERROR.is5xxServerError()).isTrue();
		assertThat(HttpStatusCode.NOT_IMPLEMENTED.is5xxServerError()).isTrue();
		assertThat(HttpStatusCode.BAD_GATEWAY.is5xxServerError()).isTrue();
		assertThat(HttpStatusCode.SERVICE_UNAVAILABLE.is5xxServerError()).isTrue();
		assertThat(HttpStatusCode.GATEWAY_TIMEOUT.is5xxServerError()).isTrue();
		assertThat(HttpStatusCode.HTTP_VERSION_NOT_SUPPORTED.is5xxServerError()).isTrue();
		assertThat(HttpStatusCode.VARIANT_ALSO_NEGOTIATES.is5xxServerError()).isTrue();
		assertThat(HttpStatusCode.INSUFFICIENT_STORAGE.is5xxServerError()).isTrue();
		assertThat(HttpStatusCode.LOOP_DETECTED.is5xxServerError()).isTrue();
		assertThat(HttpStatusCode.BANDWIDTH_LIMIT_EXCEEDED.is5xxServerError()).isTrue();
		assertThat(HttpStatusCode.NOT_EXTENDED.is5xxServerError()).isTrue();
		assertThat(HttpStatusCode.NETWORK_AUTHENTICATION_REQUIRED.is5xxServerError()).isTrue();

		assertThat(HttpStatusCode.CONTINUE.is5xxServerError()).isFalse();
	}

}
