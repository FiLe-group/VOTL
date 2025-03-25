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
			return Checks.getGuilds(session).thenAccept(guilds -> {
				ObjectMapper mapper = new ObjectMapper();
				ArrayNode guildArray = mapper.createArrayNode();

				guilds.stream()
					.filter(Checks.GuildInfo::isAdmin)
					.forEach(guild -> {
						ObjectNode guildNode = mapper.createObjectNode();

						guildNode.put("id", guild.id());
						guildNode.put("name", guild.name());
						guildNode.put("icon", guild.icon());
						guildNode.put("banner", guild.banner());
						guildNode.put("size", guild.size());

						guildNode.put("bot", guild.botJoined());

						guildArray.add(guildNode);
					});

				ctx.json(guildArray);
			});
		});
	}
}