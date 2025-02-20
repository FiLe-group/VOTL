package dev.fileeditor.votl.servlet.handlers;

import dev.fileeditor.oauth2.session.Session;
import dev.fileeditor.votl.servlet.WebServlet;
import io.javalin.http.ContentType;
import io.javalin.http.Handler;
import io.javalin.http.UnauthorizedResponse;
import jakarta.servlet.http.HttpSession;

import java.time.OffsetDateTime;

public class WebFilter {

	public static Handler authCheck() {
		return (ctx) -> {
			Session session = WebServlet.getSession(ctx);
			if (session == null || session.getExpiration().isBefore(OffsetDateTime.now()))
				throw new UnauthorizedResponse("Session expired.");
		};
	}
	
}
