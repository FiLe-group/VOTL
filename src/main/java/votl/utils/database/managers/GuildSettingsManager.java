package votl.utils.database.managers;

import java.util.List;

import votl.utils.database.DBBase;
import votl.utils.database.DBUtil;

public class GuildSettingsManager extends DBBase {
	
	public GuildSettingsManager(DBUtil util) {
		super(util);
	}

	public boolean add(String guildId) {
		if (!exists(guildId)) {
			insert("guild", "guildId", guildId);
			return true;
		}
		return false;
	}

	public void remove(String guildId) {
		delete("guild", "guildId", guildId);
		delete("guildVoice", "guildId", guildId);
	}

	public boolean exists(String guildId) {
		if (select("guild", "guildId", "guildId", guildId).isEmpty()) {
			return false;
		}
		return true;
	}

	public void setLanguage(String guildId, String language) {
		update("guild", "language", language, "guildId", guildId);
	}

	public String getLanguage(String guildId) {
		List<Object> objs = select("guild", "language", "guildId", guildId);
		if (objs.isEmpty() || objs.get(0) == null) {
			return null;
		}
		return String.valueOf(objs.get(0));
	}

	public void setModLogChannel(String guildId, String channelId) {
		update("guild", "modLogId", channelId, "guildId", guildId);
	}

	public void setGroupLogChannel(String guildId, String channelId) {
		update("guild", "groupLogId", channelId, "guildId", guildId);
	}

	public String getModLogChannel(String guildId) {
		List<Object> objs = select("guild", "modLogId", "guildId", guildId);
		if (objs.isEmpty() || objs.get(0) == null) {
			return null;
		}
		return String.valueOf(objs.get(0));
	}

	public String getGroupLogChannel(String guildId) {
		List<Object> objs = select("guild", "groupLogId", "guildId", guildId);
		if (objs.isEmpty() || objs.get(0) == null) {
			return null;
		}
		return String.valueOf(objs.get(0));
	}

}
