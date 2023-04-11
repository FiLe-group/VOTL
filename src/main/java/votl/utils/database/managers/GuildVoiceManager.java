package votl.utils.database.managers;

import java.util.List;

import votl.utils.database.DBBase;
import votl.utils.database.DBUtil;

public class GuildVoiceManager extends DBBase {

	public GuildVoiceManager(DBUtil util) {
		super(util);
	}

	
	public boolean exists(String guildId) {
		if (select("guildVoice", "guildId", "guildId", guildId).isEmpty()) return false;
		return true;
	}

	public void setup(String guildId, String categoryId, String channelId) {
		if (exists(guildId)) {
			update("guildVoice", List.of("categoryId", "channelId"), List.of(categoryId, channelId), "guildId", guildId);
		} else {
			insert("guildVoice", List.of("guildId", "categoryId", "channelId"), List.of(guildId, categoryId, channelId));
		}
	}

	public void setName(String guildId, String defaultName) {
		update("guildVoice", "defaultName", defaultName, "guildId", guildId);
	}

	public void setLimit(String guildId, Integer defaultLimit) {
		update("guildVoice", "defaultLimit", defaultLimit, "guildId", guildId);
	}

	public String getCategory(String guildId) {
		List<Object> objs = select("guildVoice", "categoryId", "guildId", guildId);
		if (objs.isEmpty() || objs.get(0) == null) return null;
		return String.valueOf(objs.get(0));
	}

	public String getChannel(String guildId) {
		List<Object> objs = select("guildVoice", "channelId", "guildId", guildId);
		if (objs.isEmpty() || objs.get(0) == null) return null;
		return String.valueOf(objs.get(0));
	}

	public String getName(String guildId) {
		List<Object> objs = select("guildVoice", "defaultName", "guildId", guildId);
		if (objs.isEmpty() || objs.get(0) == null) return null;
		return String.valueOf(objs.get(0));
	}

	public Integer getLimit(String guildId) {
		List<Object> objs = select("guildVoice", "defaultLimit", "guildId", guildId);
		if (objs.isEmpty() || objs.get(0) == null) return null;
		return Integer.parseInt(String.valueOf(objs.get(0)));
	}

}
