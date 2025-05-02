package dev.fileeditor.votl.servlet.routes;

import dev.fileeditor.oauth2.session.Session;
import dev.fileeditor.votl.App;
import dev.fileeditor.votl.objects.CmdModule;
import dev.fileeditor.votl.utils.database.managers.GuildSettingsManager;
import io.javalin.http.*;
import net.dv8tion.jda.api.entities.Guild;
import org.jetbrains.annotations.NotNull;

import static dev.fileeditor.votl.servlet.routes.Checks.checkPermissionsAsync;

public class DeleteModule implements Handler {
	@Override
	public void handle(@NotNull Context ctx) throws Exception {
		final Session session = Checks.getSession(ctx);
		final Guild guild = Checks.getGuild(ctx);

		final CmdModule module = Checks.getModule(ctx);

		// Check if disabled
		GuildSettingsManager.GuildSettings settings = App.getInstance().getDBUtil().getGuildSettings(guild);
		if (settings.isDisabled(module)) {
			throw new ConflictResponse("Module '%s' is already disabled.".formatted(module));
		}

		ctx.future(() -> checkPermissionsAsync(session, guild, (member) -> {
			// Write new data
			final int newData = settings.getModulesOff() + module.getValue();
			App.getInstance().getDBUtil().guildSettings.setModuleDisabled(guild.getIdLong(), newData);
			// Log
			App.getInstance().getLogger().server.onModuleDisabled(guild, member.getUser(), module);
			// Reply
			ctx.status(HttpStatus.NO_CONTENT);
		}));
	}
}
