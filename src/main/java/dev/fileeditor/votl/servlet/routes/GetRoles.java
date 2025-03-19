package dev.fileeditor.votl.servlet.routes;

import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import com.fasterxml.jackson.databind.ObjectMapper;

import net.dv8tion.jda.api.entities.Guild;

import io.javalin.http.Context;
import io.javalin.http.Handler;
import org.jetbrains.annotations.NotNull;

public class GetRoles implements Handler {
	@Override
	public void handle(@NotNull Context ctx) throws Exception {
		final Guild guild = Checks.getGuild(ctx);

		ObjectMapper mapper = new ObjectMapper();
		ArrayNode roleArray = mapper.createArrayNode();

		guild.getRoles().forEach(role -> {
			ObjectNode roleNode = mapper.createObjectNode();

			roleNode.put("id", role.getIdLong());
			roleNode.put("name", role.getName());
			roleNode.put("color", role.getColorRaw());
			roleNode.put("position", role.getPosition());

			roleArray.add(roleNode);
		});

		// respond
		ctx.json(roleArray);
	}
}
