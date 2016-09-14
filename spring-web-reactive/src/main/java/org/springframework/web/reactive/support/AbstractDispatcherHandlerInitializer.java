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

package org.springframework.web.reactive.support;

import javax.servlet.ServletContext;
import javax.servlet.ServletContextEvent;
import javax.servlet.ServletContextListener;
import javax.servlet.ServletException;
import javax.servlet.ServletRegistration;

import org.springframework.context.ApplicationContext;
import org.springframework.http.server.reactive.HttpHandler;
import org.springframework.http.server.reactive.ServletHttpHandlerAdapter;
import org.springframework.util.Assert;
import org.springframework.web.context.AbstractContextLoaderInitializer;
import org.springframework.web.context.ConfigurableWebApplicationContext;
import org.springframework.web.context.WebApplicationContext;
import org.springframework.web.reactive.DispatcherHandler;
import org.springframework.web.server.WebHandler;
import org.springframework.web.server.adapter.HttpWebHandlerAdapter;

/**
 * Base class for {@link org.springframework.web.WebApplicationInitializer}
 * implementations that register a {@link DispatcherHandler} in the servlet context, wrapping it in
 * a {@link ServletHttpHandlerAdapter}.
 *
 * <p>Concrete implementations are required to implement
 * {@link #createServletApplicationContext()}, as well as {@link #getServletMappings()},
 * both of which get invoked from {@link #registerDispatcherHandler(ServletContext)}.
 * Further customization can be achieved by overriding
 * {@link #customizeRegistration(ServletRegistration.Dynamic)}.
 *
 * <p>Because this class extends from {@link AbstractContextLoaderInitializer}, concrete
 * implementations are also required to implement {@link #createRootApplicationContext()}
 * to set up a parent "<strong>root</strong>" application context. If a root context is
 * not desired, implementations can simply return {@code null} in the
 * {@code createRootApplicationContext()} implementation.
 *
 * @author Arjen Poutsma
 * @since 5.0
 */
public abstract class AbstractDispatcherHandlerInitializer extends AbstractContextLoaderInitializer {

	/**
	 * The default servlet name. Can be customized by overriding {@link #getServletName}.
	 */
	public static final String DEFAULT_SERVLET_NAME = "dispatcher-handler";


	@Override
	public void onStartup(ServletContext servletContext) throws ServletException {
		super.onStartup(servletContext);
		registerDispatcherHandler(servletContext);
	}

	/**
	 * Register a {@link DispatcherHandler} against the given servlet context.
	 * <p>This method will create a {@link DispatcherHandler}, initializing it with the application
	 * context returned from {@link #createServletApplicationContext()}. The created handler will be
	 * wrapped in a {@link ServletHttpHandlerAdapter} servlet with the name
     * returned by {@link #getServletName()}, mapping it to the patterns
	 * returned from {@link #getServletMappings()}.
	 * <p>Further customization can be achieved by overriding {@link
	 * #customizeRegistration(ServletRegistration.Dynamic)} or
	 * {@link #createDispatcherHandler(WebApplicationContext)}.
	 * @param servletContext the context to register the servlet against
	 */
	protected void registerDispatcherHandler(ServletContext servletContext) {
		String servletName = getServletName();
		Assert.hasLength(servletName, "getServletName() must not return empty or null");

		WebApplicationContext servletAppContext = createServletApplicationContext();
		Assert.notNull(servletAppContext,
				"createServletApplicationContext() did not return an application " +
				"context for servlet [" + servletName + "]");
		refreshServletServletApplicationContext(servletAppContext);
		registerCloseListener(servletContext, servletAppContext);

		WebHandler dispatcherHandler = createDispatcherHandler(servletAppContext);
		Assert.notNull(dispatcherHandler,
			"createDispatcherHandler() did not return a WebHandler for servlet ["
				+ servletName + "]");

		ServletHttpHandlerAdapter servlet = createServlet(dispatcherHandler);
		Assert.notNull(servlet,
				"createHttpHandler() did not return a ServletHttpHandlerAdapter for servlet ["
						+ servletName + "]");

		ServletRegistration.Dynamic registration = servletContext.addServlet(servletName, servlet);
		Assert.notNull(registration,
				"Failed to register servlet with name '" + servletName + "'." +
				"Check if there is another servlet registered under the same name.");

		registration.setLoadOnStartup(1);
		registration.addMapping(getServletMappings());
		registration.setAsyncSupported(true);


		customizeRegistration(registration);
	}

	/**
	 * Return the name under which the {@link ServletHttpHandlerAdapter} will be registered.
	 * Defaults to {@link #DEFAULT_SERVLET_NAME}.
	 * @see #registerDispatcherHandler(ServletContext)
	 */
	protected String getServletName() {
		return DEFAULT_SERVLET_NAME;
	}

	/**
	 * Create a servlet application context to be provided to the {@code DispatcherHandler}.
	 * <p>The returned context is delegated to Spring's
	 * {@link DispatcherHandler#DispatcherHandler(ApplicationContext)}. As such,
	 * it typically contains controllers, view resolvers, and other web-related beans.
	 * @see #registerDispatcherHandler(ServletContext)
	 */
	protected abstract WebApplicationContext createServletApplicationContext();

	protected void refreshServletServletApplicationContext(WebApplicationContext servletAppContext) {
		if (servletAppContext instanceof ConfigurableWebApplicationContext) {
			ConfigurableWebApplicationContext appContext =
					(ConfigurableWebApplicationContext) servletAppContext;
			if (!appContext.isActive()) {
				appContext.refresh();
			}
		}
	}

	/**
	 * Create a {@link DispatcherHandler} (or other kind of {@link WebHandler}-derived
	 * dispatcher) with the specified {@link WebApplicationContext}.
	 */
	protected WebHandler createDispatcherHandler(WebApplicationContext servletAppContext) {
		return new DispatcherHandler(servletAppContext);
	}

	/**
	 * Create a {@link ServletHttpHandlerAdapter}  with the specified {@link WebApplicationContext}.
	 * <p>Default implementation returns a {@code ServletHttpHandlerAdapter} with the provided
	 * {@code webHandler}.
	 */
	protected ServletHttpHandlerAdapter createServlet(WebHandler webHandler) {
		HttpHandler httpHandler = new HttpWebHandlerAdapter(webHandler);
		return new ServletHttpHandlerAdapter(httpHandler);
	}

	/**
	 * Specify the servlet mapping(s) for the {@code ServletHttpHandlerAdapter} &mdash;
	 * for example {@code "/"}, {@code "/app"}, etc.
	 * @see #registerDispatcherHandler(ServletContext)
	 */
	protected abstract String[] getServletMappings();

	/**
	 * Optionally perform further registration customization once
	 * {@link #registerDispatcherHandler(ServletContext)} has completed.
	 * @param registration the {@code ServletHttpHandlerAdapter} registration to be customized
	 * @see #registerDispatcherHandler(ServletContext)
	 */
	protected void customizeRegistration(ServletRegistration.Dynamic registration) {
	}

	protected void registerCloseListener(ServletContext servletContext,
			WebApplicationContext applicationContext) {

		if (applicationContext instanceof ConfigurableWebApplicationContext) {
			ConfigurableWebApplicationContext context =
					(ConfigurableWebApplicationContext) applicationContext;
			ServletContextDestroyedListener listener = new ServletContextDestroyedListener(context);
			servletContext.addListener(listener);
		}
	}

	private static class ServletContextDestroyedListener implements ServletContextListener {

		private final ConfigurableWebApplicationContext context;

		public ServletContextDestroyedListener(ConfigurableWebApplicationContext context) {
			this.context = context;
		}

		@Override
		public void contextInitialized(ServletContextEvent sce) {
		}

		@Override
		public void contextDestroyed(ServletContextEvent sce) {
			this.context.close();
		}
	}

}
