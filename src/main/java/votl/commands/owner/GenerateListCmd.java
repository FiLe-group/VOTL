package votl.commands.owner;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import votl.App;
import votl.commands.CommandBase;
import votl.objects.command.SlashCommand;
import votl.objects.command.SlashCommandEvent;
import votl.objects.constants.CmdCategory;
import votl.objects.constants.Constants;

import net.dv8tion.jda.api.interactions.DiscordLocale;
import net.dv8tion.jda.api.utils.FileUpload;

import net.minidev.json.JSONObject;

public class GenerateListCmd extends CommandBase {
	
	public GenerateListCmd(App bot) {
		super(bot);
		this.name = "generate";
		this.path = "bot.owner.generate";
		this.category = CmdCategory.OWNER;
		this.ownerCommand = true;
		this.guildOnly = false;
	}

	@Override
	protected void execute(SlashCommandEvent event) {
		List<SlashCommand> commands = event.getClient().getSlashCommands();
		if (commands.size() == 0) {
			return;
		}

		JSONObject result = new JSONObject();
		for (Integer i = 0; i < commands.size(); i++) {
			SlashCommand cmd = commands.get(i);

			JSONObject jsonObject = new JSONObject();
			jsonObject.appendField("name", cmd.getName())
				.appendField("description", getText(cmd.getHelpPath()))
				.appendField("category", getCategoryMap(cmd.getCategory()))
				.appendField("guildOnly", cmd.isGuildOnly())
				.appendField("access", cmd.getAccessLevel().getLevel());

			if (cmd.getModule() == null) {
				jsonObject.appendField("module", Map.of("en-GB", "", "ru", ""));
			} else {
				jsonObject.appendField("module", getText(cmd.getModule().getPath()));
			}
			
			if (cmd.getChildren().length > 0) {
				List<Map<String, Object>> values = new ArrayList<>();
				for (SlashCommand child : cmd.getChildren()) {
					values.add(Map.of("description", getText(child.getHelpPath()), "usage", getText(child.getUsagePath())));
				}
				jsonObject.appendField("child", values);
				jsonObject.appendField("usage", Map.of("en-GB", "", "ru", ""));
			} else {
				jsonObject.appendField("child", Collections.emptyList());
				jsonObject.appendField("usage", getText(cmd.getUsagePath()));
			}
			
			result.appendField(i.toString(), jsonObject);
		}

		File file = new File(Constants.DATA_PATH + Constants.SEPAR + "commands.json");
		try {
			file.createNewFile();
			FileWriter writer = new FileWriter(file, Charset.forName("utf-8"));
			writer.write(result.toJSONString());
			writer.flush();
			writer.close();
			event.replyFiles(FileUpload.fromData(file, "commands.json")).setEphemeral(true).queue(hook -> file.delete());
		} catch (IOException | UncheckedIOException ex) {
			createError(event, path+".error", ex.getMessage());
		}
	}

	private Map<String, Object> getCategoryMap(Category category) {
		if (category == null) {
			return Map.of("name", "", "en-GB", "", "ru", "");
		}
		Map<String, Object> map = new HashMap<>();
		map.put("name", category.getName());
		map.putAll(getText("bot.help.command_menu.categories."+category.getName()));
		return map;
	}

	private Map<String, Object> getText(String path) {
		Map<String, Object> map = new HashMap<>();
		for (DiscordLocale locale : bot.getFileManager().getLanguages()) {
			map.put(locale.getLocale(), lu.getLocalized(locale, path));
		}
		return map;
	}

}
