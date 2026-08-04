package dev.fileeditor.votl.utils.invite;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.events.guild.invite.GuildInviteCreateEvent;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.slf4j.LoggerFactory;

import ch.qos.logback.classic.Logger;

/**
 * Works out which invite a joining member used.
 * <p>Discord never reports this, so the only way is to keep a snapshot of every invite's use count
 * and diff it against a fresh fetch on each join.
 *
 * <p>Snapshots live in memory only: they are primed on ready, on guild join and on demand, and kept
 * roughly in step by invite create/delete events. Nothing is persisted, since a resolution is only
 * ever needed at the instant a member joins.
 */
public class InviteTracker {

	/** How long a deleted invite stays available for matching. Covers the join/delete event race. */
	private static final long DELETED_RETENTION_SECONDS = 30;

	private final Logger log = (Logger) LoggerFactory.getLogger(InviteTracker.class);

	/** guildId -> (code -> invite) */
	private final Map<Long, Map<String, CachedInvite>> snapshots = new ConcurrentHashMap<>();
	/** guildId -> vanity uses */
	private final Map<Long, Integer> vanityUses = new ConcurrentHashMap<>();
	/** guildId -> (code -> recently deleted invite) */
	private final Map<Long, Map<String, DeletedInvite>> deleted = new ConcurrentHashMap<>();
	/** guildId -> tail of that guild's resolution chain, so joins are diffed one at a time */
	private final Map<Long, CompletableFuture<Void>> chains = new ConcurrentHashMap<>();

	private record DeletedInvite(CachedInvite invite, Instant at) {}

	/** Reading invites needs Manage Server. */
	public boolean canTrack(@NotNull Guild guild) {
		return guild.getSelfMember().hasPermission(Permission.MANAGE_SERVER);
	}

	/**
	 * Loads a guild's invites into the snapshot without resolving anything.
	 *
	 * @param delayMillis Stagger, so priming many guilds at once does not burst the rate limiter.
	 */
	public void prime(@NotNull Guild guild, long delayMillis) {
		if (!canTrack(guild)) return;
		final long guildId = guild.getIdLong();
		guild.retrieveInvites().queueAfter(delayMillis, TimeUnit.MILLISECONDS, invites -> {
			snapshots.put(guildId, toSnapshot(invites));
			// A full fetch supersedes whatever was buffered
			deleted.remove(guildId);
			primeVanity(guild);
		}, failure -> log.debug("Could not prime invites for guild {}: {}", guildId, failure.getMessage()));
	}

	private void primeVanity(Guild guild) {
		if (guild.getVanityCode() == null) return;
		guild.retrieveVanityInvite().queue(
			vanity -> vanityUses.put(guild.getIdLong(), vanity.getUses()),
			failure -> log.debug("Could not prime vanity invite for guild {}: {}", guild.getIdLong(), failure.getMessage())
		);
	}

	/**
	 * Re-reads the guild's invites and reports which one advanced since the last snapshot.
	 * <p>Calls are serialized per guild: two members joining at the same moment would otherwise both
	 * diff against the same stale snapshot and each see the other's increment.
	 */
	@NotNull
	public CompletableFuture<InviteInfo> resolve(@NotNull Guild guild) {
		final CompletableFuture<InviteInfo> result = new CompletableFuture<>();
		chains.compute(guild.getIdLong(), (_, previous) -> {
			CompletableFuture<Void> base = previous == null ? CompletableFuture.completedFuture(null) : previous;
			// The handle() links keep one failed resolution from poisoning the chain for later joins
			return base.handle((_, _) -> (Void) null)
				.thenCompose(_ -> resolveOnce(guild))
				.handle((info, error) -> {
					if (error != null) {
						log.debug("Could not resolve invite for guild {}: {}", guild.getIdLong(), error.getMessage());
						result.complete(InviteInfo.of(InviteInfo.Source.UNAVAILABLE));
					} else {
						result.complete(info);
					}
					//noinspection RedundantCast
					return (Void) null;
				});
		});
		return result;
	}

	private CompletableFuture<InviteInfo> resolveOnce(Guild guild) {
		if (!canTrack(guild)) {
			return CompletableFuture.completedFuture(InviteInfo.of(InviteInfo.Source.UNAVAILABLE));
		}
		return guild.retrieveInvites().submit()
			.thenCompose(list -> {
				InviteInfo matched = diffInvites(guild.getIdLong(), toSnapshot(list));
				// Only when no normal invite accounts for the join is the vanity counter worth a
				// second request - that keeps the common case at one call per join
				return matched != null ? CompletableFuture.completedFuture(matched) : checkVanity(guild);
			});
	}

	/**
	 * Diffs a fresh listing against the stored snapshot and adopts it.
	 *
	 * @return The matched invite, or {@code null} when no normal invite accounts for the join.
	 */
	@Nullable
	private InviteInfo diffInvites(long guildId, Map<String, CachedInvite> fresh) {
		final Map<String, CachedInvite> before = snapshots.get(guildId);
		final Map<String, DeletedInvite> recentlyDeleted = pruneDeleted(guildId);

		// Adopt the fresh state no matter what is concluded below
		snapshots.put(guildId, fresh);

		if (before == null) return InviteInfo.of(InviteInfo.Source.NOT_CACHED);

		// 1. An invite whose counter went up
		List<CachedInvite> advanced = new ArrayList<>();
		for (CachedInvite invite : fresh.values()) {
			CachedInvite previous = before.get(invite.code());
			int previousUses = previous == null ? 0 : previous.uses();
			if (invite.uses() > previousUses) advanced.add(invite);
		}
		if (advanced.size() == 1) {
			CachedInvite match = advanced.getFirst();
			return InviteInfo.invite(match, match.uses());
		}
		if (advanced.size() > 1) return InviteInfo.of(InviteInfo.Source.AMBIGUOUS);

		// 2. A single-use invite Discord dropped the moment it was spent. It may still sit in the
		//    snapshot, or the delete event may have moved it to the buffer already.
		Map<String, CachedInvite> gone = new HashMap<>(before);
		recentlyDeleted.forEach((code, entry) -> gone.putIfAbsent(code, entry.invite()));

		List<CachedInvite> exhausted = new ArrayList<>();
		for (CachedInvite invite : gone.values()) {
			if (fresh.containsKey(invite.code())) continue;
			if (invite.isAboutToBeExhausted()) exhausted.add(invite);
		}
		if (exhausted.size() == 1) {
			CachedInvite match = exhausted.getFirst();
			return InviteInfo.invite(match, match.maxUses());
		}
		if (exhausted.size() > 1) return InviteInfo.of(InviteInfo.Source.AMBIGUOUS);

		return null;
	}

	/**
	 * Last resort once no normal invite matched: either the vanity URL advanced, or nothing was
	 * consumed at all and the member arrived through the widget or server discovery.
	 */
	private CompletableFuture<InviteInfo> checkVanity(Guild guild) {
		final long guildId = guild.getIdLong();
		if (guild.getVanityCode() == null) {
			return CompletableFuture.completedFuture(InviteInfo.of(InviteInfo.Source.WIDGET_OR_DISCOVERY));
		}
		return guild.retrieveVanityInvite().submit().handle((vanity, error) -> {
			if (error != null) return InviteInfo.of(InviteInfo.Source.UNAVAILABLE);

			Integer before = vanityUses.put(guildId, vanity.getUses());
			// Without a baseline there is nothing to compare, so no claim can be made either way
			if (before == null) return InviteInfo.of(InviteInfo.Source.NOT_CACHED);
			if (vanity.getUses() > before) return InviteInfo.vanity(vanity.getCode(), vanity.getUses());
			return InviteInfo.of(InviteInfo.Source.WIDGET_OR_DISCOVERY);
		});
	}

	public void onInviteCreate(@NotNull GuildInviteCreateEvent event) {
		final long guildId = event.getGuild().getIdLong();
		Map<String, CachedInvite> snapshot = snapshots.get(guildId);
		// Only track guilds that already have a snapshot - otherwise the first diff would be built
		// from this single invite and wrongly treat every other one as new
		if (snapshot == null) return;
		CachedInvite cached = toCached(event.getInvite());
		snapshot.put(cached.code(), cached);
	}

	public void onInviteDelete(long guildId, @NotNull String code) {
		Map<String, CachedInvite> snapshot = snapshots.get(guildId);
		if (snapshot == null) return;
		CachedInvite removed = snapshot.remove(code);
		if (removed == null) return;
		deleted.computeIfAbsent(guildId, _ -> new ConcurrentHashMap<>())
			.put(code, new DeletedInvite(removed, Instant.now()));
	}

	/** Drops everything held for a guild, e.g. once the bot leaves it or tracking is turned off. */
	public void remove(long guildId) {
		snapshots.remove(guildId);
		vanityUses.remove(guildId);
		deleted.remove(guildId);
		chains.remove(guildId);
	}

	private Map<String, DeletedInvite> pruneDeleted(long guildId) {
		Map<String, DeletedInvite> map = deleted.get(guildId);
		if (map == null) return Map.of();
		Instant cutoff = Instant.now().minusSeconds(DELETED_RETENTION_SECONDS);
		map.values().removeIf(entry -> entry.at().isBefore(cutoff));
		return map;
	}

	private Map<String, CachedInvite> toSnapshot(List<net.dv8tion.jda.api.entities.Invite> invites) {
		// Concurrent, because invite create/delete events edit the live snapshot
		Map<String, CachedInvite> map = new ConcurrentHashMap<>();
		for (var invite : invites) {
			CachedInvite cached = toCached(invite);
			map.put(cached.code(), cached);
		}
		return map;
	}

	private CachedInvite toCached(net.dv8tion.jda.api.entities.Invite invite) {
		return new CachedInvite(
			invite.getCode(),
			invite.getUses(),
			invite.getMaxUses(),
			invite.getInviter() == null ? null : invite.getInviter().getIdLong(),
			invite.getChannel() == null ? null : invite.getChannel().getIdLong()
		);
	}

}
