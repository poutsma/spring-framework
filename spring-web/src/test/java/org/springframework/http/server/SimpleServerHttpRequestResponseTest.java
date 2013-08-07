package org.springframework.http.server;

import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.URISyntaxException;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;
import org.apache.http.HttpResponse;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.HttpClients;
import org.junit.AfterClass;
import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import org.junit.BeforeClass;
import org.junit.Test;

import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.util.FileCopyUtils;
import org.springframework.util.SocketUtils;

/**
 * @author Arjen Poutsma
 */
public class SimpleServerHttpRequestResponseTest {

	private static HttpServer server;

	private static URI url;

	@BeforeClass
	public static void startServer() throws IOException, URISyntaxException {
		int port = SocketUtils.findAvailableTcpPort();
		InetAddress localHost = InetAddress.getLocalHost();
		InetSocketAddress address =
				new InetSocketAddress(localHost, port);

		url = new URI("http", null, localHost.getHostName(), port, "/", null, null);

		server = HttpServer.create(address, port);
		server.start();
	}

	@Test
	public void exchange() throws IOException {
		final String headerName = "Foo";
		final String headerValue = "Bar";
		final byte[] body = "Hello World".getBytes();

		HttpHandler handler = new HttpHandler() {
			@Override
			public void handle(HttpExchange exchange) throws IOException {
				SimpleServerHttpRequest request = new SimpleServerHttpRequest(exchange);
				assertEquals(HttpMethod.GET, request.getMethod());
				assertEquals(url, request.getURI());
				assertEquals(headerValue, request.getHeaders().getFirst(headerName));

				SimpleServerHttpResponse response = new SimpleServerHttpResponse(exchange);

				response.setStatusCode(HttpStatus.ACCEPTED);
				response.getHeaders().add(headerName, headerValue);
				response.getHeaders().setContentLength(body.length);
				FileCopyUtils.copy(body, response.getBody());
				response.close();
			}
		};

		server.createContext("/", handler);

		HttpClient client = HttpClients.createMinimal();
		HttpGet getMethod = new HttpGet(url);
		getMethod.addHeader(headerName, headerValue);

		HttpResponse response = client.execute(getMethod);

		assertEquals(202, response.getStatusLine().getStatusCode());
		assertEquals(headerValue, response.getFirstHeader(headerName).getValue());
		byte[] actualBody = FileCopyUtils.copyToByteArray(response.getEntity().getContent());
		assertArrayEquals(body, actualBody);

	}

	@AfterClass
	public static void stopServer() {
		server.stop(1);
	}

}
