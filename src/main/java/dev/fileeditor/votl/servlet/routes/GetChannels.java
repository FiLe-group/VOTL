package dev.fileeditor.votl.servlet.routes;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import dev.fileeditor.votl.App;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import net.dv8tion.jda.api.entities.Guild;

import io.javalin.http.BadRequestResponse;
import io.javalin.http.Context;
import io.javalin.http.Handler;
import io.javalin.http.InternalServerErrorResponse;
import io.javalin.http.NotFoundResponse;

public class GetChannels implements Handler {
	
	@Override
	public void handle(Context ctx) {
		long id = ctx.pathParamAsClass("guild", Long.class)
			.getOrThrow(e -> new BadRequestResponse("Incorrect guild ID provided."));
		
		Guild guild = App.getInstance().JDA.getGuildById(id);
		if (guild == null) {
			throw new NotFoundResponse("Guild not found.");
		}

		List<Map<String, Object>> data = new ArrayList<>();
		guild.getChannels().forEach(channel -> {
			Map<String, Object> map = new HashMap<>();
			
			map.put("id", channel.getIdLong());
			map.put("name", channel.getName());
			map.put("type_id", channel.getType().getId());
			
			data.add(map);
		});

		// respond
		try {
			ctx.json(new ObjectMapper().writeValueAsString(data));
		} catch (JsonProcessingException ex) {
			throw new InternalServerErrorResponse("Unable to parse channels data. "+ex.getMessage());
		}
	}

}
