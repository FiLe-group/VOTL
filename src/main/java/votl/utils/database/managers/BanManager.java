package votl.utils.database.managers;

import java.sql.Timestamp;
import java.time.Duration;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import votl.utils.database.DBBase;
import votl.utils.database.DBUtil;

public class BanManager extends DBBase {
	
	public BanManager(DBUtil util) {
		super(util);
	}

	// add new ban
	public void add(String userId, String userName, String modId, String modName, String guildId, String reason, Timestamp timeStart, Duration duration, boolean synced) {
		insert("ban", List.of("userId", "userNickname", "modId", "modNickname", "guildId", "reason", "timeStart", "duration", "synced"),
			List.of(userId, userName, modId, modName, guildId, reason, timeStart.toString(), duration.toString(), (synced ? 1 : 0)));
	}

	// remove existing ban
	public void remove(String banId) {
		delete("ban", "banId", banId);
	}

	// update ban reason
	public void updateReason(String banId, String reason) {
		update("ban", "reason", reason, "banId", banId);
	}

	// update ban duration
	public void updateDuration(String banId, Integer duration) {
		update("ban", "duration", duration, "banId", banId);
	}

	public Map<String, Object> banGetInfo(String banId) {
		List<Map<String, Object>> banDataList = select("ban", List.of(), "banId", banId);
		if (banDataList.isEmpty() || banDataList.get(0) == null) {
			return Collections.emptyMap();
		}
		return banDataList.get(0);
	}

	// get all bans in guild
	public List<Map<String, Object>> banGetGuildAll(String guildId) {
		List<Map<String, Object>> banDataList = select("ban", List.of(), "guildId", guildId);
		if (banDataList.isEmpty() || banDataList.get(0) == null) {
			return Collections.emptyList();
		}
		return banDataList;
	}

	// get all bans in guild by mod
	public List<Map<String, Object>> banGetGuildMod(String guildId, String modId) {
		List<Map<String, Object>> banDataList = select("ban", List.of(), List.of("guildId", "modId"), List.of(guildId, modId));
		if (banDataList.isEmpty() || banDataList.get(0) == null) {
			return Collections.emptyList();
		}
		return banDataList;
	}

	// get all bans in guild for user
	public List<Map<String, Object>> banGetGuildUser(String guildId, String userId) {
		List<Map<String, Object>> banDataList = select("ban", List.of(), List.of("guildId", "userId"), List.of(guildId, userId));
		if (banDataList.isEmpty() || banDataList.get(0) == null) {
			return Collections.emptyList();
		}
		return banDataList;
	}

	// get ban start time and duration
	public Map<String, Object> banGetTime(String banId) {
		List<Map<String, Object>> banDataList = select("ban", List.of("timeStart", "duration"), "banId", banId);
		if (banDataList.isEmpty() || banDataList.get(0) == null) {
			return Collections.emptyMap();
		}
		return banDataList.get(0);
	}

	// is ban active
	public boolean banIsActive(String banId) {
		List<Object> objs = select("ban", "active", "banId", banId);
		if (objs.isEmpty() || objs.get(0) == null) {
			return false;
		}
		if (objs.get(0).equals(1)) {
			return true;
		}
		return false;
	}

	// set ban in-active
	public void banSetInactive(String banId) {
		update("ban", "active", 0, "banId", banId);
	}

	// is ban synced
	public boolean banIsSynced(String banId) {
		List<Object> objs = select("ban", "synced", "banId", banId);
		if (objs.isEmpty() || objs.get(0) == null) {
			return false;
		}
		if (objs.get(0).equals(1)) {
			return true;
		}
		return false;
	}

	// set ban synced
	public void banSetSynced(String banId) {
		update("ban", "synced", 1, "banId", banId);
	}
}
