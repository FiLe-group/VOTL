package dev.fileeditor.votl.servlet.routes;

import dev.fileeditor.oauth2.session.Session;
import dev.fileeditor.votl.App;
import dev.fileeditor.votl.servlet.WebServlet;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

import net.dv8tion.jda.api.entities.Guild;

import io.javalin.http.Context;
import io.javalin.http.Handler;
import io.javalin.http.InternalServerErrorResponse;
import org.jetbrains.annotations.NotNull;

public class GetMemberSelf implements Handler {
	@Override
	public void handle(@NotNull Context ctx) throws Exception {
		final Session session = Checks.getSession(ctx);
		final Guild guild = Checks.getGuild(ctx);

		ctx.future(() -> {
			return WebServlet.getClient().getUser(session).future()
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
					throw new InternalServerErrorResponse("User not found.");
				});
		});
	}
}
