package dev.fileeditor.votl.servlet.routes;

import dev.fileeditor.votl.App;
import dev.fileeditor.votl.base.command.SlashCommand;
import dev.fileeditor.votl.utils.CommandsJson;
import io.javalin.http.Context;
import io.javalin.http.Handler;
import io.javalin.http.InternalServerErrorResponse;
import org.jetbrains.annotations.NotNull;
import org.json.JSONArray;

import java.util.List;

public class ApiCommands implements Handler {
	@Override
	public void handle(@NotNull Context ctx) throws Exception {
		App bot = App.getInstance();
		List<SlashCommand> commands = bot.getClient().getSlashCommands();
		if (commands.isEmpty()) {
			throw new InternalServerErrorResponse("No commands available.");
		}

		JSONArray jsonArray = CommandsJson.getJson(bot, commands);
		ctx.json(jsonArray.toString());
	}
}
