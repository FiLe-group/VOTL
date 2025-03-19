package dev.fileeditor.votl.servlet.handlers;

import dev.fileeditor.votl.servlet.WebServlet;
import io.javalin.http.Handler;

import static dev.fileeditor.votl.servlet.utils.SessionUtil.SESSION_KEY;

public class WebFilter {

	public static Handler authCheck() {
		return (ctx) -> {
			ctx.sessionAttribute(SESSION_KEY, WebServlet.getSession(ctx));
		};
	}
	
}
