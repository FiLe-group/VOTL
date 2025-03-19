package dev.fileeditor.votl.commands.owner;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.List;

import dev.fileeditor.votl.base.command.SlashCommand;
import dev.fileeditor.votl.base.command.SlashCommandEvent;
import dev.fileeditor.votl.commands.CommandBase;
import dev.fileeditor.votl.objects.constants.CmdCategory;
import dev.fileeditor.votl.objects.constants.Constants;

import dev.fileeditor.votl.utils.CommandsJson;
import net.dv8tion.jda.api.utils.FileUpload;

import org.json.JSONArray;

public class GenerateListCmd extends CommandBase {
	
	public GenerateListCmd() {
		this.name = "generate";
		this.path = "bot.owner.generate";
		this.category = CmdCategory.OWNER;
		this.ownerCommand = true;
	}

	@Override
	protected void execute(SlashCommandEvent event) {
		event.deferReply(true).queue();
		List<SlashCommand> commands = event.getClient().getSlashCommands();
		if (commands.isEmpty()) {
			editError(event, "Commands not found");
			return;
		}

		JSONArray commandArray = CommandsJson.getJson(bot, commands);

		File file = new File(Constants.DATA_PATH + "commands.json");
		try {
			boolean ignored = file.createNewFile();
			FileWriter writer = new FileWriter(file, StandardCharsets.UTF_8);
			writer.write(commandArray.toString());
			writer.flush();
			writer.close();
			event.getHook().editOriginalAttachments(FileUpload.fromData(file, "commands.json")).queue(hook -> {
				boolean ignored2 = file.delete();
			});
		} catch (IOException | UncheckedIOException ex) {
			editError(event, path+".error", ex.getMessage());
		}
	}

}
