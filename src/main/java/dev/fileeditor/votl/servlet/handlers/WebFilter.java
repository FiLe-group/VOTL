package dev.fileeditor.votl.servlet.handlers;

import dev.fileeditor.votl.servlet.WebServlet;
import io.javalin.http.ContentType;
import io.javalin.http.Handler;
import io.javalin.http.UnauthorizedResponse;

public class WebFilter {

	public static Handler filterRequest() {
		return (ctx) -> {
			WebServlet.log.debug("{} {}", ctx.req().getMethod(), ctx.req().getPathInfo());

			ctx.res().setHeader("Access-Control-Allow-Origin", "*");
			ctx.res().setContentType(ContentType.JSON);
		};
	}

	public static Handler authCheck() {
		return (ctx) -> {
			String data = ctx.req().getHeader("Authorization");
			if (data == null || !data.startsWith("Bearer ")) {
				WebServlet.log.warn("Unauthorized request, missing or invalid \"Authorization\" header given.");
				throw new UnauthorizedResponse("You must login first.");
			}

			String token = data.substring(7);
			ctx.cookieStore().set("token_type", "Bearer");
			ctx.cookieStore().set("access_token", token);
		};
	}
	
}
