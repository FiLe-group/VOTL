package dev.fileeditor.votl.servlet.routes;

import com.fasterxml.jackson.core.JsonProcessingException;
import dev.fileeditor.oauth2.session.Session;
import dev.fileeditor.votl.App;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

import io.javalin.http.InternalServerErrorResponse;
import net.dv8tion.jda.api.entities.Guild;

import io.javalin.http.Context;
import io.javalin.http.Handler;
import org.jetbrains.annotations.NotNull;

import java.util.List;

public class GetGuild implements Handler {
	@Override
	public void handle(@NotNull Context ctx) throws Exception {
		final Session session = Checks.getSession(ctx);
		final Guild guild = Checks.getGuild(ctx);

		ctx.future(() -> Checks.checkPermissionsAsync(session, guild, member -> {
			ObjectMapper mapper = new ObjectMapper();
			ObjectNode guildNode = mapper.createObjectNode();

			guildNode.put("id", guild.getId());
			guildNode.put("name", guild.getName());
			guildNode.put("icon", guild.getIconId());
			guildNode.put("banner", guild.getBannerId());

			guildNode.put("size", guild.getMemberCount());

			final List<String> disabledModules = App.getInstance().getDBUtil().getGuildSettings(guild)
				.getDisabledModules()
				.stream()
				.map(m -> m.name().toLowerCase())
				.toList();
			try {
				guildNode.put("disabledModules", mapper.writeValueAsString(disabledModules));
			} catch (JsonProcessingException ex) {
				throw new InternalServerErrorResponse("Unable to parse disabled modules. "+ex.getMessage());
			}

			// Send response
			ctx.json(guildNode);
		}));
	}
}
