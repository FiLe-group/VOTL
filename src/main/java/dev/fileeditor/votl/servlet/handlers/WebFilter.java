package dev.fileeditor.votl.servlet.handlers;

import dev.fileeditor.oauth2.session.Session;
import dev.fileeditor.votl.servlet.WebServlet;
import io.javalin.http.ContentType;
import io.javalin.http.Handler;
import io.javalin.http.UnauthorizedResponse;

import java.time.OffsetDateTime;

public class WebFilter {

	public static Handler logRequest() {
		return (ctx) -> {
			WebServlet.log.debug("{} {}", ctx.req().getMethod(), ctx.req().getPathInfo());
		};
	}

	public static Handler setJsonResponse() {
		return (ctx) -> {
			ctx.res().setContentType(ContentType.JSON);
		};
	}

	public static Handler authCheck() {
		return (ctx) -> {
			Session session = WebServlet.getSession(ctx.cookieStore());
			if (session == null || session.getExpiration().isAfter(OffsetDateTime.now()))
				throw new UnauthorizedResponse("Session expired.");
		};
	}
	
}
