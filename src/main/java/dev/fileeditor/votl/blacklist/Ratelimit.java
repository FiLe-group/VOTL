package dev.fileeditor.votl.blacklist;

import ch.qos.logback.classic.Logger;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.github.benmanes.caffeine.cache.LoadingCache;
import dev.fileeditor.votl.contracts.blacklist.PunishmentLevel;
import dev.fileeditor.votl.middleware.ThrottleMiddleware;
import dev.fileeditor.votl.utils.message.TimeUtil;
import net.dv8tion.jda.api.events.interaction.command.GenericCommandInteractionEvent;
import org.jetbrains.annotations.Nullable;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

public class Ratelimit {

	static final int hitLimit = 10;

	static final long hitTime = 30 * 1000; // 30 seconds

	public static final LoadingCache<Long, Rate> cache = Caffeine.newBuilder()
		.expireAfterWrite(hitTime, TimeUnit.MILLISECONDS)
		.build(Rate::new);

	private static final Logger LOG = (Logger) LoggerFactory.getLogger(Ratelimit.class);

	private static final Map<Long, Integer> punishments = new HashMap<>();

	private static final List<PunishmentLevel> levels = List.of(
		() -> Instant.now().plus(1, ChronoUnit.MINUTES),
		() -> Instant.now().plus(15, ChronoUnit.MINUTES),
		() -> Instant.now().plus(30, ChronoUnit.MINUTES),
		() -> Instant.now().plus(1, ChronoUnit.HOURS),
		() -> Instant.now().plus(6, ChronoUnit.HOURS),
		() -> Instant.now().plus(12, ChronoUnit.HOURS),
		() -> Instant.now().plus(1, ChronoUnit.DAYS),
		() -> Instant.now().plus(3, ChronoUnit.DAYS),
		() -> Instant.now().plus(1, ChronoUnit.WEEKS)
	);

	private final Blacklist blacklist;

	Ratelimit(Blacklist blacklist) {
		this.blacklist = blacklist;
	}

	@Nullable
	public Instant hit(ThrottleMiddleware.ThrottleType type, GenericCommandInteractionEvent event) {
		final long id = type.getSnowflake(event).getIdLong();

		Rate rate = cache.get(id);
		if (rate == null) {
			return null;
		}

		synchronized (rate) {
			rate.hit();

			if (rate.getHits() < hitLimit) {
				return null;
			}
		}

		Long last = rate.getLast();

		if (last != null && last < System.currentTimeMillis() - 2500) {
			return null;
		}

		Instant punishment = getPunishment(id);

		LOG.info("{}:{} has been added to blacklist for excessive command usage, the blacklist expires {}.",
			type.getName(), id, TimeUtil.timeToString(punishment)
		);

		blacklist.addToBlacklist(
			type.equals(ThrottleMiddleware.ThrottleType.USER) ? Scope.USER : Scope.GUILD,
			id,
			"Automatic blacklist due to excessive command usage.",
			punishment
		);

		return punishment;
	}

	private Instant getPunishment(long userId) {
		int level = punishments.getOrDefault(userId, -1) + 1;

		punishments.put(userId, level);

		return getPunishment(level);
	}

	private Instant getPunishment(int level) {
		if (level < 0) {
			return levels.getFirst().generateTime();
		}
		return levels.get(level >= levels.size() ? levels.size()-1 : level).generateTime();
	}
}
