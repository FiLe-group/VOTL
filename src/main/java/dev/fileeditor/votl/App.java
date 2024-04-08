package dev.fileeditor.votl;

import java.util.Objects;
import java.util.Optional;
import java.util.Random;
import java.util.Set;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.JDABuilder;
import net.dv8tion.jda.api.OnlineStatus;
import net.dv8tion.jda.api.entities.Activity;
import net.dv8tion.jda.api.exceptions.ErrorResponseException;
import net.dv8tion.jda.api.exceptions.InvalidTokenException;
import net.dv8tion.jda.api.interactions.DiscordLocale;
import net.dv8tion.jda.api.requests.GatewayIntent;
import net.dv8tion.jda.api.utils.ChunkingFilter;
import net.dv8tion.jda.api.utils.MemberCachePolicy;
import net.dv8tion.jda.api.utils.cache.CacheFlag;

import org.slf4j.LoggerFactory;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.LoggerContext;
import ch.qos.logback.classic.encoder.PatternLayoutEncoder;

import dev.fileeditor.votl.base.command.CommandClient;
import dev.fileeditor.votl.base.command.CommandClientBuilder;
import dev.fileeditor.votl.base.waiter.EventWaiter;
import dev.fileeditor.votl.commands.guild.*;
import dev.fileeditor.votl.commands.moderation.*;
import dev.fileeditor.votl.commands.other.*;
import dev.fileeditor.votl.commands.owner.*;
import dev.fileeditor.votl.commands.verification.*;
import dev.fileeditor.votl.commands.voice.*;
import dev.fileeditor.votl.commands.webhook.WebhookCmd;
import dev.fileeditor.votl.listeners.*;
import dev.fileeditor.votl.objects.constants.Constants;
import dev.fileeditor.votl.objects.constants.Links;
import dev.fileeditor.votl.services.CountingThreadFactory;
import dev.fileeditor.votl.services.ExpiryCheck;
import dev.fileeditor.votl.utils.*;
import dev.fileeditor.votl.utils.database.DBUtil;
import dev.fileeditor.votl.utils.file.FileManager;
import dev.fileeditor.votl.utils.file.lang.LangUtil;
import dev.fileeditor.votl.utils.file.lang.LocaleUtil;
import dev.fileeditor.votl.utils.logs.LogUtil;
import dev.fileeditor.votl.utils.message.*;

public class App {
	
	private final Logger logger = (Logger) LoggerFactory.getLogger(App.class);

	private static App instance;

	public final String version = Optional.ofNullable(App.class.getPackage().getImplementationVersion()).map(v -> "v"+v).orElse("DEVELOPMENT");

	public final JDA JDA;
	public final EventWaiter WAITER;
	private final CommandClient commandClient;

	private final FileManager fileManager = new FileManager();

	private final GuildListener guildListener;
	private final VoiceListener voiceListener;
	private final AutoCompleteListener acListener;
	private final ButtonListener buttonListener;

	private final LogListener logListener;
	
	private final ScheduledExecutorService scheduledExecutor;
	private final ExpiryCheck expiryCheck;
	
	private final DBUtil dbUtil;
	private final MessageUtil messageUtil;
	private final EmbedUtil embedUtil;
	private final CheckUtil checkUtil;
	private final LocaleUtil localeUtil;
	private final LogUtil logUtil;

	public App() {
		try {
			fileManager.addFile("config", "/config.json", Constants.DATA_PATH + "config.json")
				.addFile("database", "/server.db", Constants.DATA_PATH + "server.db")
				.addLang("en-GB")
				.addLang("ru");
		} catch (Exception ex) {
			logger.error("Error while interacting with File Manager", ex);
			System.exit(0);
		}
		
		// Define for default
		dbUtil		= new DBUtil(getFileManager());
		localeUtil	= new LocaleUtil(this, "en-GB", DiscordLocale.ENGLISH_UK);
		messageUtil	= new MessageUtil(localeUtil);
		embedUtil	= new EmbedUtil(localeUtil);
		checkUtil	= new CheckUtil(this);
		logUtil		= new LogUtil(this);

		WAITER			= new EventWaiter();
		guildListener	= new GuildListener(this);
		voiceListener	= new VoiceListener(this);
		logListener		= new LogListener(this);
		buttonListener	= new ButtonListener(this);

		scheduledExecutor	= new ScheduledThreadPoolExecutor(2, new CountingThreadFactory("VOTL", "Scheduler", false));
		expiryCheck			= new ExpiryCheck(this);

		scheduledExecutor.scheduleWithFixedDelay(() -> expiryCheck.checkUnbans(), 2, 10, TimeUnit.MINUTES);

		// Define a command client
		commandClient = new CommandClientBuilder()
			.setOwnerId(fileManager.getString("config", "owner-id"))
			.setServerInvite(Links.DISCORD)
			.setScheduleExecutor(scheduledExecutor)
			.setStatus(OnlineStatus.ONLINE)
			.setActivity(Activity.customStatus("/help"))
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
				new GhostCmd(this),
				new UnghostCmd(this),
				new PermsCmd(this),
				// guild
				new SetupCmd(this),
				new ModuleCmd(this, WAITER),
				new AccessCmd(this),
				new LogCmd(this, WAITER),
				// owner
				new ShutdownCmd(this),
				new EvalCmd(this),
				new InviteCmd(this),
				new GenerateListCmd(this),
				// webhook
				new WebhookCmd(this),
				// moderation
				new BanCmd(this, WAITER),
				new UnbanCmd(this, WAITER),
				new KickCmd(this, WAITER),
				new SyncCmd(this, WAITER),
				new CaseCmd(this),
				new ReasonCmd(this),
				new DurationCmd(this),
				new GroupCmd(this, WAITER),
				// other
				new PingCmd(this),
				new AboutCmd(this),
				new HelpCmd(this),
				new StatusCmd(this),
				// verify
				new VerifyPanelCmd(this),
				new VerifyRoleCmd(this),
				new VerifyCmd(this),
				new BlacklistCmd(this)
			)
			.setDevGuildIds(fileManager.getStringList("config", "dev-servers").toArray(new String[0]))
			.build();

		// Build
		acListener = new AutoCompleteListener(commandClient, dbUtil);

		final Set<GatewayIntent> intents = Set.of(
			GatewayIntent.GUILD_EMOJIS_AND_STICKERS,
			GatewayIntent.GUILD_INVITES,
			GatewayIntent.GUILD_MEMBERS,
			GatewayIntent.GUILD_MESSAGES,
			GatewayIntent.GUILD_MODERATION,
			GatewayIntent.GUILD_VOICE_STATES,
			GatewayIntent.GUILD_WEBHOOKS,
			GatewayIntent.MESSAGE_CONTENT
		);
		final Set<CacheFlag> enabledCacheFlags = Set.of(
			CacheFlag.EMOJI,
			CacheFlag.MEMBER_OVERRIDES,
			CacheFlag.STICKER,
			CacheFlag.ROLE_TAGS,
			CacheFlag.VOICE_STATE
		);
		final Set<CacheFlag> disabledCacheFlags = Set.of(
			CacheFlag.ACTIVITY,
			CacheFlag.CLIENT_STATUS,
			CacheFlag.ONLINE_STATUS,
			CacheFlag.SCHEDULED_EVENTS
		);

		JDABuilder mainBuilder = JDABuilder.create(fileManager.getString("config", "bot-token"), intents)
			.setMemberCachePolicy(MemberCachePolicy.ALL)	// cache all members
			.setChunkingFilter(ChunkingFilter.ALL)		// chunk all guilds
			.enableCache(enabledCacheFlags)
			.disableCache(disabledCacheFlags)
			.setBulkDeleteSplittingEnabled(false)
			.addEventListeners(commandClient, WAITER, acListener, buttonListener, guildListener, voiceListener);
			
		JDA tempJda = null;

		int retries = 4; // how many times will it try to build
		int cooldown = 8; // in seconds; cooldown amount, will doubles after each retry
		while (true) {
			try {
				tempJda = mainBuilder.build();
				break;
			} catch (InvalidTokenException ex) {
				logger.error("Login failed due to Token", ex);
				System.exit(0);
			} catch (ErrorResponseException ex) { // Tries to reconnect to discord x times with some delay, else exits
				if (retries > 0) {
					retries--;
					logger.info("Retrying connecting in "+cooldown+" seconds... "+retries+" more attempts");
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

		this.JDA = tempJda;
	}

	public CommandClient getClient() {
		return commandClient;
	}

	public Logger getAppLogger() {
		return logger;
	}

	public FileManager getFileManager() {
		return fileManager;
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

	public LocaleUtil getLocaleUtil() {
		return localeUtil;
	}

	public LogUtil getLogUtil() {
		return logUtil;
	}

	public LogListener getLogListener() {
		return logListener;
	}

	public static void main(String[] args) {
		instance = new App();
		instance.createWebhookAppender();
		instance.logger.info("Success start");
	}

	private void createWebhookAppender() {
		String url = getFileManager().getNullableString("config", "webhook");
		if (url == null) return;
		
		LoggerContext lc = (LoggerContext) LoggerFactory.getILoggerFactory();
		PatternLayoutEncoder ple = new PatternLayoutEncoder();
		ple.setPattern("%d{dd.MM.yyyy HH:mm:ss} [%thread] [%logger{0}] %msg%n");
		ple.setContext(lc);
		ple.start();
		WebhookAppender webhookAppender = new WebhookAppender();
		webhookAppender.setUrl(url);
		webhookAppender.setEncoder(ple);
		webhookAppender.setContext(lc);
		webhookAppender.start();

		Logger logbackLogger = (Logger) LoggerFactory.getLogger(Logger.ROOT_LOGGER_NAME);
		logbackLogger.addAppender(webhookAppender);
		logbackLogger.setAdditive(false);
	}
}
