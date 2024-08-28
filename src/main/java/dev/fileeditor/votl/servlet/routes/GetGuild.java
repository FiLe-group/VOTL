package dev.fileeditor.votl.servlet.routes;

import com.fasterxml.jackson.core.JsonProcessingException;
import dev.fileeditor.votl.App;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

import dev.fileeditor.votl.objects.CmdModule;
import io.javalin.http.InternalServerErrorResponse;
import net.dv8tion.jda.api.entities.Guild;

import io.javalin.http.BadRequestResponse;
import io.javalin.http.Context;
import io.javalin.http.Handler;
import io.javalin.http.NotFoundResponse;

import java.util.ArrayList;
import java.util.List;

public class GetGuild implements Handler {
	@Override
	public void handle(Context ctx) throws Exception {
		long id = ctx.pathParamAsClass("guild", Long.class)
			.getOrThrow(e -> new BadRequestResponse("Incorrect guild ID provided."));
		
		Guild guild = App.getInstance().JDA.getGuildById(id);
		if (guild == null) {
			throw new NotFoundResponse("Guild not found.");
		}

		ObjectMapper mapper = new ObjectMapper();
		ObjectNode guildNode = mapper.createObjectNode();

		guildNode.put("id", guild.getId());
		guildNode.put("name", guild.getName());
		guildNode.put("icon", guild.getIconUrl());
		guildNode.put("bannerUrl", guild.getBannerUrl());
		
		guildNode.put("size", guild.getMemberCount());

		List<String> disabledModules = App.getInstance().getDBUtil().getGuildSettings(guild)
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
	}
}
