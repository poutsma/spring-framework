package org.springframework.http.server;

import java.io.IOException;
import java.io.OutputStream;
import java.util.List;
import java.util.Map;

import com.sun.net.httpserver.Headers;
import com.sun.net.httpserver.HttpExchange;

import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.util.Assert;

/**
 * {@link ServerHttpResponse} implementation that is based on the HTTP server that is
 * included in Sun's JRE 1.6.
 *
 * @author Arjen Poutsma
 */
public class SimpleServerHttpResponse implements ServerHttpResponse {

	private static final String CONTENT_LENGTH = "Content-Length";


	private final HttpExchange exchange;

	private HttpStatus status = HttpStatus.OK;

	private final HttpHeaders headers = new HttpHeaders();

	private boolean headersWritten = false;

	/**
	 * Construct a new instance of the {@code SimpleServerHttpResponse} based on the
	 * given {@link HttpExchange}.
	 */
	public SimpleServerHttpResponse(HttpExchange exchange) {
		Assert.notNull(exchange, "'exchange' must not be null");
		this.exchange = exchange;
	}

	/**
	 * Return the {@code HttpExchange} this object is based on.
	 */
	public HttpExchange getExchange() {
		return exchange;
	}

	@Override
	public void setStatusCode(HttpStatus status) {
		this.status = status;
	}

	@Override
	public HttpHeaders getHeaders() {
		return (this.headersWritten ? HttpHeaders.readOnlyHttpHeaders(this.headers) : this.headers);
	}

	@Override
	public OutputStream getBody() throws IOException {
		writeHeaders();
		return this.exchange.getResponseBody();
	}

	@Override
	public void flush() throws IOException {
		getBody().flush();
	}

	@Override
	public void close() {
		this.exchange.close();
	}

	private void writeHeaders() throws IOException {
		if (!this.headersWritten) {
			Headers exchangeHeaders = this.exchange.getResponseHeaders();
			for (Map.Entry<String, List<String>> entry : this.headers.entrySet()) {
				String headerName = entry.getKey();
				if (!headerName.equalsIgnoreCase(CONTENT_LENGTH)) {
					exchangeHeaders.put(headerName, entry.getValue());
				}
			}
			long contentLength = this.headers.getContentLength();
			this.exchange.sendResponseHeaders(this.status.value(), contentLength);
			this.headersWritten = true;
		}
	}
}
