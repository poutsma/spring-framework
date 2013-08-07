package org.springframework.http.server;

import java.io.IOException;
import java.io.InputStream;
import java.net.InetSocketAddress;
import java.net.URI;
import java.security.Principal;
import java.util.Map;

import com.sun.net.httpserver.HttpExchange;

import org.springframework.http.Cookie;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.util.Assert;
import org.springframework.util.MultiValueMap;

/**
 * {@link ServerHttpRequest} implementation that is based on the HTTP server that is
 * included in Sun's JRE 1.6.
 *
 * @author Arjen Poutsma
 * @since 4.0
 */
public class SimpleServerHttpRequest implements ServerHttpRequest {

	private final HttpExchange exchange;

	private HttpHeaders headers;


	/**
	 * Construct a new instance of the {@code SimpleServerHttpRequest} based on the
	 * given {@link HttpExchange}.
	 */
	public SimpleServerHttpRequest(HttpExchange exchange) {
		Assert.notNull(exchange, "'exchange' must not be null");
		this.exchange = exchange;
	}

	/**
	 * Returns the {@code HttpExchange} this object is based on.
	 */
	public HttpExchange getExchange() {
		return exchange;
	}

	@Override
	public HttpMethod getMethod() {
		return HttpMethod.valueOf(this.exchange.getRequestMethod());
	}

	@Override
	public URI getURI() {
		return this.exchange.getRequestURI();
	}

	@Override
	public HttpHeaders getHeaders() {
		if (this.headers == null) {
			this.headers = new HttpHeaders();
			this.headers.putAll(this.exchange.getRequestHeaders());
		}
		return this.headers;
	}

	@Override
	public Principal getPrincipal() {
		return this.exchange.getPrincipal();
	}

	@Override
	public InetSocketAddress getRemoteAddress() {
		return this.exchange.getRemoteAddress();
	}

	@Override
	public InetSocketAddress getLocalAddress() {
		return this.exchange.getLocalAddress();
	}

	@Override
	public InputStream getBody() throws IOException {
		return this.exchange.getRequestBody();
	}

	// Impossible to implement

	@Override
	public MultiValueMap<String, String> getQueryParams() {
		return null;  //To change body of implemented methods use File | Settings | File Templates.
	}

	@Override
	public Map<String, Cookie> getCookies() {
		return null;  //To change body of implemented methods use File | Settings | File Templates.
	}

	@Override
	public String getRemoteHostName() {
		return null;  //To change body of implemented methods use File | Settings | File Templates.
	}

	@Override
	public ServerHttpAsyncRequestControl getAsyncRequestControl(
			ServerHttpResponse response) {
		return null;  //To change body of implemented methods use File | Settings | File Templates.
	}
}
