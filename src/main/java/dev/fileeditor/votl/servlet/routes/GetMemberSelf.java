package dev.fileeditor.votl.servlet.routes;

import dev.fileeditor.votl.App;
import dev.fileeditor.votl.servlet.WebServlet;

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
	public void handle(Context ctx) throws Exception {
		long id = ctx.pathParamAsClass("guild", Long.class)
			.getOrThrow(e -> new BadRequestResponse("Incorrect guild ID provided."));
		
		Guild guild = App.getInstance().JDA.getGuildById(id);
		if (guild == null) {
			throw new NotFoundResponse("Guild not found.");
		}

		ctx.future(() -> {
			return WebServlet.getClient().getUser(WebServlet.getSession(ctx)).future()
				.thenCompose(user -> guild.retrieveMemberById(user.getIdLong()).submit())
				.thenAccept(member -> {
					ObjectNode node = new ObjectMapper().createObjectNode();
					node.put("id", member.getIdLong());
					node.put("name", member.getEffectiveName());
					node.put("avatar", member.getEffectiveAvatarUrl());
					node.put("access", App.getInstance().getCheckUtil().getAccessLevel(member).getName());

					ctx.json(node);
				})
				.exceptionally(t -> {
					WebServlet.log.error(t.getMessage());
					throw new InternalServerErrorResponse("Unable to get the user.");
				});
		});
	}
}
