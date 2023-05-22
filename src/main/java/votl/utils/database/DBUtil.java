package votl.utils.database;

import java.io.File;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.List;

import votl.utils.database.managers.AccessManager;
import votl.utils.database.managers.BanManager;
import votl.utils.database.managers.GroupManager;
import votl.utils.database.managers.GuildSettingsManager;
import votl.utils.database.managers.GuildVoiceManager;
import votl.utils.database.managers.ModuleManager;
import votl.utils.database.managers.UserSettingsManager;
import votl.utils.database.managers.VerifyManager;
import votl.utils.database.managers.VoiceChannelManager;
import votl.utils.database.managers.WebhookManager;

import org.slf4j.LoggerFactory;

import ch.qos.logback.classic.Logger;

public class DBUtil {

	public final GuildSettingsManager guild;
	public final GuildVoiceManager guildVoice;
	public final UserSettingsManager user;
	public final VoiceChannelManager voice;
	public final WebhookManager webhook;
	public final ModuleManager module;
	public final AccessManager access;
	public final BanManager ban;
	public final GroupManager group;
	public final VerifyManager verify;

	protected final Logger logger = (Logger) LoggerFactory.getLogger(DBUtil.class);

	private String urlSQLite;

	public DBUtil(File location) {
		this.urlSQLite = "jdbc:sqlite:" + location;
		
		guild = new GuildSettingsManager(this);
		guildVoice = new GuildVoiceManager(this);
		user = new UserSettingsManager(this);
		voice = new VoiceChannelManager(this);
		webhook = new WebhookManager(this);
		module = new ModuleManager(this);
		access = new AccessManager(this);
		ban = new BanManager(this);
		group = new GroupManager(this);
		verify = new VerifyManager(this);

		updateDB();
	}

	protected Connection connectSQLite() {
		Connection conn = null;
		try {
			conn = DriverManager.getConnection(urlSQLite);
		} catch (SQLException ex) {
			logger.error("DB SQLite: Connection error to database", ex);
			return null;
		}
		return conn;
	}

	// 0 - no version or error
	// 1> - compare active db version with resources
	// if version lower -> apply instruction for creating new tables, adding/removing collumns
	// in the end set active db version to resources
	public Integer getActiveDBVersion() {
		Integer version = 0;
		try (Connection conn = DriverManager.getConnection(urlSQLite);
		PreparedStatement st = conn.prepareStatement("PRAGMA user_version")) {
			version = st.executeQuery().getInt(1);
		} catch(SQLException ex) {
			logger.warn("DB SQLite: Failed to get active database version", ex);
		}
		return version;
	}

	public Integer getResourcesDBVersion() {
		Integer version = 0;
		try (Connection conn = DriverManager.getConnection("jdbc:sqlite:" + getClass().getResource("/server.db").toString());
		PreparedStatement st = conn.prepareStatement("PRAGMA user_version")) {
			version = st.executeQuery().getInt(1);
		} catch(SQLException ex) {
			logger.warn("DB SQLite: Failed to get resources database version", ex);
		}
		return version;
	}

	// CREATE TABLE table_name (column1 datatype, column2 datatype);
	// DROP TABLE table_name;
	// ALTER TABLE table_name RENAME TO new_name;
	// ALTER TABLE table_name ADD column_name datatype;
	// ALTER TABLE table_name DROP column_name;
	// ALTER TABLE table_name RENAME old_name to new_name;
	private final List<String> instruct = List.of(
		"" // 1 -> 2
	);

	private void updateDB() {
		// 0 - skip
		Integer newVersion = getResourcesDBVersion();
		if (newVersion == 0) return;
		Integer activeVersion = getActiveDBVersion();
		if (activeVersion == 0) return;

		if (newVersion > activeVersion) {
			while (activeVersion < newVersion) {
				Integer next = activeVersion + 1;
				String sql = instruct.get(next - 2);

				logger.debug(sql);
				try (Connection conn = DriverManager.getConnection(urlSQLite);
				Statement st = conn.createStatement()) {
					st.execute(sql);
				} catch(SQLException ex) {
					logger.warn("DB SQLite: Failed to execute update", ex);
					break;
				}

				activeVersion = next;
			}

			// Update version
			try (Connection conn = DriverManager.getConnection(urlSQLite);
			Statement st = conn.createStatement()) {
				st.execute("PRAGMA user_version = "+newVersion.toString());
				logger.info("DB SQLite: Database version updated to {}", newVersion);
			} catch(SQLException ex) {
				logger.warn("DB SQLite: Failed to set active database version", ex);
			}
		}
	}

}
