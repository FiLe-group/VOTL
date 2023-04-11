package votl.utils.database.managers;

import java.util.List;

import votl.utils.database.DBBase;
import votl.utils.database.DBUtil;

public class VoiceChannelManager extends DBBase {
	
	public VoiceChannelManager(DBUtil util) {
		super(util);
	}

	public void add(String userId, String channelId) {
		insert("voiceChannel", List.of("userId", "channelId"), List.of(userId, channelId));
	}

	public void remove(String channelId) {
		delete("voiceChannel", "channelId", channelId);
	}

	public boolean existsUser(String userId) {
		if (select("voiceChannel", "userId", "userId", userId).isEmpty()) {
			return false;
		}
		return true;
	}

	public boolean existsChannel(String channelId) {
		if (select("voiceChannel", "channelId", "channelId", channelId).isEmpty()) return false;
		return true;
	}

	public void setUser(String userId, String channelId) {
		update("voiceChannel", "userId", userId, "channelId", channelId);
	}

	public String getChannel(String userId) {
		List<Object> objs = select("voiceChannel", "channelId", "userId", userId);
		if (objs.isEmpty() || objs.get(0) == null) return null;
		return String.valueOf(objs.get(0));
	}

	public String getUser(String channelId) {
		List<Object> objs = select("voiceChannel", "userId", "channelId", channelId);
		if (objs.isEmpty() || objs.get(0) == null) return null;
		return String.valueOf(objs.get(0));
	}

}
