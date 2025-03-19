package dev.fileeditor.votl.servlet.routes;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import dev.fileeditor.votl.App;
import dev.fileeditor.votl.objects.CmdModule;
import io.javalin.http.*;
import net.dv8tion.jda.api.entities.Guild;
import org.jetbrains.annotations.NotNull;

public class GetModule implements Handler {
	@Override
	public void handle(@NotNull Context ctx) throws Exception {
		final Guild guild = Checks.getGuild(ctx);

		final CmdModule module = Checks.getModule(ctx);

		final boolean enabled = !App.getInstance().getDBUtil().getGuildSettings(guild).isDisabled(module);

		// TODO add other modules
		switch (module) {
			case REPORT -> {
				ObjectNode moduleNode = new ObjectMapper().createObjectNode();

				moduleNode.put("channel", "");
				moduleNode.put("message", "");
				moduleNode.put("temp", true);
				moduleNode.put("enabled", enabled);

				// Send response
				ctx.json(moduleNode);
			}
			default ->  {
				ObjectNode moduleNode = new ObjectMapper().createObjectNode();

				moduleNode.put("enabled", enabled);

				ctx.json(moduleNode);
			}
		}
	}
}
