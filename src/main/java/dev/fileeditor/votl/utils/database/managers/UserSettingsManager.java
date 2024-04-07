package dev.fileeditor.votl.utils.database.managers;

import java.util.List;

import dev.fileeditor.votl.utils.database.DBUtil;
import dev.fileeditor.votl.utils.database.SQLiteDBBase;

public class UserSettingsManager extends SQLiteDBBase {

	private final String table = "user";

	public UserSettingsManager(DBUtil util) {
		super(util);
	}

	public void add(String userId) {
		insert(table, "userId", userId);
	}

	public void remove(String userId) {
		delete(table, "userId", userId);
	}

	public boolean exists(String userId) {
		if (select(table, "userId", "userId", userId).isEmpty()) return false;
		return true;
	}

	public void setName(String userId, String channelName) {
		update(table, "voiceName", channelName, "userId", userId);
	}

	public void setLimit(String userId, Integer channelLimit) {
		update(table, "voiceLimit", channelLimit, "userId", userId);
	}

	public String getName(String userId) {
		List<Object> objs = select(table, "voiceName", "userId", userId);
		if (objs.isEmpty() || objs.get(0) == null) return null;
		return String.valueOf(objs.get(0));
	}

	public Integer getLimit(String userId) {
		List<Object> objs = select(table, "voiceLimit", "userId", userId);
		if (objs.isEmpty() || objs.get(0) == null) return null;
		return Integer.parseInt(String.valueOf(objs.get(0)));
	}

}
