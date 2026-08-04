package dev.fileeditor.votl.listeners;

import ch.qos.logback.classic.Logger;
import dev.fileeditor.votl.App;
import dev.fileeditor.votl.metrics.Metrics;
import dev.fileeditor.votl.utils.database.DBUtil;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.events.GenericEvent;
import net.dv8tion.jda.api.events.session.ReadyEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import org.jetbrains.annotations.NotNull;
import org.slf4j.LoggerFactory;

public class EventListener extends ListenerAdapter {

	/** Gap between invite priming requests, so a restart does not burst the rate limiter. */
	private static final long PRIME_STAGGER_MS = 250;

	private final Logger LOG = (Logger) LoggerFactory.getLogger(EventListener.class);

	private final App bot;
	private final DBUtil db;

	public EventListener(App bot) {
		this.bot = bot;
		this.db = bot.getDBUtil();
	}

	@Override
	public void onGenericEvent(@NotNull GenericEvent event) {
		Metrics.jdaEvents.labelValue(event.getClass().getSimpleName()).inc();
	}

	@Override
	public void onReady(@NotNull ReadyEvent event) {
		// Check voice channels
		try {
			db.voice.checkCache(event.getJDA());
			LOG.debug("Voice cache checked");
		} catch (Throwable ex) {
			LOG.error("Error checking custom voice channels cache", ex);
		}

		// Load invite snapshots for guilds that track them
		try {
			int primed = 0;
			for (Guild guild : event.getJDA().getGuilds()) {
				if (!db.getGuildSettings(guild).isInviteTrackerEnabled()) continue;
				bot.getInviteTracker().prime(guild, primed * PRIME_STAGGER_MS);
				primed++;
			}
			LOG.debug("Priming invite cache for {} guild(s)", primed);
		} catch (Throwable ex) {
			LOG.error("Error priming invite cache", ex);
		}
	}

}
