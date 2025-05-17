package dev.fileeditor.votl;

import java.util.Optional;
import java.util.Set;

import dev.fileeditor.votl.base.command.CommandClient;
import dev.fileeditor.votl.base.command.CommandClientBuilder;
import dev.fileeditor.votl.base.command.SlashCommand;
import dev.fileeditor.votl.base.waiter.EventWaiter;
import dev.fileeditor.votl.blacklist.Blacklist;
import dev.fileeditor.votl.contracts.scheduler.Job;
import dev.fileeditor.votl.listeners.*;
import dev.fileeditor.votl.menus.ActiveModlogsMenu;
import dev.fileeditor.votl.menus.ModlogsMenu;
import dev.fileeditor.votl.menus.ReportMenu;
import dev.fileeditor.votl.middleware.MiddlewareHandler;
import dev.fileeditor.votl.middleware.ThrottleMiddleware;
import dev.fileeditor.votl.middleware.global.HasAccess;
import dev.fileeditor.votl.objects.constants.Constants;
import dev.fileeditor.votl.objects.constants.Names;
import dev.fileeditor.votl.scheduler.ScheduleHandler;
import dev.fileeditor.votl.utils.*;
import dev.fileeditor.votl.utils.database.DBUtil;
import dev.fileeditor.votl.utils.file.FileManager;
import dev.fileeditor.votl.utils.file.lang.LocaleUtil;
import dev.fileeditor.votl.utils.imagegen.UserBackgroundHandler;
import dev.fileeditor.votl.utils.level.LevelUtil;
import dev.fileeditor.votl.utils.logs.GuildLogger;
import dev.fileeditor.votl.utils.logs.LogEmbedUtil;
import dev.fileeditor.votl.utils.message.EmbedUtil;

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

import static java.lang.Long.parseLong;

public class App {
	protected static App instance;
	
	private final Logger LOG = (Logger) LoggerFactory.getLogger(App.class);

	public final String VERSION = Optional.ofNullable(App.class.getPackage().getImplementationVersion()).map(v -> "v"+v).orElse("DEVELOPMENT");

	public final JDA JDA;
	private final CommandClient commandClient;
	private final EventWaiter eventWaiter;

	private final FileManager fileManager = new FileManager();

	private final GuildLogger guildLogger;
	private final LogEmbedUtil logEmbedUtil;

	private final DBUtil dbUtil;
	private final EmbedUtil embedUtil;
	private final CheckUtil checkUtil;
	private final LocaleUtil localeUtil;
	private final TicketUtil ticketUtil;
	private final GroupHelper groupHelper;
	private final ModerationUtil moderationUtil;
	private final LevelUtil levelUtil;

	private final Blacklist blacklist;

	@SuppressWarnings("BusyWait")
	public App() {
		App.instance = this;

		try {
			fileManager.addFile("config", "/config.json", Constants.DATA_PATH + "config.json")
				.addFile("database", "/server.db", Constants.DATA_PATH + "server.db")
				.addFileUpdate("backgrounds", "/backgrounds/index.json", Constants.DATA_PATH+"backgrounds"+Constants.SEPAR+"main.json")
				.addLang("en-GB")
				.addLang("ru");
		} catch (Exception ex) {
			System.out.println(ex.getMessage());
			System.exit(0);
		}

		final long ownerId = parseLong(fileManager.getString("config", "owner-id"));
		
		// Define for default
		dbUtil		= new DBUtil(getFileManager());
		localeUtil	= new LocaleUtil(this, DiscordLocale.ENGLISH_UK);
		embedUtil	= new EmbedUtil(localeUtil);
		checkUtil	= new CheckUtil(this, ownerId);
		ticketUtil	= new TicketUtil(this);
		moderationUtil = new ModerationUtil(dbUtil, localeUtil);
		levelUtil	= new LevelUtil(this);

		logEmbedUtil	= new LogEmbedUtil();
		guildLogger		= new GuildLogger(this, logEmbedUtil);
		groupHelper		= new GroupHelper(this);

		eventWaiter = new EventWaiter();

		CommandListener commandListener = new CommandListener(localeUtil);
		InteractionListener interactionListener = new InteractionListener(this, eventWaiter);

		GuildListener guildListener = new GuildListener(this);
		VoiceListener voiceListener = new VoiceListener(this);
		ModerationListener moderationListener = new ModerationListener(this);
		AuditListener auditListener = new AuditListener(dbUtil, guildLogger);
		MemberListener memberListener = new MemberListener(this);
		MessageListener messageListener = new MessageListener(this);
		EventListener eventListener = new EventListener(dbUtil);

		// Define a command client
		CommandClientBuilder commandClientBuilder = new CommandClientBuilder()
			.setOwnerId(ownerId)
			.setStatus(OnlineStatus.ONLINE)
			.setActivity(Activity.customStatus("/help"))
			.addContextMenus(
				new ReportMenu(),
				new ModlogsMenu(),
				new ActiveModlogsMenu()
			)
			.setListener(commandListener)
			.setDevGuildIds(fileManager.getStringList("config", "dev-servers").toArray(new String[0]));

		LOG.info("Registering default middlewares");
		MiddlewareHandler.initialize(this);
		MiddlewareHandler.register("throttle", new ThrottleMiddleware(this));
		MiddlewareHandler.register("hasAccess", new HasAccess(this));

		LOG.info("Registering commands...");
		AutoloaderUtil.load(Names.PACKAGE_COMMAND_PATH, command -> commandClientBuilder.addSlashCommands((SlashCommand) command), false);

		commandClient = commandClientBuilder.build();
		// Build
		AutoCompleteListener acListener = new AutoCompleteListener(commandClient, dbUtil);

		final Set<GatewayIntent> intents = Set.of(
			GatewayIntent.GUILD_EXPRESSIONS,
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
			.setChunkingFilter(ChunkingFilter.ALL)			// enable chunking
			.enableCache(enabledCacheFlags)
			.disableCache(disabledCacheFlags)
			.setBulkDeleteSplittingEnabled(false)
			.addEventListeners(
				commandClient, eventWaiter, acListener, interactionListener,
				guildListener, voiceListener, moderationListener, messageListener,
				auditListener, memberListener, eventListener
			);
			
		JDA tempJda;

		// try to log in
		int retries = 4; // how many times will it try to build
		int cooldown = 8; // in seconds; cooldown amount, will doubles after each retry
		while (true) {
			try {
				tempJda = mainBuilder.build();
				break;
			} catch (IllegalArgumentException | InvalidTokenException ex) {
				LOG.error("Login failed due to Token", ex);
				System.exit(0);
			} catch (ErrorResponseException ex) { // Tries to reconnect to discord x times with some delay, else exits
				if (retries > 0) {
					retries--;
					LOG.info("Retrying connecting in {} seconds... {} more attempts", cooldown, retries);
					try {
						Thread.sleep(cooldown*1000L);
					} catch (InterruptedException e) {
						LOG.error("Thread sleep interrupted", e);
					}
					cooldown*=2;
				} else {
					LOG.error("No network connection or couldn't connect to DNS", ex);
					System.exit(0);
				}
			}
		}

		this.JDA = tempJda;

		// logger
		createWebhookAppender();


		LOG.info("Registering jobs...");
		AutoloaderUtil.load(Names.PACKAGE_JOB_PATH, job -> ScheduleHandler.registerJob((Job) job));
		LOG.info("Registered {} jobs successfully!", ScheduleHandler.entrySet().size());

		LOG.info("Preparing blacklist");
		blacklist = new Blacklist(this);
		blacklist.syncBlacklistWithDatabase();

		LOG.info("Creating user backgrounds");
		UserBackgroundHandler.getInstance().start();

		LOG.info("Success start");
	}

	public static App getInstance() {
		return instance;
	}

	public CommandClient getClient() {
		return commandClient;
	}

	public Logger getAppLogger() {
		return LOG;
	}

	public FileManager getFileManager() {
		return fileManager;
	}

	public DBUtil getDBUtil() {
		return dbUtil;
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

	public GuildLogger getLogger() {
		return guildLogger;
	}

	public LogEmbedUtil getLogEmbedUtil() {
		return logEmbedUtil;
	}

	public TicketUtil getTicketUtil() {
		return ticketUtil;
	}

	public GroupHelper getHelper() {
		return groupHelper;
	}

	public ModerationUtil getModerationUtil() {
		return moderationUtil;
	}

	public LevelUtil getLevelUtil() {
		return levelUtil;
	}

	public Blacklist getBlacklist() {
		return blacklist;
	}

	public EventWaiter getEventWaiter() {
		return eventWaiter;
	}

	public void shutdownUtils() {
		for (var future : ScheduleHandler.entrySet()) {
			future.cancel(false);
		}
	}

	private void createWebhookAppender() {
		String url = getFileManager().getNullableString("config", "webhook");
		if (url == null) return;
		
		LoggerContext lc = (LoggerContext) LoggerFactory.getILoggerFactory();
		PatternLayoutEncoder ple = new PatternLayoutEncoder();
		ple.setPattern("%d{dd.MM.yyyy HH:mm:ss} [%thread] [%logger{0}] %ex{10}%n");
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
