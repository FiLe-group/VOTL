package dev.fileeditor.votl.utils;

import dev.fileeditor.votl.App;
import dev.fileeditor.votl.base.command.Category;
import dev.fileeditor.votl.base.command.SlashCommand;
import dev.fileeditor.votl.utils.file.lang.LocaleUtil;
import net.dv8tion.jda.api.interactions.DiscordLocale;
import org.jetbrains.annotations.Nullable;
import org.json.JSONArray;
import org.json.JSONObject;

import java.util.*;

public class CommandsJson {
	private static JSONArray jsonArray;
	private static HashMap<String, String> emptyLocales;

	@Nullable
	public static JSONArray getCommands() {
		return jsonArray;
	}

	public static JSONArray getJson(App bot, List<SlashCommand> commands) {
		if (jsonArray == null) {
			jsonArray = generateJson(bot, commands);
		}
		return jsonArray;
	}

	private static JSONArray generateJson(App bot, List<SlashCommand> commands) {
		JSONArray commandArray = new JSONArray();
		for (SlashCommand cmd : commands) {
			if (cmd.isOwnerCommand()) continue;

			JSONObject jsonObject = new JSONObject();
			jsonObject.put("name", cmd.getName())
				.put("description", getText(cmd.getHelpPath()))
				.put("category", getCategoryMap(bot, cmd.getCategory()))
				.put("guildOnly", cmd.isGuildOnly())
				.put("access", cmd.getAccessLevel().getLevel());

			if (cmd.getModule() == null) {
				jsonObject.put("module", getEmptyLocales(bot));
			} else {
				jsonObject.put("module", getText(cmd.getModule().getPath()));
			}

			if (cmd.getChildren().length > 0) {
				List<Map<String, Object>> values = new ArrayList<>();
				for (SlashCommand child : cmd.getChildren()) {
					values.add(Map.of("description", getText(child.getHelpPath()), "usage", getText(child.getUsagePath())));
				}
				jsonObject.put("child", values);
				jsonObject.put("usage", getEmptyLocales(bot));
			} else {
				jsonObject.put("child", Collections.emptyList());
				jsonObject.put("usage", getText(cmd.getUsagePath()));
			}

			commandArray.put(jsonObject);
		}

		return commandArray;
	}

	private static HashMap<String, String> getEmptyLocales(App bot) {
		if (emptyLocales == null) {
			emptyLocales = new HashMap<>();
			bot.getFileManager().getLanguages()
				.forEach((lang) -> emptyLocales.put(lang.getLocale(), ""));
		}
		return emptyLocales;
	}

	private static Map<String, Object> getCategoryMap(App bot, Category category) {
		if (category == null) {
			HashMap<String, Object> map = new HashMap<>(getEmptyLocales(bot));
			map.put("name", "");
			return map;
		}
		Map<String, Object> map = new HashMap<>();
		map.put("name", category.name());
		map.putAll(getText("bot.help.command_menu.categories."+category.name()));
		return map;
	}

	private static Map<String, Object> getText(String path) {
		final App bot = App.getInstance();
		final LocaleUtil lu = bot.getLocaleUtil();
		Map<String, Object> map = new HashMap<>();
		for (DiscordLocale locale : bot.getFileManager().getLanguages()) {
			map.put(locale.getLocale(), lu.getLocalized(locale, path));
		}
		return map;
	}
}
