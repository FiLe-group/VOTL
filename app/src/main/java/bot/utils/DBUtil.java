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
	public boolean guildAdd(String guildID) {
		boolean added = false;
		if (!isGuild(guildID)) {
			insert("guild", "guildID", guildID);
			added = true;
		}
		if (!isGuildVoice(guildID)) {
			insert("guildVoice", "guildID", guildID);
			added = true;
		}
		return added;
	}

	public void guildRemove(String guildID) {
		delete("guild", "guildID", guildID);
		delete("guildVoice", "guildID", guildID);
	}

	public boolean isGuild(String guildID) {
		if (select("guild", "guildID", "guildID", guildID).isEmpty()) {
			return false;
		}
		return true;
	}

	public boolean isGuildVoice(String guildID) {
		if (select("guildVoice", "guildID", "guildID", guildID).isEmpty()) {
			return false;
		}
		return true;
	}

	// Guild
	public void guildSetLanguage(String guildID, String language) {
		update("guild", "language", language, "guildID", guildID);
	}

	public String guildGetLanguage(String guildID) {
		List<Object> objs = select("guild", "language", "guildID", guildID);
		if (objs.isEmpty() || objs.get(0) == null) {
			return null;
		}
		return String.valueOf(objs.get(0));
	}
	
	// Guild Voice
	public void guildVoiceSetup(String guildID, String categoryID, String channelID) {
		update("guildVoice", new String[]{"categoryID", "channelID"}, new String[]{categoryID, channelID}, "guildID", guildID);
	}

	public void guildVoiceSetName(String guildID, String channelName) {
		update("guildVoice", "channelName", channelName, "guildID", guildID);
	}

	public void guildVoiceSetLimit(String guildID, Integer channelLimit) {
		update("guildVoice", "channelLimit", channelLimit, "guildID", guildID);
	}

	public String guildVoiceGetCategory(String guildID) {
		List<Object> objs = select("guildVoice", "categoryID", "guildID", guildID);
		if (objs.isEmpty() || objs.get(0) == null) {
			return null;
		}
		return String.valueOf(objs.get(0));
	}

	public String guildVoiceGetChannel(String guildID) {
		List<Object> objs = select("guildVoice", "channelID", "guildID", guildID);
		if (objs.isEmpty() || objs.get(0) == null) {
			return null;
		}
		return String.valueOf(objs.get(0));
	}

	public String guildVoiceGetName(String guildID) {
		List<Object> objs = select("guildVoice", "channelName", "guildID", guildID);
		if (objs.isEmpty() || objs.get(0) == null) {
			return null;
		}
		return String.valueOf(objs.get(0));
	}

	public Integer guildVoiceGetLimit(String guildID) {
		List<Object> objs = select("guildVoice", "channelLimit", "guildID", guildID);
		if (objs.isEmpty() || objs.get(0) == null) {
			return null;
		}
		return Integer.parseInt(String.valueOf(objs.get(0)));
	}

	// User Settings
	public void userAdd(String userID) {
		insert("userSettings", "userID", userID);
	}

	public void userRemove(String userID) {
		delete("userSettings", "userID", userID);
	}

	public boolean isUser(String userID) {
		if (select("userSettings", "userID", "userID", userID).isEmpty()) {
			return false;
		}
		return true;
	}

	public void userSetName(String userID, String channelName) {
		update("userSettings", "channelName", channelName, "userID", userID);
	}

	public void userSetLimit(String userID, Integer channelLimit) {
		update("userSettings", "channelLimit", channelLimit, "userID", userID);
	}

	public String userGetName(String userID) {
		List<Object> objs = select("userSettings", "channelName", "userID", userID);
		if (objs.isEmpty() || objs.get(0) == null) {
			return null;
		}
		return String.valueOf(objs.get(0));
	}

	public Integer userGetLimit(String userID) {
		List<Object> objs = select("userSettings", "channelLimit", "userID", userID);
		if (objs.isEmpty() || objs.get(0) == null) {
			return null;
		}
		return Integer.parseInt(String.valueOf(objs.get(0)));
	}

	// Voice Channel
	public void channelAdd(String userID, String channelID) {
		insert("voiceChannel", new String[]{"userID", "channelID"}, new Object[]{userID, channelID});
	}

	public void channelRemove(String channelID) {
		delete("voiceChannel", "channelID", channelID);
	}

	public boolean isVoiceChannel(String userID) {
		if (select("voiceChannel", "userID", "userID", userID).isEmpty()) {
			return false;
		}
		return true;
	}

	public boolean isVoiceChannelExists(String channelID) {
		if (select("voiceChannel", "channelID", "channelID", channelID).isEmpty()) {
			return false;
		}
		return true;
	}

	public void channelSetUser(String userID, String channelID) {
		update("voiceChannel", "userID", userID, "channelID", channelID);
	}

	public String channelGetChannel(String userID) {
		List<Object> objs = select("voiceChannel", "channelID", "userID", userID);
		if (objs.isEmpty() || objs.get(0) == null) {
			return null;
		}
		return String.valueOf(objs.get(0));
	}

	public String channelGetUser(String channelID) {
		List<Object> objs = select("voiceChannel", "userID", "channelID", channelID);
		if (objs.isEmpty() || objs.get(0) == null) {
			return null;
		}
		return String.valueOf(objs.get(0));
	}

	// Webhook List
	public void webhookAdd(String webhookID, String guildID, String token) {
		insert("webhookList", new String[]{"webhookID", "guildID", "token"}, new Object[]{webhookID, guildID, token});
	}

	public void webhookRemove(String webhookID) {
		delete("webhookList", "webhookID", webhookID);
	}

	public boolean webhookExists(String webhookID) {
		if (select("webhookList", "webhookID", "webhookID", webhookID).isEmpty()) {
			return false;
		}
		return true;
	}

	public String webhookGetToken(String webhookID) {
		List<Object> objs = select("webhookList", "token", "webhookID", webhookID);
		if (objs.isEmpty() || objs.get(0) == null) {
			return null;
		}
		return String.valueOf(objs.get(0));
	}

	public List<String> webhookGetIDs(String guildID) {
		List<Object> objs = select("webhookList", "webhookID", "guildID", guildID);
		if (objs.isEmpty()) {
			return Collections.emptyList();
		}
		return objs.stream().map(obj -> String.valueOf(obj)).collect(Collectors.toList());
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
		String condValue = quote(condValueObj);

		String sql = "SELECT * FROM "+table+" WHERE "+condKey+"="+condValue;
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
		update(table, new String[]{updateKey}, new Object[]{updateValueObj}, condKey, condValueObj);
	}

	private void update(String table, String[] updateKeys, Object[] updateValuesObj, String condKey, Object condValueObj) {
		String[] updateValues = new String[updateValuesObj.length];
		for (int i = 0; i<updateValuesObj.length; i++) {
			updateValues[i] = quote(updateValuesObj[i]);
		}
		String condValue = quote(condValueObj);

		String sql = "UPDATE "+table+" SET ";
		for (int i = 0; i<updateKeys.length; i++) {
			if (i > 0) {
				sql += ", ";
			}
			sql += updateKeys[i]+"="+updateValues[i];
		}
		sql += " WHERE "+condKey+"="+condValue;
		try (Connection conn = connect();
		PreparedStatement st = conn.prepareStatement(sql)) {
			st.executeUpdate();
		} catch (SQLException ex) {
			logger.warn("DB: Error at UPDATE\nrequest: {}", sql, ex);
		}
	}

	// DELETE sql
	private void delete(String table, String condKey, Object condValueObj) {
		String condValue = quote(condValueObj); 

		String sql = "DELETE FROM "+table+" WHERE "+condKey+"="+condValue;
		try (Connection conn = connect();
		PreparedStatement st = conn.prepareStatement(sql)) {
			st.executeUpdate();
		} catch (SQLException ex) {
			logger.warn("DB: Error at UPDATE\nrequest: {}", sql, ex);
		}
	}

	private String quote(Object value) {
		// Convert to string and replace '(single quote) with ''(2 single quotes) for sql
		return "'" + String.valueOf(value).replaceAll("'", "''") + "'"; // smt's -> 'smt''s'
	}
}
