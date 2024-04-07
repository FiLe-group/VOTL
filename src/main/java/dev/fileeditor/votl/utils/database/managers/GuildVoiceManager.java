package dev.fileeditor.votl.utils.database.managers;

import java.util.List;

import dev.fileeditor.votl.utils.database.DBUtil;
import dev.fileeditor.votl.utils.database.SQLiteDBBase;

public class GuildVoiceManager extends SQLiteDBBase {

	private final String table = "guildVoice";

	public GuildVoiceManager(DBUtil util) {
		super(util);
	}
	
	public boolean exists(String guildId) {
		if (select(table, "guildId", "guildId", guildId).isEmpty()) return false;
		return true;
	}

	public void setup(String guildId, String categoryId, String channelId) {
		if (exists(guildId)) {
			update(table, List.of("categoryId", "channelId"), List.of(categoryId, channelId), "guildId", guildId);
		} else {
			insert(table, List.of("guildId", "categoryId", "channelId"), List.of(guildId, categoryId, channelId));
		}
	}

	public void setName(String guildId, String defaultName) {
		update(table, "defaultName", defaultName, "guildId", guildId);
	}

	public void setLimit(String guildId, Integer defaultLimit) {
		update(table, "defaultLimit", defaultLimit, "guildId", guildId);
	}

	public String getCategory(String guildId) {
		List<Object> objs = select(table, "categoryId", "guildId", guildId);
		if (objs.isEmpty() || objs.get(0) == null) return null;
		return String.valueOf(objs.get(0));
	}

	public String getChannel(String guildId) {
		List<Object> objs = select(table, "channelId", "guildId", guildId);
		if (objs.isEmpty() || objs.get(0) == null) return null;
		return String.valueOf(objs.get(0));
	}

	public String getName(String guildId) {
		List<Object> objs = select(table, "defaultName", "guildId", guildId);
		if (objs.isEmpty() || objs.get(0) == null) return null;
		return String.valueOf(objs.get(0));
	}

	public Integer getLimit(String guildId) {
		List<Object> objs = select(table, "defaultLimit", "guildId", guildId);
		if (objs.isEmpty() || objs.get(0) == null) return null;
		return Integer.parseInt(String.valueOf(objs.get(0)));
	}

}
