package votl.utils.database.managers;

import java.util.List;

import votl.utils.database.DBBase;
import votl.utils.database.DBUtil;

public class UserSettingsManager extends DBBase {

	public UserSettingsManager(DBUtil util) {
		super(util);
	}

	public void add(String userId) {
		insert("user", "userId", userId);
	}

	public void remove(String userId) {
		delete("user", "userId", userId);
	}

	public boolean exists(String userId) {
		if (select("user", "userId", "userId", userId).isEmpty()) {
			return false;
		}
		return true;
	}

	public void setName(String userId, String channelName) {
		update("user", "voiceName", channelName, "userId", userId);
	}

	public void setLimit(String userId, Integer channelLimit) {
		update("user", "voiceLimit", channelLimit, "userId", userId);
	}

	public String getName(String userId) {
		List<Object> objs = select("user", "voiceName", "userId", userId);
		if (objs.isEmpty() || objs.get(0) == null) {
			return null;
		}
		return String.valueOf(objs.get(0));
	}

	public Integer getLimit(String userId) {
		List<Object> objs = select("user", "voiceLimit", "userId", userId);
		if (objs.isEmpty() || objs.get(0) == null) {
			return null;
		}
		return Integer.parseInt(String.valueOf(objs.get(0)));
	}

}
