package dev.fileeditor.votl.servlet.routes;

import dev.fileeditor.votl.App;
import dev.fileeditor.votl.objects.CmdModule;
import dev.fileeditor.votl.servlet.WebServlet;
import dev.fileeditor.votl.utils.database.managers.GuildSettingsManager;
import io.javalin.http.*;
import net.dv8tion.jda.api.entities.Guild;

import static dev.fileeditor.votl.servlet.routes.Checks.checkPermissionsAsync;

public class PutModule implements Handler {
	@Override
	public void handle(Context ctx) throws Exception {
		long id = ctx.pathParamAsClass("guild", Long.class)
			.getOrThrow(e -> new BadRequestResponse("Incorrect guild ID provided."));

		Guild guild = App.getInstance().JDA.getGuildById(id);
		if (guild == null) {
			throw new NotFoundResponse("Guild not found.");
		}

		// Check if module exists by name
		final String moduleName = ctx.pathParamAsClass("module", String.class)
			.check(CmdModule::exists, "Incorrect module name provided.")
			.get();
		final CmdModule module = CmdModule.valueOf(moduleName.toUpperCase());

		// Check if disabled
		GuildSettingsManager.GuildSettings settings = App.getInstance().getDBUtil().getGuildSettings(guild);
		if (!settings.isDisabled(module)) {
			throw new ConflictResponse("Module '%s' is already enabled.".formatted(module));
		}

		ctx.future(() -> {
			return checkPermissionsAsync(ctx, guild, (member) -> {
				// Write new data
				final int newData = settings.getModulesOff() - module.getValue();
				App.getInstance().getDBUtil().guildSettings.setModuleDisabled(guild.getIdLong(), newData);
				// Log
				App.getInstance().getLogger().server.onModuleEnabled(guild, member.getUser(), module);
				// Reply
				ctx.status(HttpStatus.NO_CONTENT);
			});
		});
	}
}
