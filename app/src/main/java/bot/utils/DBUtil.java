package bot.utils;

import java.io.File;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Arrays;

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
	public void guildAdd(String guildID) {
		insert("guild", "guildID", guildID);
		insert("guildVoice", "guildID", guildID);
	}

	public void guildRemove(String guildID) {
		delete("guild", "guildID", guildID);
		delete("guildVoice", "guildID", guildID);
	}

	public boolean isGuild(String guildID) {
		if (select("guild", "guildID", "guildID", guildID) != null) {
			return true;
		}
		return false;
	}

	// Guild
	public void guildSetLanguage(String guildID, String language) {
		update("guild", "language", language, "guildID", guildID);
	}

	public void guildSetPrefix(String guildID, String prefix) {
		update("guild", "prefix", prefix, "guildID", guildID);
	}

	public String guildGetLanguage(String guildID) {
		Object obj = select("guild", "language", "guildID", guildID);
		if (obj == null) {
			return null;
		}
		return String.valueOf(obj);
	}

	public String guildGetPrefix(String guildID) {
		Object obj = select("guild", "prefix", "guildID", guildID);
		if (obj == null) {
			return null;
		}
		return String.valueOf(obj);
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
		Object obj = select("guildVoice", "categoryID", "guildID", guildID);
		if (obj == null) {
			return null;
		}
		return String.valueOf(obj);
	}

	public String guildVoiceGetChannel(String guildID) {
		Object obj = select("guildVoice", "channelID", "guildID", guildID);
		if (obj == null) {
			return null;
		}
		return String.valueOf(obj);
	}

	public String guildVoiceGetName(String guildID) {
		Object obj = select("guildVoice", "channelName", "guildID", guildID);
		if (obj == null) {
			return null;
		}
		return String.valueOf(obj);
	}

	public Integer guildVoiceGetLimit(String guildID) {
		Object obj = select("guildVoice", "channelLimit", "guildID", guildID);
		if (obj == null) {
			return null;
		}
		return Integer.parseInt(String.valueOf(obj));
	}

	// User Settings
	public void userAdd(String userID) {
		insert("userSettings", "userID", userID);
	}

	public void userRemove(String userID) {
		delete("userSettings", "userID", userID);
	}

	public void userSetName(String userID, String channelName) {
		update("userSettings", "channelName", channelName, "userID", userID);
	}

	public void userSetLimit(String userID, Integer channelLimit) {
		update("userSettings", "channelLimit", channelLimit, "userID", userID);
	}

	public String userGetName(String userID) {
		Object obj = select("userSettings", "channelName", "userID", userID);
		if (obj == null) {
			return null;
		}
		return String.valueOf(obj);
	}

	public Integer userGetLimit(String userID) {
		Object obj = select("userSettings", "channelLimit", "userID", userID);
		if (obj == null) {
			return null;
		}
		return Integer.parseInt(String.valueOf(obj));
	}

	// Voice Channel
	public void channelAdd(String userID, String channelID) {
		insert("voiceChannel", new String[]{"userID", "channelID"}, new Object[]{userID, channelID});
	}

	public void channelRemove(String channelID) {
		delete("voiceChannel", "channelID", channelID);
	}

	public boolean isVoiceChannelUser(String userID) {
		if (select("voiceChannel", "userID", "userID", userID) != null) {
			return true;
		}
		return false;
	}

	public boolean isVoiceChannel(String channelID) {
		if (select("voiceChannel", "channelID", "channelID", channelID) != null) {
			return true;
		}
		return false;
	}

	public void channelSetUser(String userID, String channelID) {
		update("voiceChannel", "userID", userID, "channelID", channelID);
	}

	public String channelGetChannel(String userID) {
		Object obj = select("voiceChannel", "channelID", "userID", userID);
		if (obj == null) {
			return null;
		}
		return String.valueOf(obj);
	}

	public String channelGetUser(String channelID) {
		Object obj = select("voiceChannel", "userID", "channelID", channelID);
		if (obj == null) {
			return null;
		}
		return String.valueOf(obj);
	}


	// INSERT sql
	private void insert(String table, String insertKey, Object insertValueObj) {
		insert(table, new String[]{insertKey}, new Object[]{insertValueObj});
	}
	
	private void insert(String table, String[] insertKeys, Object[] insertValuesObj) {
		String[] insertValues = Arrays.stream(insertValuesObj).toArray(String[]::new);
		for (int i = 0; i<insertValues.length; i++) {
			insertValues[i] = "'"+insertValues[i]+"'";
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
	private Object select(String table, String selectKey, String condKey, Object condValueObj) {
		String condValue = String.valueOf(condValueObj);
		condValue = "'"+condValue+"'";

		String sql = "SELECT * FROM "+table+" WHERE "+condKey+"="+condValue;
		Object result = null;
		try (Connection conn = connect();
		PreparedStatement st = conn.prepareStatement(sql)) {
			ResultSet rs = st.executeQuery();
			while (rs.next()) {
				result = rs.getObject(selectKey);
			}
		} catch (SQLException ex) {
			logger.warn("DB: Error at SELECT\nrequest: {}", sql, ex);
		}
		return result;
	}

	// UPDATE sql
	private void update(String table, String updateKey, Object updateValueObj, String condKey, Object condValueObj) {
		update(table, new String[]{updateKey}, new Object[]{updateValueObj}, condKey, condValueObj);
	}

	private void update(String table, String[] updateKeys, Object[] updateValuesObj, String condKey, Object condValueObj) {
		String[] updateValues = Arrays.stream(updateValuesObj).toArray(String[]::new);
		for (int i = 0; i<updateValues.length; i++) {
			updateValues[i] = "'"+updateValues[i]+"'";
		}
		String condValue = String.valueOf(condValueObj);
		condValue = "'"+condValue+"'"; 

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
		String condValue = String.valueOf(condValueObj);
		condValue = "'"+condValue+"'"; 

		String sql = "DELETE FROM "+table+" WHERE "+condKey+"="+condValue;
		try (Connection conn = connect();
		PreparedStatement st = conn.prepareStatement(sql)) {
			st.executeUpdate();
		} catch (SQLException ex) {
			logger.warn("DB: Error at UPDATE\nrequest: {}", sql, ex);
		}
	}
}
