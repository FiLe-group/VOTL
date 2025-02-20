package dev.fileeditor.votl.servlet.routes;

import dev.fileeditor.oauth2.Scope;
import dev.fileeditor.oauth2.exceptions.InvalidStateException;
import dev.fileeditor.votl.servlet.WebServlet;
import dev.fileeditor.votl.servlet.utils.SessionUtil;
import io.javalin.http.*;

public class ApiCallback implements Handler {
	@Override
	public void handle(Context ctx) throws Exception {
		var query = ctx.queryParamMap();
		if (!query.containsKey("code") || !query.containsKey("state")) {
			//ctx.redirect("/");
			throw new RedirectResponse(HttpStatus.FOUND, "/");
		}

		String sessionId = SessionUtil.getSessionId(ctx);
		if (sessionId == null) {
			//ctx.redirect("/");
			throw new RedirectResponse(HttpStatus.FOUND, "/");
		}

		ctx.future(() -> {
			try {
				return WebServlet.getClient().startSession(
					ctx.queryParam("code"), ctx.queryParam("state"), sessionId,
					Scope.IDENTIFY, Scope.GUILDS
				).future().thenRunAsync(() -> {
					//ctx.redirect("/dash/user/home");
					throw new RedirectResponse(HttpStatus.FOUND, "/dash/user/home");
				});
			} catch (InvalidStateException ex) {
				throw new InternalServerErrorResponse("Invalid state provided: %s".formatted(ex.getMessage()));
			}
		});

	}


}
