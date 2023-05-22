package votl.utils.database.managers;

import java.util.List;

import votl.utils.database.SQLiteDBBase;
import votl.utils.database.DBUtil;

public class GuildSettingsManager extends SQLiteDBBase {

	private final String table = "guild";
	
	public GuildSettingsManager(DBUtil util) {
		super(util);
	}

	public boolean add(String guildId) {
		if (!exists(guildId)) {
			insert(table, "guildId", guildId);
			return true;
		}
		return false;
	}

	public void remove(String guildId) {
		delete("guild", "guildId", guildId);
		delete("guildVoice", "guildId", guildId);
	}

	public boolean exists(String guildId) {
		if (select(table, "guildId", "guildId", guildId).isEmpty()) return false;
		return true;
	}

	public void setModLogChannel(String guildId, String channelId) {
		update(table, "modLogId", channelId, "guildId", guildId);
	}

	public void setGroupLogChannel(String guildId, String channelId) {
		update(table, "groupLogId", channelId, "guildId", guildId);
	}

	public void setVerifyLogChannel(String guildId, String channelId) {
		update(table, "verifyLogId", channelId, "guildId", guildId);
	}

	public String getModLogChannel(String guildId) {
		List<Object> objs = select(table, "modLogId", "guildId", guildId);
		if (objs.isEmpty() || objs.get(0) == null) return null;
		return String.valueOf(objs.get(0));
	}

	public String getGroupLogChannel(String guildId) {
		List<Object> objs = select(table, "groupLogId", "guildId", guildId);
		if (objs.isEmpty() || objs.get(0) == null) return null;
		return String.valueOf(objs.get(0));
	}

	public String getVerifyLogChannel(String guildId) {
		List<Object> objs = select(table, "verifyLogId", "guildId", guildId);
		if (objs.isEmpty() || objs.get(0) == null) return null;
		return String.valueOf(objs.get(0));
	}

}
