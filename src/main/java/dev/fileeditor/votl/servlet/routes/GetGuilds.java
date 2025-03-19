package dev.fileeditor.votl.servlet.routes;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import dev.fileeditor.oauth2.session.Session;
import io.javalin.http.*;
import org.jetbrains.annotations.NotNull;

public class GetGuilds implements Handler {
	@Override
	public void handle(@NotNull Context ctx) throws Exception {
		final Session session = Checks.getSession(ctx);

		ctx.future(() -> {
			return Checks.retrieveGuilds(session, guilds -> {
				ObjectMapper mapper = new ObjectMapper();
				ArrayNode guildArray = mapper.createArrayNode();

				guilds.forEach(guild -> {
					ObjectNode guildNode = mapper.createObjectNode();

					guildNode.put("id", guild.id());
					guildNode.put("name", guild.name());
					guildNode.put("icon", guild.icon());
					guildNode.put("banner", guild.banner());
					guildNode.put("size", guild.size());

					guildNode.put("isAdmin", guild.isAdmin());
					// set if admin

					guildArray.add(guildNode);
				});

				ctx.json(guildArray);
			});
		});
	}
}