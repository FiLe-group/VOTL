package dev.fileeditor.votl.servlet.routes;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import dev.fileeditor.oauth2.session.Session;
import dev.fileeditor.votl.App;
import dev.fileeditor.votl.objects.CmdModule;

import io.javalin.http.*;
import net.dv8tion.jda.api.entities.Guild;
import org.jetbrains.annotations.NotNull;

import static dev.fileeditor.votl.servlet.routes.Checks.checkPermissionsAsync;

public class PatchModule implements Handler {
	@Override
	public void handle(@NotNull Context ctx) throws Exception {
		final Session session = Checks.getSession(ctx);
		final Guild guild = Checks.getGuild(ctx);

		final CmdModule module = Checks.getModule(ctx);

		// Check if disabled
		if (App.getInstance().getDBUtil().getGuildSettings(guild).isDisabled(module)) {
			throw new ConflictResponse("Module '%s' is disabled.".formatted(module));
		}

		// Check for permission
		ctx.future(() -> checkPermissionsAsync(session, guild, (member) -> {
			// TODO add other modules
			switch (module) {
				case REPORT -> {
					ReportModule data = ctx.bodyValidator(ReportModule.class)
						.getOrThrow(e -> new BadRequestResponse("Incorrect module name provided."));

					ObjectNode moduleNode = new ObjectMapper().createObjectNode();

					moduleNode.put("channel", 0L); // TODO temp
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
		}));
	}

	private record ReportModule(Long channelId, String message, boolean temp) {}
}
