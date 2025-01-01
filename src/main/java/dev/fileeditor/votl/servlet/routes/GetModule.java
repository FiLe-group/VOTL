package dev.fileeditor.votl.servlet.routes;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import dev.fileeditor.votl.App;
import dev.fileeditor.votl.objects.CmdModule;
import io.javalin.http.BadRequestResponse;
import io.javalin.http.Context;
import io.javalin.http.Handler;
import io.javalin.http.NotFoundResponse;
import net.dv8tion.jda.api.entities.Guild;

public class GetModule implements Handler {
	@Override
	public void handle(Context ctx) throws Exception {
		// Check if guild id is correct
		long id = ctx.pathParamAsClass("guild", Long.class)
			.getOrThrow(e -> new BadRequestResponse("Incorrect guild ID provided."));

		Guild guild = App.getInstance().JDA.getGuildById(id);
		if (guild == null) {
			throw new NotFoundResponse("Guild not found.");
		}

		String moduleName = ctx.pathParamAsClass("module", String.class)
			.check(CmdModule::exists, "Incorrect module name provided.").get();
		final CmdModule module = CmdModule.valueOf(moduleName.toUpperCase());

		// Check if disabled
		if (App.getInstance().getDBUtil().getGuildSettings(guild).isDisabled(module)) {
			throw new NotFoundResponse("Module " + moduleName + " is disabled.");
		}

		// TODO add other modules
		switch (module) {
			case REPORT -> {
				ObjectNode moduleNode = new ObjectMapper().createObjectNode();

				moduleNode.put("channel", "");
				moduleNode.put("message", "");
				moduleNode.put("temp", true);

				// Send response
				ctx.json(moduleNode);
			}
			default ->  {
				ObjectNode moduleNode = new ObjectMapper().createObjectNode();
				ctx.json(moduleNode);
			}
		}
	}
}
