package dev.fileeditor.votl.servlet;

import org.slf4j.LoggerFactory;

import ch.qos.logback.classic.Logger;

import java.io.FileNotFoundException;

import dev.fileeditor.votl.servlet.handlers.WebFilter;
import dev.fileeditor.votl.servlet.handlers.WebHandler;
import dev.fileeditor.votl.servlet.oauth2.OAuth2Client;

import io.javalin.Javalin;
import io.javalin.http.Handler;
import io.javalin.http.HttpStatus;

public class WebServlet {
	
	public static final Logger log = (Logger) LoggerFactory.getLogger(WebServlet.class);

	public static final int defaultPort = 8080;

	private static Javalin web;
	private final int port;
	private final String allowedHost;
	private static boolean initialized;

	private static OAuth2Client webClient;

	public WebServlet(int port, String allowedHost) {
		this.port = port;
		this.allowedHost = allowedHost;
		WebServlet.initialized = false;
	}

	public static OAuth2Client getWebClient() {
		return webClient;
	}

	private void initialize() {
		log.info("Starting Javalin API on port: {}", port);

		web = Javalin.create(config -> {
				config.http.asyncTimeout = 10_000;
				config.bundledPlugins.enableCors(cors -> {
					cors.addRule(it -> {
						it.allowHost(allowedHost);
						it.allowCredentials = true;
					});
				});
			})
			.beforeMatched(WebFilter.authCheck())
			.before(WebFilter.filterRequest())
			.exception(FileNotFoundException.class, (e, ctx) -> ctx.status(HttpStatus.NOT_FOUND))
			.exception(Exception.class, WebHandler.exceptionHandler())
			.after(ctx -> ctx.cookieStore().clear())
			.start(port);

		webClient = new OAuth2Client();
		initialized = true;
	}

	public static void shutdown() {
		if (initialized) web.stop();
	}

	/**
	 * Map the handler for HTTP GET requests
	 *
	 * @param path  the path
	 * @param handler The handler
	 */
	public synchronized void registerGet(final String path, final Handler handler) {
		if (!initialized) initialize();

		log.debug("GET {} has been registered to {}", path, handler.getClass().getTypeName());
		web.get(path, handler);
	}

	/**
	 * Map the handler for HTTP POST requests
	 *
	 * @param path  the path
	 * @param handler The handler
	 */
	public synchronized void registerPost(final String path, final Handler handler) {
		if (!initialized) initialize();

		log.debug("POST {} has been registered to {}", path, handler.getClass().getTypeName());
		web.post(path, handler);
	}

	/**
	 * Map the handler for HTTP PUT requests
	 *
	 * @param path  the path
	 * @param handler The handler
	 */
	public synchronized void registerPut(final String path, final Handler handler) {
		if (!initialized) initialize();

		log.debug("PUT {} has been registered to {}", path, handler.getClass().getTypeName());
		web.put(path, handler);
	}

	/**
	 * Map the handler for HTTP PATCH requests
	 *
	 * @param path  the path
	 * @param handler The handler
	 */
	public synchronized void registerPatch(final String path, final Handler handler) {
		if (!initialized) initialize();

		log.debug("PATCH {} has been registered to {}", path, handler.getClass().getTypeName());
		web.patch(path, handler);
	}

	/**
	 * Map the handler for HTTP DELETE requests
	 *
	 * @param path  the path
	 * @param handler The handler
	 */
	public synchronized void registerDelete(final String path, final Handler handler) {
		if (!initialized) initialize();

		log.debug("DELETE {} has been registered to {}", path, handler.getClass().getTypeName());
		web.delete(path, handler);
	}

	/**
	 * Map the handler for HTTP HEAD requests
	 *
	 * @param path  the path
	 * @param handler The handler
	 */
	public synchronized void registerHead(final String path, final Handler handler) {
		if (!initialized) initialize();

		log.debug("HEAD {} has been registered to {}", path, handler.getClass().getTypeName());
		web.head(path, handler);
	}

	/**
	 * Map the handler for HTTP OPTIONS requests
	 *
	 * @param path  the path
	 * @param handler The handler
	 */
	public synchronized void registerOptions(final String path, final Handler handler) {
		if (!initialized) initialize();

		log.debug("OPTIONS {} has been registered to {}", path, handler.getClass().getTypeName());
		web.options(path, handler);
	}

}
