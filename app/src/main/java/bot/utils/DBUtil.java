package bot.utils;

import java.io.File;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

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
			update("guildVoice", new String[]{"categoryId", "channelId"}, new String[]{categoryId, channelId}, "guildId", guildId);
		} else {
			insert("guildVoice", new String[]{"guildId", "categoryId", "channelId"}, new String[]{guildId, categoryId, channelId});
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
		insert("voiceChannel", new String[]{"userId", "channelId"}, new Object[]{userId, channelId});
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
		insert("webhook", new String[]{"webhookId", "guildId", "token"}, new Object[]{webhookId, guildId, token});
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
	public void moduleAdd(String guildId, String module) {
		insert("moduleOff", new String[]{"guildId", "module"}, new Object[]{guildId, module});
	}

	public void moduleRemove(String guildId, String module) {
		delete("moduleOff", new String[]{"guildId", "module"}, new Object[]{guildId, module});
	}

	public List<String> modulesGet(String guildId) {
		List<Object> objs = select("moduleOff", "module", "guildId", guildId);
		if (objs.isEmpty()) {
			return Collections.emptyList();
		}
		return objs.stream().map(obj -> String.valueOf(obj)).collect(Collectors.toList());
	}

	public boolean moduleDisabled(String guildId, String module) {
		if (select("moduleOff", "guildId", new String[]{"guildId", "module"}, new Object[]{guildId, module}).isEmpty()) {
			return false;
		}
		return true;
	}

	// Mod/Admin Access
	public void accessAdd(String guildId, String userId, boolean admin) {
		insert("modAccess", new String[]{"guildId", "userId", "admin"}, new Object[]{guildId, userId, (admin ? 1 : 0)});
	}

	public void accessRemove(String guildId, String userId) {
		delete("modAccess", new String[]{"guildId", "userId"}, new Object[]{guildId, userId});
	}

	public void accessChange(String guildId, String userId, boolean admin) {
		update("modAccess", "admin", (admin ? 1 : 0), new String[]{"guildId", "userId"}, new Object[]{guildId, userId});
	}

	public List<String> accessAllGet(String guildId) {
		List<Object> objs = select("modAccess", "userId", "guildId", guildId);
		if (objs.isEmpty()) {
			return Collections.emptyList();
		}
		return objs.stream().map(obj -> String.valueOf(obj)).collect(Collectors.toList());
	}

	public List<String> accessModGet(String guildId) {
		List<Object> objs = select("modAccess", "userId", new String[]{"guildId", "admin"}, new Object[]{guildId, 0});
		if (objs.isEmpty()) {
			return Collections.emptyList();
		}
		return objs.stream().map(obj -> String.valueOf(obj)).collect(Collectors.toList());
	}

	public List<String> accessAdminGet(String guildId) {
		List<Object> objs = select("modAccess", "userId", new String[]{"guildId", "admin"}, new Object[]{guildId, 1});
		if (objs.isEmpty()) {
			return Collections.emptyList();
		}
		return objs.stream().map(obj -> String.valueOf(obj)).collect(Collectors.toList());
	}

	public String hasAccess(String guildId, String userId) {
		List<Object> objs = select("modAccess", "admin", new String[]{"guildId", "userId"}, new Object[]{guildId, userId});
		if (objs.isEmpty() || objs.get(0) == null) {
			return null;
		}
		if (objs.get(0).equals(1)) {
			return "admin";
		}
		return "mod";
	}
	

	// INSERT sql
	private void insert(String table, String insertKey, Object insertValueObj) {
		insert(table, new String[]{insertKey}, new Object[]{insertValueObj});
	}
	
	private void insert(String table, String[] insertKeys, Object[] insertValuesObj) {
		String[] insertValues = new String[insertValuesObj.length];
		for (int i = 0; i<insertValuesObj.length; i++) {
			insertValues[i] = quote(insertValuesObj[i]);
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
		return select(table, selectKey, new String[]{condKey}, new Object[]{condValueObj});
	}

	private List<Object> select(String table, String selectKey, String[] condKeys, Object[] condValuesObj) {
		String[] condValues = new String[condValuesObj.length];
		for (int i = 0; i<condValuesObj.length; i++) {
			condValues[i] = quote(condValuesObj[i]);
		}

		String sql = "SELECT * FROM "+table+" WHERE ";
		for (int i = 0; i<condKeys.length; i++) {
			if (i > 0) {
				sql += " AND ";
			}
			sql += condKeys[i]+"="+condValues[i];
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

	// UPDATE sql
	private void update(String table, String updateKey, Object updateValueObj, String condKey, Object condValueObj) {
		update(table, new String[]{updateKey}, new Object[]{updateValueObj}, new String[]{condKey}, new Object[]{condValueObj});
	}

	private void update(String table, String updateKey, Object updateValueObj, String[] condKeys, Object[] condValuesObj) {
		update(table, new String[]{updateKey}, new Object[]{updateValueObj}, condKeys, condValuesObj);
	}

	private void update(String table, String[] updateKeys, Object[] updateValuesObj, String condKey, Object condValueObj) {
		update(table, updateKeys, updateValuesObj, new String[]{condKey}, new Object[]{condValueObj});
	}

	private void update(String table, String[] updateKeys, Object[] updateValuesObj, String[] condKeys, Object[] condValuesObj) {
		String[] updateValues = new String[updateValuesObj.length];
		for (int i = 0; i<updateValuesObj.length; i++) {
			updateValues[i] = quote(updateValuesObj[i]);
		}
		String[] condValues = new String[condValuesObj.length];
		for (int i = 0; i<condValuesObj.length; i++) {
			condValues[i] = quote(condValuesObj[i]);
		}

		String sql = "UPDATE "+table+" SET ";
		for (int i = 0; i<updateKeys.length; i++) {
			if (i > 0) {
				sql += ", ";
			}
			sql += updateKeys[i]+"="+updateValues[i];
		}
		sql += " WHERE ";
		for (int i = 0; i<condKeys.length; i++) {
			if (i > 0) {
				sql += " AND ";
			}
			sql += condKeys[i]+"="+condValues[i];
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
		delete(table, new String[]{condKey}, new Object[]{condValueObj});
	}

	private void delete(String table, String[] condKeys, Object[] condValuesObj) {
		String[] condValues = new String[condValuesObj.length];
		for (int i = 0; i<condValuesObj.length; i++) {
			condValues[i] = quote(condValuesObj[i]);
		}

		String sql = "DELETE FROM "+table+" WHERE ";
		for (int i = 0; i<condKeys.length; i++) {
			if (i > 0) {
				sql += " AND ";
			}
			sql += condKeys[i]+"="+condValues[i];
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
