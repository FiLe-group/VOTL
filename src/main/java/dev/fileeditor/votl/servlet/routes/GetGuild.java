package dev.fileeditor.votl.servlet.routes;

import dev.fileeditor.votl.App;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

import net.dv8tion.jda.api.entities.Guild;

import io.javalin.http.BadRequestResponse;
import io.javalin.http.Context;
import io.javalin.http.Handler;
import io.javalin.http.NotFoundResponse;

public class GetGuild implements Handler {

	@Override
	public void handle(Context ctx) {
		long id = ctx.pathParamAsClass("guild", Long.class)
			.getOrThrow(e -> new BadRequestResponse("Incorrect guild ID provided."));
		
		Guild guild = App.getInstance().JDA.getGuildById(id);
		if (guild == null) {
			throw new NotFoundResponse("Guild not found.");
		}

		ObjectNode guildNode = new ObjectMapper().createObjectNode();

		guildNode.put("id", guild.getId());
		guildNode.put("name", guild.getName());
		guildNode.put("icon", guild.getIconUrl());
		
		guildNode.put("size", guild.getMemberCount());

		// NOTE: add enabled 'features' info

		// Send response
		ctx.json(guildNode);	
	}
	
}
