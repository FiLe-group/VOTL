package dev.fileeditor.votl.utils.database.managers;

import java.util.List;

import dev.fileeditor.votl.utils.database.DBUtil;
import dev.fileeditor.votl.utils.database.SQLiteDBBase;

public class VoiceChannelManager extends SQLiteDBBase {

	private final String table = "voiceChannel";
	
	public VoiceChannelManager(DBUtil util) {
		super(util);
	}

	public void add(String userId, String channelId) {
		insert(table, List.of("userId", "channelId"), List.of(userId, channelId));
	}

	public void remove(String channelId) {
		delete(table, "channelId", channelId);
	}

	public boolean existsUser(String userId) {
		if (select(table, "userId", "userId", userId).isEmpty()) {
			return false;
		}
		return true;
	}

	public boolean existsChannel(String channelId) {
		if (select(table, "channelId", "channelId", channelId).isEmpty()) return false;
		return true;
	}

	public void setUser(String userId, String channelId) {
		update(table, "userId", userId, "channelId", channelId);
	}

	public String getChannel(String userId) {
		List<Object> objs = select(table, "channelId", "userId", userId);
		if (objs.isEmpty() || objs.get(0) == null) return null;
		return String.valueOf(objs.get(0));
	}

	public String getUser(String channelId) {
		List<Object> objs = select(table, "userId", "channelId", channelId);
		if (objs.isEmpty() || objs.get(0) == null) return null;
		return String.valueOf(objs.get(0));
	}

}
