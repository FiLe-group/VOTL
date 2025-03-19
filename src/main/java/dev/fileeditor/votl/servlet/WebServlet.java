package dev.fileeditor.votl.servlet;

import dev.fileeditor.oauth2.OAuth2Client;
import dev.fileeditor.oauth2.session.Session;
import dev.fileeditor.votl.servlet.utils.AuthSessionController;
import dev.fileeditor.votl.servlet.utils.AuthStateController;
import dev.fileeditor.votl.servlet.utils.SessionUtil;
import io.javalin.http.*;
import io.javalin.validation.ValidationException;
import org.jetbrains.annotations.NotNull;
import org.slf4j.LoggerFactory;

import ch.qos.logback.classic.Logger;

import java.io.FileNotFoundException;

import dev.fileeditor.votl.servlet.handlers.WebFilter;
import dev.fileeditor.votl.servlet.handlers.WebHandler;

import io.javalin.Javalin;

@SuppressWarnings("unused")
public class WebServlet {
	
	public static final Logger log = (Logger) LoggerFactory.getLogger(WebServlet.class);

	public static final int DEFAULT_PORT = 8080;

	private static Javalin web;
	private static OAuth2Client client;

	private final int port;
	private final String allowedHost;
	private final long clientId;
	private final String clientSecret;

	private static boolean initialized;

	public WebServlet(int port, String allowedHost, long clientId, String clientSecret) {
		this.port = port;
		this.allowedHost = allowedHost;
		this.clientId = clientId;
		this.clientSecret = clientSecret;
		WebServlet.initialized = false;
	}

	public static OAuth2Client getClient() {
		return client;
	}

	private void initialize() {
		log.info("Starting Javalin API on port: {}", port);

		web = Javalin.create(config ->
			{
				config.http.asyncTimeout = 10_000;
				config.http.defaultContentType = "application/json";
				config.http.strictContentTypes = true;
				config.bundledPlugins.enableCors(cors -> {
					cors.addRule(it -> {
						it.allowHost(allowedHost);
						it.allowCredentials = true;
					});
				});
				config.requestLogger.http((ctx, ms) ->
					log.debug("{} {} took {}ms", ctx.req().getMethod(), ctx.req().getPathInfo(), ms)
				);
				config.useVirtualThreads = true; // TODO: check
			})
			.beforeMatched(WebHandler.setContentType())
			.before("/priv/*", WebFilter.authCheck())
			.exception(FileNotFoundException.class, (e, ctx) -> ctx.status(HttpStatus.NOT_FOUND))
			.exception(ValidationException.class, WebHandler.validationExceptionHandler())
			.exception(HttpResponseException.class, WebHandler.errorResponseHandler())
			.exception(Exception.class, WebHandler.exceptionHandler())
			.start(port);

		client = new OAuth2Client.Builder()
			.setClientId(clientId)
			.setClientSecret(clientSecret)
			.setStateController(new AuthStateController())
			.setSessionController(new AuthSessionController())
			.build();

		initialized = true;
	}

	public static void shutdown() {
		if (initialized) {
			web.stop();
			client.shutdown();
		}
	}

	@NotNull
	public static Session getSession(@NotNull Context ctx) {
		if (!initialized)
			throw new ServiceUnavailableResponse("Servlet is not initialized.");

		String sessionId = SessionUtil.getSessionId(ctx);
		if (sessionId == null) {
			ctx.header(Header.WWW_AUTHENTICATE, "Bearer");
			throw new UnauthorizedResponse("No session found.");
		}

		Session session = getClient().getSessionController().getSession(sessionId);
		if (session == null) {
			throw new UnauthorizedResponse("No active session.");
		}

		return session;
	}

	public static void endSession(@NotNull Context ctx) {
		if (!initialized)
			throw new ServiceUnavailableResponse("Servlet is not initialized.");

		String sessionId = SessionUtil.getSessionId(ctx);
		if (sessionId != null) {
			getClient().getSessionController().endSession(sessionId);
		}
	}

	/**
	 * Map the handler for HTTP GET requests
	 *
	 * @param path the path
	 * @param handler The handler
	 * @param privatePath Path is protected
	 */
	public synchronized void registerGet(final String path, final Handler handler, final boolean privatePath) {
		if (!initialized) initialize();

		final String finalPath = privatePath ? "/priv"+path : path;
		log.debug("GET {} has been registered to {}", finalPath, handler.getClass().getTypeName());
		web.get(finalPath, handler);
	}

	/**
	 * Map the handler for HTTP POST requests
	 *
	 * @param path the path
	 * @param handler The handler
	 * @param privatePath Path is protected
	 */
	public synchronized void registerPost(final String path, final Handler handler, final boolean privatePath) {
		if (!initialized) initialize();

		final String finalPath = privatePath ? "/priv"+path : path;
		log.debug("POST {} has been registered to {}", finalPath, handler.getClass().getTypeName());
		web.post(finalPath, handler);
	}

	/**
	 * Map the handler for HTTP PUT requests
	 *
	 * @param path the path
	 * @param handler The handler
	 * @param privatePath Path is protected
	 */
	public synchronized void registerPut(final String path, final Handler handler, final boolean privatePath) {
		if (!initialized) initialize();

		final String finalPath = privatePath ? "/priv"+path : path;
		log.debug("PUT {} has been registered to {}", finalPath, handler.getClass().getTypeName());
		web.put(finalPath, handler);
	}

	/**
	 * Map the handler for HTTP PATCH requests
	 *
	 * @param path the path
	 * @param handler The handler
	 * @param privatePath Path is protected
	 */
	public synchronized void registerPatch(final String path, final Handler handler, final boolean privatePath) {
		if (!initialized) initialize();

		final String finalPath = privatePath ? "/priv"+path : path;
		log.debug("PATCH {} has been registered to {}", finalPath, handler.getClass().getTypeName());
		web.patch(finalPath, handler);
	}

	/**
	 * Map the handler for HTTP DELETE requests
	 *
	 * @param path the path
	 * @param handler The handler
	 * @param privatePath Path is protected
	 */
	public synchronized void registerDelete(final String path, final Handler handler, final boolean privatePath) {
		if (!initialized) initialize();

		final String finalPath = privatePath ? "/priv"+path : path;
		log.debug("DELETE {} has been registered to {}", finalPath, handler.getClass().getTypeName());
		web.delete(finalPath, handler);
	}

	/**
	 * Map the handler for HTTP HEAD requests
	 *
	 * @param path the path
	 * @param handler The handler
	 * @param privatePath Path is protected
	 */
	public synchronized void registerHead(final String path, final Handler handler, final boolean privatePath) {
		if (!initialized) initialize();

		final String finalPath = privatePath ? "/priv"+path : path;
		log.debug("HEAD {} has been registered to {}", finalPath, handler.getClass().getTypeName());
		web.head(finalPath, handler);
	}

	/**
	 * Map the handler for HTTP OPTIONS requests
	 *
	 * @param path the path
	 * @param handler The handler
	 * @param privatePath Path is protected
	 */
	public synchronized void registerOptions(final String path, final Handler handler, final boolean privatePath) {
		if (!initialized) initialize();

		final String finalPath = privatePath ? "/priv"+path : path;
		log.debug("OPTIONS {} has been registered to {}", finalPath, handler.getClass().getTypeName());
		web.options(finalPath, handler);
	}
}
