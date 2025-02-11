package dev.fileeditor.votl.servlet.routes;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import dev.fileeditor.votl.App;
import dev.fileeditor.votl.objects.CmdModule;
import dev.fileeditor.votl.servlet.WebServlet;

import io.javalin.http.BadRequestResponse;
import io.javalin.http.Context;
import io.javalin.http.Handler;
import io.javalin.http.NotFoundResponse;
import net.dv8tion.jda.api.entities.Guild;

import static dev.fileeditor.votl.servlet.routes.Checks.checkPermissionsAsync;

public class PatchModule implements Handler {
	@Override
	public void handle(Context ctx) throws Exception {
		// Check if guild id is correct
		long id = ctx.pathParamAsClass("guild", Long.class)
			.getOrThrow(e -> new BadRequestResponse("Incorrect guild ID provided."));

		Guild guild = App.getInstance().JDA.getGuildById(id);
		if (guild == null) {
			throw new NotFoundResponse("Guild not found.");
		}

		// Check if module exists by name
		String moduleName = ctx.pathParamAsClass("module", String.class)
			.check(CmdModule::exists, "Incorrect module name provided.").get();
		final CmdModule module = CmdModule.valueOf(moduleName.toUpperCase());

		// Check if disabled
		if (App.getInstance().getDBUtil().getGuildSettings(guild).isDisabled(module)) {
			throw new NotFoundResponse("Module " + moduleName + " is disabled.");
		}

		// Check for permission
		ctx.future(() -> {
			return checkPermissionsAsync(WebServlet.getSession(ctx.cookieStore()), guild, (member) -> {
				// TODO add other modules
				switch (module) {
					case REPORT -> {
						ReportModule data = ctx.bodyValidator(ReportModule.class)
							.get();

						ObjectNode moduleNode = new ObjectMapper().createObjectNode();

						moduleNode.put("channel", 0L);
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
			});
		});

	}

	private static class ReportModule {
		private final Long channelId;
		private final String message;
		private final boolean temp;

		public ReportModule() {
			this.channelId = null;
			this.message = null;
			this.temp = false;
		}

		public ReportModule(Long channelId, String message, boolean temp) {
			this.channelId = channelId;
			this.message = message;
			this.temp = temp;
		}

		public Long getChannelId() {
			return channelId;
		}

		public String getMessage() {
			return message;
		}

		public boolean isTemp() {
			return temp;
		}
	}
}
