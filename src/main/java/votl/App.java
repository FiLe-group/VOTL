package votl;

import java.util.Objects;
import java.util.Optional;
import java.util.Random;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

import votl.commands.guild.*;
import votl.commands.moderation.*;
import votl.commands.other.*;
import votl.commands.owner.*;
import votl.commands.verification.*;
import votl.commands.voice.*;
import votl.commands.webhook.WebhookCmd;
import votl.listeners.*;
import votl.objects.command.CommandClient;
import votl.objects.command.CommandClientBuilder;
import votl.objects.constants.Constants;
import votl.objects.constants.Links;
import votl.services.ExpiryCheck;
import votl.utils.*;
import votl.utils.database.DBUtil;
import votl.utils.file.FileManager;
import votl.utils.file.lang.LangUtil;
import votl.utils.message.*;

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

import com.jagrosh.jdautilities.commons.waiter.EventWaiter;

import org.slf4j.LoggerFactory;

import ch.qos.logback.classic.Logger;

public class App {
	
	private final Logger logger = (Logger) LoggerFactory.getLogger(App.class);

	private static App instance;

	public final String version = Optional.ofNullable(App.class.getPackage().getImplementationVersion()).map(v -> "v"+v).orElse("DEVELOPMENT");

	public final JDA jda;
	public final EventWaiter waiter;

	private final FileManager fileManager = new FileManager();

	private final Random random = new Random();

	private final GuildListener guildListener;
	private final VoiceListener voiceListener;
	private final AutoCompleteListener acListener;
	private final ButtonListener buttonListener;

	private final LogListener logListener;
	
	private final ScheduledExecutorService executorService;
	private final ExpiryCheck expiryCheck;
	
	private final DBUtil dbUtil;
	private final MessageUtil messageUtil;
	private final EmbedUtil embedUtil;
	private final LangUtil langUtil;
	private final CheckUtil checkUtil;
	private final LocaleUtil localeUtil;
	private final TimeUtil timeUtil;
	private final LogUtil logUtil;
	private final SteamUtil steamUtil;

	public App() {

		JDA setJda = null;

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
		dbUtil		= new DBUtil(getFileManager().getFiles().get("database"));
		langUtil	= new LangUtil(this);
		localeUtil	= new LocaleUtil(this, langUtil, "en-GB", DiscordLocale.ENGLISH_UK);
		messageUtil	= new MessageUtil(this);
		embedUtil	= new EmbedUtil(localeUtil);
		checkUtil	= new CheckUtil(this);
		timeUtil	= new TimeUtil();
		logUtil		= new LogUtil(this);
		steamUtil	= new SteamUtil();

		waiter			= new EventWaiter();
		guildListener	= new GuildListener(this);
		voiceListener	= new VoiceListener(this);
		logListener		= new LogListener(this);
		buttonListener	= new ButtonListener(this);

		executorService = new ScheduledThreadPoolExecutor(2, r -> new Thread(r, "VOTL Scheduler"));
		expiryCheck		= new ExpiryCheck(this);

		// Define a command client
		CommandClient commandClient = new CommandClientBuilder()
			.setOwnerId(fileManager.getString("config", "owner-id"))
			.setServerInvite(Links.DISCORD)
			.setEmojis(Constants.SUCCESS, Constants.WARNING, Constants.FAILURE)
			.useHelpBuilder(false)
			.setScheduleExecutor(executorService)
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
				new GhostCmd(this),
				new UnghostCmd(this),
				new PermsCmd(this),
				// guild
				new SetupCmd(this),
				new ModuleCmd(this, waiter),
				new AccessCmd(this),
				new LogCmd(this, waiter),
				// owner
				new ShutdownCmd(this),
				new EvalCmd(this),
				new InviteCmd(this),
				new GenerateListCmd(this),
				// webhook
				new WebhookCmd(this),
				// moderation
				new BanCmd(this, waiter),
				new UnbanCmd(this, waiter),
				new KickCmd(this, waiter),
				new SyncCmd(this, waiter),
				new CaseCmd(this),
				new ReasonCmd(this),
				new DurationCmd(this),
				new GroupCmd(this, waiter),
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
		MemberCachePolicy policy = MemberCachePolicy.VOICE			// check if in voice
			.or(Objects.requireNonNull(MemberCachePolicy.OWNER));	// check for owner
		
		acListener = new AutoCompleteListener(commandClient, dbUtil);

		JDABuilder jdaBuilder = JDABuilder.createLight(fileManager.getString("config", "bot-token"))
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
			.addEventListeners(commandClient, waiter, guildListener, voiceListener, acListener, buttonListener);

		Integer retries = 4; // how many times will it try to build
		Integer cooldown = 8; // in seconds; cooldown amount, will doubles after each retry
		while (true) {
			try {
				setJda = jdaBuilder.build();
				break;
			} catch (InvalidTokenException ex) {
				logger.error("Login failed due to Token", ex);
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

		executorService.scheduleWithFixedDelay(() -> expiryCheck.checkUnbans(), 5, 15, TimeUnit.MINUTES);

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

	public LocaleUtil getLocaleUtil() {
		return localeUtil;
	}

	public TimeUtil getTimeUtil() {
		return timeUtil;
	}

	public LogUtil getLogUtil() {
		return logUtil;
	}

	public SteamUtil getSteamUtil() {
		return steamUtil;
	}

	public LogListener getLogListener() {
		return logListener;
	}

	public static void main(String[] args) {
		instance = new App();
		instance.logger.info("Success start");
	}
}
