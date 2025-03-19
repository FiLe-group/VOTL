package dev.fileeditor.votl.servlet.routes;

import dev.fileeditor.oauth2.Scope;
import dev.fileeditor.votl.servlet.WebServlet;
import io.javalin.http.Context;
import io.javalin.http.FoundResponse;
import io.javalin.http.Handler;
import org.jetbrains.annotations.NotNull;

public class ApiLogin implements Handler {
	@Override
	public void handle(@NotNull Context ctx) throws Exception {
		String url = WebServlet.getClient().generateAuthorizationURL(
			"%s://%s/auth/callback".formatted(ctx.scheme(), "localhost:3000"),
			Scope.IDENTIFY, Scope.GUILDS
		);

		throw new FoundResponse(url);
	}
}
