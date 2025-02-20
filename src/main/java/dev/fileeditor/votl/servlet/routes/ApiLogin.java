package dev.fileeditor.votl.servlet.routes;

import dev.fileeditor.oauth2.Scope;
import dev.fileeditor.votl.servlet.WebServlet;
import dev.fileeditor.votl.servlet.utils.SessionUtil;
import io.javalin.http.Context;
import io.javalin.http.Handler;
import io.javalin.http.HttpStatus;
import io.javalin.http.RedirectResponse;
import org.jetbrains.annotations.NotNull;

public class ApiLogin implements Handler {
	@Override
	public void handle(@NotNull Context ctx) throws Exception {
		String sessionId = SessionUtil.getSessionId(ctx);
		if (sessionId != null && WebServlet.getClient().getSessionController().getSession(sessionId) != null) {
			//ctx.redirect("/dash/home/user");
			throw new RedirectResponse(HttpStatus.FOUND, "/dash/user/home");
		}
		SessionUtil.createSessionId(ctx);

		String url = WebServlet.getClient().generateAuthorizationURL(
			"%s://%s/api/callback".formatted(ctx.scheme(), ctx.host()),
			Scope.IDENTIFY, Scope.GUILDS
		);

		//ctx.redirect(url);
		throw new RedirectResponse(HttpStatus.FOUND, url);
	}
}
