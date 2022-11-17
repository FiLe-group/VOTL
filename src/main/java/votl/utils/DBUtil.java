package votl.utils;

import java.io.File;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import votl.objects.CmdModule;

import org.slf4j.LoggerFactory;

import ch.qos.logback.classic.Logger;

public class DBUtil {
	
	private final Logger logger = (Logger) LoggerFactory.getLogger(DBUtil.class);

	private String url;

	public DBUtil(File location) {
		this.url = "jdbc:sqlite:" + location;
	}

	private Connection connect() {
		Connection conn = null;
		try {
			conn = DriverManager.getConnection(url);
		} catch (SQLException ex) {
			logger.error("DB: Connection error to database", ex);
			return null;
		}
		return conn;
	}


	// Guild oweral
	public boolean guildAdd(String guildId) {
		if (!isGuild(guildId)) {
			insert("guild", "guildId", guildId);
			return true;
		}
		return false;
	}

	public void guildRemove(String guildId) {
		delete("guild", "guildId", guildId);
		delete("guildVoice", "guildId", guildId);
	}

	public boolean isGuild(String guildId) {
		if (select("guild", "guildId", "guildId", guildId).isEmpty()) {
			return false;
		}
		return true;
	}

	public boolean isGuildVoice(String guildId) {
		if (select("guildVoice", "guildId", "guildId", guildId).isEmpty()) {
			return false;
		}
		return true;
	}

	// Guild
	public void guildSetLanguage(String guildId, String language) {
		update("guild", "language", language, "guildId", guildId);
	}

	public String guildGetLanguage(String guildId) {
		List<Object> objs = select("guild", "language", "guildId", guildId);
		if (objs.isEmpty() || objs.get(0) == null) {
			return null;
		}
		return String.valueOf(objs.get(0));
	}
	
	// Guild Voice
	public void guildVoiceSetup(String guildId, String categoryId, String channelId) {
		if (isGuildVoice(guildId)) {
			update("guildVoice", List.of("categoryId", "channelId"), List.of(categoryId, channelId), "guildId", guildId);
		} else {
			insert("guildVoice", List.of("guildId", "categoryId", "channelId"), List.of(guildId, categoryId, channelId));
		}
	}

	public void guildVoiceSetName(String guildId, String defaultName) {
		update("guildVoice", "defaultName", defaultName, "guildId", guildId);
	}

	public void guildVoiceSetLimit(String guildId, Integer defaultLimit) {
		update("guildVoice", "defaultLimit", defaultLimit, "guildId", guildId);
	}

	public String guildVoiceGetCategory(String guildId) {
		List<Object> objs = select("guildVoice", "categoryId", "guildId", guildId);
		if (objs.isEmpty() || objs.get(0) == null) {
			return null;
		}
		return String.valueOf(objs.get(0));
	}

	public String guildVoiceGetChannel(String guildId) {
		List<Object> objs = select("guildVoice", "channelId", "guildId", guildId);
		if (objs.isEmpty() || objs.get(0) == null) {
			return null;
		}
		return String.valueOf(objs.get(0));
	}

	public String guildVoiceGetName(String guildId) {
		List<Object> objs = select("guildVoice", "defaultName", "guildId", guildId);
		if (objs.isEmpty() || objs.get(0) == null) {
			return null;
		}
		return String.valueOf(objs.get(0));
	}

	public Integer guildVoiceGetLimit(String guildId) {
		List<Object> objs = select("guildVoice", "defaultLimit", "guildId", guildId);
		if (objs.isEmpty() || objs.get(0) == null) {
			return null;
		}
		return Integer.parseInt(String.valueOf(objs.get(0)));
	}

	// User Settings
	public void userAdd(String userId) {
		insert("user", "userId", userId);
	}

	public void userRemove(String userId) {
		delete("user", "userId", userId);
	}

	public boolean isUser(String userId) {
		if (select("user", "userId", "userId", userId).isEmpty()) {
			return false;
		}
		return true;
	}

	public void userSetName(String userId, String channelName) {
		update("user", "voiceName", channelName, "userId", userId);
	}

	public void userSetLimit(String userId, Integer channelLimit) {
		update("user", "voiceLimit", channelLimit, "userId", userId);
	}

	public String userGetName(String userId) {
		List<Object> objs = select("user", "voiceName", "userId", userId);
		if (objs.isEmpty() || objs.get(0) == null) {
			return null;
		}
		return String.valueOf(objs.get(0));
	}

	public Integer userGetLimit(String userId) {
		List<Object> objs = select("user", "voiceLimit", "userId", userId);
		if (objs.isEmpty() || objs.get(0) == null) {
			return null;
		}
		return Integer.parseInt(String.valueOf(objs.get(0)));
	}

	// Voice Channel
	public void channelAdd(String userId, String channelId) {
		insert("voiceChannel", List.of("userId", "channelId"), List.of(userId, channelId));
	}

	public void channelRemove(String channelId) {
		delete("voiceChannel", "channelId", channelId);
	}

	public boolean isVoiceChannel(String userId) {
		if (select("voiceChannel", "userId", "userId", userId).isEmpty()) {
			return false;
		}
		return true;
	}

	public boolean isVoiceChannelExists(String channelId) {
		if (select("voiceChannel", "channelId", "channelId", channelId).isEmpty()) {
			return false;
		}
		return true;
	}

	public void channelSetUser(String userId, String channelId) {
		update("voiceChannel", "userId", userId, "channelId", channelId);
	}

	public String channelGetChannel(String userId) {
		List<Object> objs = select("voiceChannel", "channelId", "userId", userId);
		if (objs.isEmpty() || objs.get(0) == null) {
			return null;
		}
		return String.valueOf(objs.get(0));
	}

	public String channelGetUser(String channelId) {
		List<Object> objs = select("voiceChannel", "userId", "channelId", channelId);
		if (objs.isEmpty() || objs.get(0) == null) {
			return null;
		}
		return String.valueOf(objs.get(0));
	}

	// Webhook List
	public void webhookAdd(String webhookId, String guildId, String token) {
		insert("webhook", List.of("webhookId", "guildId", "token"), List.of(webhookId, guildId, token));
	}

	public void webhookRemove(String webhookId) {
		delete("webhook", "webhookId", webhookId);
	}

	public boolean webhookExists(String webhookId) {
		if (select("webhook", "webhookId", "webhookId", webhookId).isEmpty()) {
			return false;
		}
		return true;
	}

	public String webhookGetToken(String webhookId) {
		List<Object> objs = select("webhook", "token", "webhookId", webhookId);
		if (objs.isEmpty() || objs.get(0) == null) {
			return null;
		}
		return String.valueOf(objs.get(0));
	}

	public List<String> webhookGetIds(String guildId) {
		List<Object> objs = select("webhook", "webhookId", "guildId", guildId);
		if (objs.isEmpty()) {
			return Collections.emptyList();
		}
		return objs.stream().map(obj -> String.valueOf(obj)).collect(Collectors.toList());
	}

	// Module disable
	public void moduleAdd(String guildId, CmdModule module) {
		insert("moduleOff", List.of("guildId", "module"), List.of(guildId, module.toString()));
	}

	public void moduleRemove(String guildId, CmdModule module) {
		delete("moduleOff", List.of("guildId", "module"), List.of(guildId, module.toString()));
	}

	public List<CmdModule> modulesGet(String guildId) {
		List<Object> objs = select("moduleOff", "module", "guildId", guildId);
		if (objs.isEmpty()) {
			return Collections.emptyList();
		}
		return objs.stream().map(obj -> CmdModule.valueOf(String.valueOf(obj))).collect(Collectors.toList());
	}

	public boolean moduleDisabled(String guildId, CmdModule module) {
		if (select("moduleOff", "guildId", List.of("guildId", "module"), List.of(guildId, module.toString())).isEmpty()) {
			return false;
		}
		return true;
	}

	// Mod/Admin Access
	public void accessAdd(String guildId, String userId, boolean admin) {
		insert("modAccess", List.of("guildId", "userId", "admin"), List.of(guildId, userId, (admin ? 1 : 0)));
	}

	public void accessRemove(String guildId, String userId) {
		delete("modAccess", List.of("guildId", "userId"), List.of(guildId, userId));
	}

	public void accessChange(String guildId, String userId, boolean admin) {
		update("modAccess", "admin", (admin ? 1 : 0), List.of("guildId", "userId"), List.of(guildId, userId));
	}

	public List<String> accessAllGet(String guildId) {
		List<Object> objs = select("modAccess", "userId", "guildId", guildId);
		if (objs.isEmpty()) {
			return Collections.emptyList();
		}
		return objs.stream().map(obj -> String.valueOf(obj)).collect(Collectors.toList());
	}

	public List<String> accessModGet(String guildId) {
		List<Object> objs = select("modAccess", "userId", List.of("guildId", "admin"), List.of(guildId, 0));
		if (objs.isEmpty()) {
			return Collections.emptyList();
		}
		return objs.stream().map(obj -> String.valueOf(obj)).collect(Collectors.toList());
	}

	public List<String> accessAdminGet(String guildId) {
		List<Object> objs = select("modAccess", "userId", List.of("guildId", "admin"), List.of(guildId, 1));
		if (objs.isEmpty()) {
			return Collections.emptyList();
		}
		return objs.stream().map(obj -> String.valueOf(obj)).collect(Collectors.toList());
	}

	public String hasAccess(String guildId, String userId) {
		List<Object> objs = select("modAccess", "admin", List.of("guildId", "userId"), List.of(guildId, userId));
		if (objs.isEmpty() || objs.get(0) == null) {
			return null;
		}
		if (objs.get(0).equals(1)) {
			return "admin";
		}
		return "mod";
	}

	// Ban table

	// add new ban
	public void banAdd(String userId, String userName, String modId, String modName, String guildId, String reason, Timestamp timeStart, Duration duration, boolean synced) {
		insert("ban", List.of("userId", "userNickname", "modId", "modNickname", "guildId", "reason", "timeStart", "duration", "synced"),
			List.of(userId, userName, modId, modName, guildId, reason, timeStart.toString(), duration.toString(), (synced ? 1 : 0)));
	}

	// update ban reason
	public void banUpdateReason(String banId, String reason) {
		update("ban", "reason", reason, "banId", banId);
	}

	// update ban duration
	public void banUpdateDuration(String banId, Integer duration) {
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
	

	// INSERT sql
	private void insert(String table, String insertKey, Object insertValueObj) {
		insert(table, List.of(insertKey), List.of(insertValueObj));
	}
	
	private void insert(final String table, final List<String> insertKeys, final List<Object> insertValuesObj) {
		List<String> insertValues = new ArrayList<String>(insertValuesObj.size());
		for (Object obj : insertValuesObj) {
			insertValues.add(quote(obj));
		}

		String sql = "INSERT INTO "+table+" ("+String.join(", ", insertKeys)+") VALUES ("+String.join(", ", insertValues)+")";
		try (Connection conn = connect();
		PreparedStatement st = conn.prepareStatement(sql)) {
			st.executeUpdate();
		} catch (SQLException ex) {
			logger.warn("DB: Error at INSERT\nrequest: {}", sql, ex);
		}
	}

	// SELECT sql
	private List<Object> select(String table, String selectKey, String condKey, Object condValueObj) {
		return select(table, selectKey, List.of(condKey), List.of(condValueObj));
	}

	private List<Object> select(final String table, final String selectKey, final List<String> condKeys, final List<Object> condValuesObj) {
		List<String> condValues = new ArrayList<String>(condValuesObj.size());
		for (Object obj : condValuesObj) {
			condValues.add(quote(obj));
		}

		String sql = "SELECT * FROM "+table+" WHERE ";
		for (int i = 0; i < condKeys.size(); i++) {
			if (i > 0) {
				sql += " AND ";
			}
			sql += condKeys.get(i)+"="+condValues.get(i);
		}

		List<Object> results = new ArrayList<Object>();
		try (Connection conn = connect();
		PreparedStatement st = conn.prepareStatement(sql)) {
			ResultSet rs = st.executeQuery();
			while (rs.next()) {
				results.add(rs.getObject(selectKey));
			}
		} catch (SQLException ex) {
			logger.warn("DB: Error at SELECT\nrequest: {}", sql, ex);
		}
		return results;
	}

	private List<Map<String, Object>> select(String table, List<String> selectKeys, String condKey, Object condValueObj) {
		return select(table, selectKeys, List.of(condKey), List.of(condValueObj));
	}

	private List<Map<String, Object>> select(final String table, final List<String> selectKeys, final List<String> condKeys, final List<Object> condValuesObj) {
		List<String> condValues = new ArrayList<String>(condValuesObj.size());
		for (Object obj : condValuesObj) {
			condValues.add(quote(obj));
		}

		String sql = "SELECT * FROM "+table+" WHERE ";
		for (int i = 0; i < condKeys.size(); i++) {
			if (i > 0) {
				sql += " AND ";
			}
			sql += condKeys.get(i)+"="+condValues.get(i);
		}

		List<Map<String, Object>> results = new ArrayList<Map<String, Object>>();

		try (Connection conn = connect();
		PreparedStatement st = conn.prepareStatement(sql)) {
			ResultSet rs = st.executeQuery();
			List<String> keys = new ArrayList<>();
			
			if (selectKeys.size() == 0) {
				for (int i = 1; i<=rs.getMetaData().getColumnCount(); i++) {
					keys.add(rs.getMetaData().getColumnName(i));
				}
			} else {
				keys = selectKeys;
			}

			Map<String, Object> banDataTemp = keys.stream().collect(HashMap::new, (m,v)->m.put(v, null), HashMap::putAll);
			while (rs.next()) {
				Map<String, Object> banData = banDataTemp;
				for (String key : keys) {
					banData.put(key, rs.getObject(key));
				}
				results.add(banData);
			}
		} catch (SQLException ex) {
			logger.warn("DB: Error at SELECT\nrequest: {}", sql, ex);
		}
		return results;
	}

	// UPDATE sql
	private void update(String table, String updateKey, Object updateValueObj, String condKey, Object condValueObj) {
		update(table, List.of(updateKey), List.of(updateValueObj), List.of(condKey), List.of(condValueObj));
	}

	private void update(String table, String updateKey, Object updateValueObj, List<String> condKeys, List<Object> condValuesObj) {
		update(table, List.of(updateKey), List.of(updateValueObj), condKeys, condValuesObj);
	}

	private void update(String table, List<String> updateKeys, List<Object> updateValuesObj, String condKey, Object condValueObj) {
		update(table, updateKeys, updateValuesObj, List.of(condKey), List.of(condValueObj));
	}

	private void update(final String table, final List<String> updateKeys, final List<Object> updateValuesObj, final List<String> condKeys, final List<Object> condValuesObj) {
		List<String> updateValues = new ArrayList<String>(updateValuesObj.size());
		for (Object obj : updateValuesObj) {
			updateValues.add(quote(obj));
		}
		List<String> condValues = new ArrayList<String>(condValuesObj.size());
		for (Object obj : condValuesObj) {
			condValues.add(quote(obj));
		}

		String sql = "UPDATE "+table+" SET ";
		for (int i = 0; i<updateKeys.size(); i++) {
			if (i > 0) {
				sql += ", ";
			}
			sql += updateKeys.get(i)+"="+updateValues.get(i);
		}
		sql += " WHERE ";
		for (int i = 0; i<condKeys.size(); i++) {
			if (i > 0) {
				sql += " AND ";
			}
			sql += condKeys.get(i)+"="+condValues.get(i);
		}

		try (Connection conn = connect();
		PreparedStatement st = conn.prepareStatement(sql)) {
			st.executeUpdate();
		} catch (SQLException ex) {
			logger.warn("DB: Error at UPDATE\nrequest: {}", sql, ex);
		}
	}

	// DELETE sql
	private void delete(String table, String condKey, Object condValueObj) {
		delete(table, List.of(condKey), List.of(condValueObj));
	}

	private void delete(final String table, final List<String> condKeys, final List<Object> condValuesObj) {
		List<String> condValues = new ArrayList<String>(condValuesObj.size());
		for (Object obj : condValuesObj) {
			condValues.add(quote(obj));
		}

		String sql = "DELETE FROM "+table+" WHERE ";
		for (int i = 0; i<condKeys.size(); i++) {
			if (i > 0) {
				sql += " AND ";
			}
			sql += condKeys.get(i)+"="+condValues.get(i);
		}

		try (Connection conn = connect();
		PreparedStatement st = conn.prepareStatement(sql)) {
			st.executeUpdate();
		} catch (SQLException ex) {
			logger.warn("DB: Error at DELETE\nrequest: {}", sql, ex);
		}
	}

	private String quote(Object value) {
		// Convert to string and replace '(single quote) with ''(2 single quotes) for sql
		return "'" + String.valueOf(value).replaceAll("'", "''") + "'"; // smt's -> 'smt''s'
	}
}
