package dev.fileeditor.votl.servlet.routes;

import dev.fileeditor.votl.servlet.WebServlet;
import io.javalin.http.Context;
import io.javalin.http.Handler;
import io.javalin.http.HttpStatus;
import io.javalin.http.RedirectResponse;
import org.jetbrains.annotations.NotNull;

public class ApiLogout implements Handler {
	@Override
	public void handle(@NotNull Context ctx) throws Exception {
		WebServlet.endSession(ctx);

		//ctx.redirect("/");
		throw new RedirectResponse(HttpStatus.FOUND, "/");
	}
}
