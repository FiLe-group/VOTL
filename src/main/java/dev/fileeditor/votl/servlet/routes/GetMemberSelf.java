package dev.fileeditor.votl.servlet.routes;

import dev.fileeditor.votl.App;
import dev.fileeditor.votl.servlet.WebServlet;
import dev.fileeditor.votl.servlet.oauth2.Session;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

import net.dv8tion.jda.api.entities.Guild;

import io.javalin.http.BadRequestResponse;
import io.javalin.http.Context;
import io.javalin.http.Handler;
import io.javalin.http.InternalServerErrorResponse;
import io.javalin.http.NotFoundResponse;

public class GetMemberSelf implements Handler {
	
	@Override
	public void handle(Context ctx) {
		long id = ctx.pathParamAsClass("guild", Long.class)
			.getOrThrow(e -> new BadRequestResponse("Incorrect guild ID provided."));
		
		Guild guild = App.getInstance().JDA.getGuildById(id);
		if (guild == null) {
			throw new NotFoundResponse("Guild not found.");
		}

		ctx.future(() -> {
			return WebServlet.getWebClient().getUser(new Session(ctx.cookieStore())).getFuture()
				.thenAccept(user -> {
					ObjectNode node = new ObjectMapper().createObjectNode();
					node.put("id", user.getIdLong());
					node.put("name", user.getName());
					node.put("avatar", user.getEffectiveAvatarUrl());

					ctx.json(node);
				})
				.exceptionally(t -> {
					WebServlet.log.error(t.getMessage());
					throw new InternalServerErrorResponse("Unable to get the user.");
				});
		});
		
	}
}
