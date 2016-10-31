/*
 * Copyright 2002-2016 the original author or authors.
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

package org.springframework.http.codec.json;

import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;

import de.undercouch.actson.JsonEvent;
import de.undercouch.actson.JsonParser;
import org.reactivestreams.Publisher;
import reactor.core.publisher.Flux;

import org.springframework.core.ResolvableType;
import org.springframework.core.codec.AbstractDecoder;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.core.io.buffer.DataBufferUtils;
import org.springframework.util.MimeType;

/**
 * @author Arjen Poutsma
 */
public class ActsonJsonDecoder extends AbstractDecoder<DataBuffer> {

	public ActsonJsonDecoder() {
		super(new MimeType("application", "json", StandardCharsets.UTF_8),
				new MimeType("application", "*+json", StandardCharsets.UTF_8));

	}

	@Override
	public Flux<DataBuffer> decode(Publisher<DataBuffer> inputStream, ResolvableType elementType,
			MimeType mimeType, Map<String, Object> hints) {

		Flux<DataBuffer> flux = Flux.from(inputStream);

		JsonParser parser = new JsonParser(getCharset(mimeType));
		return flux.flatMap(new ActsonDataBufferToJsonEvent(parser));
	}

	private Charset getCharset(MimeType mimeType) {
		return mimeType != null ? mimeType.getCharset() : StandardCharsets.UTF_8;
	}

	private static class ActsonDataBufferToJsonEvent implements
				Function<DataBuffer, Publisher<? extends DataBuffer>> {

		private final JsonParser parser;

		private final AtomicReference<DataBuffer> dataBuffer = new AtomicReference<>();

		public ActsonDataBufferToJsonEvent(JsonParser parser) {
			this.parser = parser;
		}


		@Override
		public Publisher<? extends DataBuffer> apply(DataBuffer current) {
			byte[] bytes = new byte[current.readableByteCount()];
			current.read(bytes);
			this.parser.getFeeder().feed(bytes);

			current.setReadPosition(0);
			if (!this.dataBuffer.compareAndSet(null, current)) {
				this.dataBuffer.get().write(current);
				DataBufferUtils.retain(current);
			}

			while (true) {
				int event = parser.nextEvent();
				if (event == JsonEvent.NEED_MORE_INPUT) {
					// no more events with what currently has been fed to the reader
					break;
				}
				else if (event == JsonEvent.END_ARRAY || event == JsonEvent.END_OBJECT) {
					DataBuffer slice =
							this.dataBuffer.get().slice(0, parser.getParsedCharacterCount());
					DataBufferUtils.retain(slice);

					return Flux.just(slice);
				}

			}
			return Flux.empty();
		}
	}

}
