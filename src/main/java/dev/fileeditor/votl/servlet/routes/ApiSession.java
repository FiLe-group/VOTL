package dev.fileeditor.votl.servlet.routes;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import dev.fileeditor.oauth2.session.Session;
import dev.fileeditor.votl.servlet.WebServlet;
import io.javalin.http.*;
import org.jetbrains.annotations.NotNull;

import java.util.Optional;

public class ApiSession implements Handler {
	@Override
	public void handle(@NotNull Context ctx) throws Exception {
		final Session session = WebServlet.getSession(ctx);

		ctx.future(() -> {
			return WebServlet.getClient().getUser(session).future()
				.thenAccept(user -> {
					ObjectNode node = new ObjectMapper().createObjectNode();
					node.put("id", user.getId());
					node.put("name", user.getName());
					node.put("avatar", Optional.ofNullable(user.getAvatarId())
						.orElse(user.getDefaultAvatarId()));
					node.put("banner", user.getBannerId());

					ctx.json(node);
				})
				.exceptionally(t -> {
					WebServlet.log.error(t.getMessage());
					throw new InternalServerErrorResponse("Failed to get the user.");
				});
		});
	}
}
