package bot;

import java.util.Collections;
import java.util.List;
import java.util.Random;

import javax.security.auth.login.LoginException;

import com.jagrosh.jdautilities.command.CommandClient;
import com.jagrosh.jdautilities.command.CommandClientBuilder;
import com.jagrosh.jdautilities.commons.waiter.EventWaiter;

import org.slf4j.LoggerFactory;

import bot.utils.CheckUtil;
import bot.utils.DBUtil;
import bot.utils.file.FileManager;
import bot.utils.file.lang.LangUtil;
import bot.utils.message.*;
import bot.commands.*;
import bot.commands.guild.*;
import bot.commands.owner.*;
import bot.commands.voice.*;
import bot.commands.webhook.WebhookCmd;
import bot.constants.*;
import bot.listeners.GuildListener;
import bot.listeners.VoiceListener;
import ch.qos.logback.classic.Logger;
import net.dv8tion.jda.annotations.ForRemoval;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.JDABuilder;
import net.dv8tion.jda.api.OnlineStatus;
import net.dv8tion.jda.api.entities.Activity;
import net.dv8tion.jda.api.exceptions.ErrorResponseException;
import net.dv8tion.jda.api.requests.GatewayIntent;
import net.dv8tion.jda.api.utils.ChunkingFilter;
import net.dv8tion.jda.api.utils.MemberCachePolicy;
import net.dv8tion.jda.api.utils.cache.CacheFlag;

public class App {
	
	private final Logger logger = (Logger) LoggerFactory.getLogger(App.class);

	private static App instance;

	public final String version = (App.class.getPackage().getImplementationVersion() == null) ? "DEVELOPMENT" : App.class.getPackage().getImplementationVersion();

	public final JDA jda;
	public final EventWaiter waiter;

	private final FileManager fileManager = new FileManager();

	private final Random random = new Random();

	private final GuildListener guildListener;
	private final VoiceListener voiceListener;
	
	private DBUtil dbUtil;
	private MessageUtil messageUtil;
	private EmbedUtil embedUtil;
	private LangUtil langUtil;
	private CheckUtil checkUtil;

	public String defaultLanguage;

	public App() {

		JDA setJda = null;

		fileManager.addFile("config", Constants.SEPAR + "config.json", Constants.DATA_PATH + Constants.SEPAR + "config.json")
			.addFile("database", Constants.SEPAR + "server.db", Constants.DATA_PATH + Constants.SEPAR + "server.db")
			.addLang("en")
			.addLang("ru-RU");

		defaultLanguage = "en";
		
		// Define for default
		waiter 			= new EventWaiter();
		guildListener 	= new GuildListener(this);
		voiceListener	= new VoiceListener(this);

		dbUtil		= new DBUtil(getFileManager().getFiles().get("database"));
		messageUtil = new MessageUtil(this);
		embedUtil 	= new EmbedUtil(this);
		langUtil 	= new LangUtil(this);
		checkUtil	= new CheckUtil(this);

		// Define a command client
		CommandClient commandClient = new CommandClientBuilder()
			.setOwnerId(fileManager.getString("config", "owner-id"))
			.setServerInvite(Links.DISCORD)
			.setEmojis(Constants.SUCCESS, Constants.WARNING, Constants.ERROR)
			.useHelpBuilder(false)
			.setStatus(OnlineStatus.ONLINE)
			.setActivity(Activity.watching("/help"))
			.addSlashCommands(
				// voice
				new SetNameCmd(this),
				new SetLimitCmd(this),
				new ClaimCmd(this),
				new NameCmd(this),
				new LimitCmd(this),
				new PermitCmd(this),
				new RejectCmd(this),
				new LockCmd(this),
				new UnlockCmd(this),
				// guild
				new LanguageCmd(this),
				new SetupCmd(this),
				// owner
				new ShutdownCmd(this),
				new EvalCmd(this),
				// webhook
				new WebhookCmd(this),
				// other
				new PingCmd(this),
				new AboutCmd(this),
				new HelpCmd(this),
				new StatusCmd(this)
			)
			.build();

		// Build
		MemberCachePolicy policy = MemberCachePolicy.VOICE		// check if in voice
			.or(MemberCachePolicy.OWNER);						// check for owner

		Integer retries = 4; // how many times will it try to build
		Integer cooldown = 8; // in seconds; cooldown amount, will doubles after each retry
		while (true) {
			try {
				setJda = JDABuilder.createLight(fileManager.getString("config", "bot-token"))
					.setEnabledIntents(
						GatewayIntent.GUILD_MEMBERS,				// required for updating member profiles and ChunkingFilter
						GatewayIntent.GUILD_VOICE_STATES			// required for CF VOICE_STATE and policy VOICE
					)
					.setMemberCachePolicy(policy)
					.setChunkingFilter(ChunkingFilter.ALL)		// chunk all guilds
					.enableCache(
						CacheFlag.VOICE_STATE,			// required for policy VOICE
						CacheFlag.MEMBER_OVERRIDES,		// channel permission overrides
						CacheFlag.ROLE_TAGS				// role search
					) 
					.setAutoReconnect(true)
					.addEventListeners(commandClient, waiter, guildListener, voiceListener)
					.build();
				break;
			} catch (LoginException ex) {
				logger.error("Build failed", ex);
				System.exit(0);
			} catch (ErrorResponseException ex) { // Tries to reconnect to discord x times with some delay, else exits
				if (retries > 0) {
					retries--;
					logger.info("Retrying connecting in "+cooldown+" seconds..."+retries+" more attempts");
					try {
						Thread.sleep(cooldown*1000);
					} catch (InterruptedException e) {
						logger.error("Thread sleep interupted", e);
					}
					cooldown*=2;
				} else {
					logger.error("No network connection or couldn't connect to DNS", ex);
					System.exit(0);
				}
			}
		}
		

		this.jda = setJda;
	}

	public Logger getLogger() {
		return logger;
	}

	public FileManager getFileManager() {
		return fileManager;
	}

	public Random getRandom() {
		return random;
	}

	public DBUtil getDBUtil() {
		return dbUtil;
	}

	public MessageUtil getMessageUtil() {
		return messageUtil;
	}

	public EmbedUtil getEmbedUtil() {
		return embedUtil;
	}

	public CheckUtil getCheckUtil() {
		return checkUtil;
	}

	public String getLanguage(String id) {
		String res = dbUtil.guildGetLanguage(id);
		return (res == null ? "en" : res);
	}

	@ForRemoval
	public String getPrefix(String id) {
		return "/"; // default prefix
	}

	public void setLanguage(String id, String value) {
		dbUtil.guildSetLanguage(id, value);
	}

	public String getMsg(String id, String path, String user, String target) {
		target = target == null ? "null" : target;
		
		return getMsg(id, path, user, Collections.singletonList(target));
	}

	public String getMsg(String id, String path, String user, List<String> targets) {
		String targetReplacement = targets.isEmpty() ? "null" : getMessageUtil().getFormattedMembers(id, targets.toArray(new String[0]));

		return getMsg(id, path, user)
			.replace("{target}", targetReplacement)
			.replace("{targets}", targetReplacement);
	}

	public String getMsg(String id, String path, String user) {
		return getMsg(id, path, user, true);
	}

	public String getMsg(String id, String path, String user, boolean format) {
		if (format)
			user = getMessageUtil().getFormattedMembers(id, user);

		return getMsg(id, path).replace("{user}", user);
	}

	public String getMsg(String path) {
		return getMsg("0", path);
	}

	public String getMsg(String id, String path) {
		return setPlaceholders(langUtil.getString(getLanguage(id), path))
			.replace("{prefix}", getPrefix(id));
	}

	private String setPlaceholders(String msg) {
		return Emotes.getWithEmotes(msg)
			.replace("{name}", "Voice of the Lord")
			.replace("{guild_invite}", Links.DISCORD)
			.replace("{owner_id}", fileManager.getString("config", "owner-id"))
			.replace("{developer_name}", Constants.DEVELOPER_NAME)
			.replace("{developer_id}", Constants.DEVELOPER_ID)
			.replace("{bot_invite}", fileManager.getString("config", "bot-invite"))
			.replace("{bot_version}", version);
	}


	public static void main(String[] args) {
		instance = new App();
		instance.logger.info("Success start");
		instance.jda.getPresence().setPresence(OnlineStatus.ONLINE, Activity.watching("/help"));
	}
}
