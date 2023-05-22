package votl.utils.database;

import java.io.File;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;

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
		
		verifyRequest = new VerifyRequestManager(this);
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

	protected Connection connectMySql() {
		Connection conn = null;
		try {
			conn = DriverManager.getConnection(urlMySql, username, pass);
		} catch (SQLException ex) {
			logger.error("DB MySQL: Connection error to database", ex);
			return null;
		}
		return conn;
	}

}
