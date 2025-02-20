package dev.fileeditor.votl;

import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

import dev.fileeditor.votl.base.command.CommandClient;
import dev.fileeditor.votl.base.command.CommandClientBuilder;
import dev.fileeditor.votl.base.waiter.EventWaiter;
import dev.fileeditor.votl.commands.games.GameCmd;
import dev.fileeditor.votl.commands.games.GameStrikeCmd;
import dev.fileeditor.votl.commands.guild.*;
import dev.fileeditor.votl.commands.level.LeaderboardCmd;
import dev.fileeditor.votl.commands.level.LevelExemptCmd;
import dev.fileeditor.votl.commands.level.LevelRolesCmd;
import dev.fileeditor.votl.commands.level.UserProfileCmd;
import dev.fileeditor.votl.commands.moderation.*;
import dev.fileeditor.votl.commands.other.*;
import dev.fileeditor.votl.commands.owner.*;
import dev.fileeditor.votl.commands.role.*;
import dev.fileeditor.votl.commands.strike.*;
import dev.fileeditor.votl.commands.ticketing.*;
import dev.fileeditor.votl.commands.verification.*;
import dev.fileeditor.votl.commands.voice.VoiceCmd;
import dev.fileeditor.votl.commands.webhook.WebhookCmd;
import dev.fileeditor.votl.listeners.*;
import dev.fileeditor.votl.menus.ActiveModlogsMenu;
import dev.fileeditor.votl.menus.ModlogsMenu;
import dev.fileeditor.votl.menus.ReportMenu;
import dev.fileeditor.votl.metrics.Metrics;
import dev.fileeditor.votl.objects.constants.Constants;
import dev.fileeditor.votl.objects.constants.Links;
import dev.fileeditor.votl.services.CountingThreadFactory;
import dev.fileeditor.votl.services.ScheduledCheck;
import dev.fileeditor.votl.servlet.WebServlet;
import dev.fileeditor.votl.servlet.routes.*;
import dev.fileeditor.votl.utils.*;
import dev.fileeditor.votl.utils.database.DBUtil;
import dev.fileeditor.votl.utils.file.FileManager;
import dev.fileeditor.votl.utils.file.lang.LocaleUtil;
import dev.fileeditor.votl.utils.imagegen.UserBackgroundHandler;
import dev.fileeditor.votl.utils.level.LevelUtil;
import dev.fileeditor.votl.utils.logs.GuildLogger;
import dev.fileeditor.votl.utils.logs.LogEmbedUtil;
import dev.fileeditor.votl.utils.message.EmbedUtil;
import dev.fileeditor.votl.utils.message.MessageUtil;

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
	
	private final Logger log = (Logger) LoggerFactory.getLogger(App.class);

	public final String VERSION = Optional.ofNullable(App.class.getPackage().getImplementationVersion()).map(v -> "v"+v).orElse("DEVELOPMENT");

	public final JDA JDA;
	public final EventWaiter WAITER;
	private final CommandClient commandClient;

	private final FileManager fileManager = new FileManager();

	private final GuildLogger guildLogger;
	private final LogEmbedUtil logEmbedUtil;

	private final DBUtil dbUtil;
	private final MessageUtil messageUtil;
	private final EmbedUtil embedUtil;
	private final CheckUtil checkUtil;
	private final LocaleUtil localeUtil;
	private final TicketUtil ticketUtil;
	private final GroupHelper groupHelper;
	private final ModerationUtil moderationUtil;
	private final LevelUtil levelUtil;

	private final WebServlet servlet;

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
		messageUtil	= new MessageUtil(localeUtil);
		embedUtil	= new EmbedUtil(localeUtil);
		checkUtil	= new CheckUtil(this, ownerId);
		ticketUtil	= new TicketUtil(this);
		moderationUtil = new ModerationUtil(dbUtil, localeUtil);
		levelUtil	= new LevelUtil(this);

		logEmbedUtil	= new LogEmbedUtil();
		guildLogger		= new GuildLogger();

		WAITER			= new EventWaiter();
		groupHelper		= new GroupHelper(this);
		CommandListener commandListener = new CommandListener(localeUtil);
		InteractionListener interactionListener = new InteractionListener(this, WAITER);

		ScheduledExecutorService scheduledExecutor = new ScheduledThreadPoolExecutor(3, new CountingThreadFactory("VOTL", "Scheduler", false));

		GuildListener guildListener = new GuildListener(this);
		VoiceListener voiceListener = new VoiceListener(this, scheduledExecutor);
		ModerationListener moderationListener = new ModerationListener(this);
		AuditListener auditListener = new AuditListener(dbUtil, guildLogger);
		MemberListener memberListener = new MemberListener(this);
		MessageListener messageListener = new MessageListener(this);
		EventListener eventListener = new EventListener();

		ScheduledCheck scheduledCheck = new ScheduledCheck(this);
		scheduledExecutor.scheduleWithFixedDelay(scheduledCheck::regularChecks, 2, 3, TimeUnit.MINUTES);
		scheduledExecutor.scheduleWithFixedDelay(scheduledCheck::irregularChecks, 3, 10, TimeUnit.MINUTES);

		// Start backend server
		final Boolean servletEnabled = fileManager.getBoolean("config", "web-servlet.enabled");
		if (servletEnabled != null && servletEnabled) {
			final int port = Optional.ofNullable(fileManager.getInteger("config", "web-servlet.port")).orElse(WebServlet.DEFAULT_PORT);
			final String allowedHost = fileManager.getString("config", "web-servlet.allow-host");
			final long clientId = Long.parseLong(fileManager.getString("config", "web-servlet.client-id"));
			final String clientSecret = fileManager.getString("config", "web-servlet.client-secret");

			servlet = new WebServlet(port, allowedHost, clientId, clientSecret);
			// Register routes
			// Getters
			servlet.registerGet("/guilds/{guild}", new GetGuild());
			servlet.registerGet("/guilds/{guild}/roles", new GetRoles());
			servlet.registerGet("/guilds/{guild}/channels", new GetChannels());
			servlet.registerGet("/guilds/{guild}/members/@me", new GetMemberSelf());
			// Modules
			servlet.registerGet("/guilds/{guild}/modules/{module}", new GetModule()); // Get data
			servlet.registerDelete("/guilds/{guild}/modules/{module}", new DeleteModule()); // Disable
			servlet.registerPut("/guilds/{guild}/modules/{module}", new PutModule()); // Enable
			servlet.registerPatch("/guilds/{guild}/modules/{module}", new PatchModule()); // Update
			// Auth
			servlet.registerGet("/login", new ApiLogin());
			servlet.registerGet("/logout", new ApiLogout());
			servlet.registerGet("/callback", new ApiCallback());
		} else {
			servlet = null;
		}

		// Define a command client
		commandClient = new CommandClientBuilder()
			.setOwnerId(ownerId)
			.setServerInvite(Links.DISCORD)
			.setScheduleExecutor(scheduledExecutor)
			.setStatus(OnlineStatus.ONLINE)
			.setActivity(Activity.customStatus("/help"))
			.addSlashCommands(
				// guild
				new AccessCmd(),
				new AutopunishCmd(),
				new LogsCmd(),
				new ModuleCmd(WAITER),
				new SetupCmd(),
				new PersistentRoleCmd(),
				// moderation
				new BanCmd(),
				new BlacklistCmd(),
				new CaseCmd(),
				new DurationCmd(),
				new GroupCmd(WAITER),
				new KickCmd(WAITER),
				new MuteCmd(),
				new ModLogsCmd(),
				new ModStatsCmd(),
				new ReasonCmd(),
				new SyncCmd(WAITER),
				new UnbanCmd(),
				new UnmuteCmd(),
				new PurgeCmd(),
				new ModReportCmd(),
				// other
				new AboutCmd(),
				new HelpCmd(),
				new PingCmd(),
				new StatusCmd(),
				// owner
				new EvalCmd(),
				new ForceAccessCmd(),
				new GenerateListCmd(),
				new ShutdownCmd(),
				new DebugCmd(),
				new MessageCmd(),
				new SetStatusCmd(),
				new CheckAccessCmd(),
				new BotBlacklist(),
				new ExperienceCmd(),
				// role
				new RoleCmd(),
				new TempRoleCmd(),
				// strike
				new ClearStrikesCmd(),
				new DeleteStrikeCmd(WAITER),
				new StrikeCmd(),
				new StrikesCmd(),
				// ticketing
				new AddUserCmd(),
				new CloseCmd(),
				new RcloseCmd(),
				new RemoveUserCmd(),
				new RolesManageCmd(),
				new RolesPanelCmd(),
				new TicketCountCmd(),
				new TicketCmd(),
				// verification
				new VerifyPanelCmd(),
				new VerifyRoleCmd(),
				// voice
				new VoiceCmd(),
				// webhook
				new WebhookCmd(),
				// game
				new GameCmd(),
				new GameStrikeCmd(),
				// level
				new UserProfileCmd(),
				new LeaderboardCmd(),
				new LevelRolesCmd(),
				new LevelExemptCmd()
			)
			.addContextMenus(
				new ReportMenu(),
				new ModlogsMenu(),
				new ActiveModlogsMenu()
			)
			.setListener(commandListener)
			.setDevGuildIds(fileManager.getStringList("config", "dev-servers").toArray(new String[0]))
			.build();

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
				commandClient, WAITER, acListener, interactionListener,
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
				log.error("Login failed due to Token", ex);
				System.exit(0);
			} catch (ErrorResponseException ex) { // Tries to reconnect to discord x times with some delay, else exits
				if (retries > 0) {
					retries--;
					log.info("Retrying connecting in {} seconds... {} more attempts", cooldown, retries);
					try {
						Thread.sleep(cooldown*1000L);
					} catch (InterruptedException e) {
						log.error("Thread sleep interrupted", e);
					}
					cooldown*=2;
				} else {
					log.error("No network connection or couldn't connect to DNS", ex);
					System.exit(0);
				}
			}
		}

		this.JDA = tempJda;

		createWebhookAppender();

		log.info("Creating user backgrounds...");
		try {
			UserBackgroundHandler.getInstance().start();
		} catch (Throwable ex) {
			log.error("Error starting background handler", ex);
		}

		log.info("Preparing and setting up metrics.");
		Metrics.setup();

		log.info("Success start");
	}

	public static App getInstance() {
		return instance;
	}

	public CommandClient getClient() {
		return commandClient;
	}

	public Logger getAppLogger() {
		return log;
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

	public WebServlet getServlet() {
		return servlet;
	}

	public LevelUtil getLevelUtil() {
		return levelUtil;
	}

	public void shutdownUtils() {
		WebServlet.shutdown();
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
