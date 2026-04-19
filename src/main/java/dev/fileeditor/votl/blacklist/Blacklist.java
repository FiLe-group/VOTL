package dev.fileeditor.votl.blacklist;

import dev.fileeditor.votl.App;
import dev.fileeditor.votl.utils.CastUtil;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.entities.UserSnowflake;
import net.dv8tion.jda.api.events.interaction.GenericInteractionCreateEvent;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.sql.SQLException;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.*;

import static dev.fileeditor.votl.utils.CastUtil.*;

public class Blacklist {

	private final App bot;
	private final Map<Long, BlacklistEntity> blacklistMap;
	private final Ratelimit ratelimit;

	public Blacklist(App bot) {
		this.bot = bot;

		this.blacklistMap = new HashMap<>();
		this.ratelimit = new Ratelimit(this);
	}

	public Ratelimit getRatelimit() {
		return ratelimit;
	}

	public boolean isBlacklisted(GenericInteractionCreateEvent event) {
		return isBlacklisted(event.getUser()) || (event.getGuild()!=null && isBlacklisted(event.getGuild()));
	}

	public boolean isBlacklisted(@NotNull UserSnowflake user) {
		BlacklistEntity entity = getEntity(user.getIdLong());
		return entity != null && entity.isBlacklisted();
	}

	public boolean isBlacklisted(@NotNull Guild guild) {
		BlacklistEntity entity = getEntity(guild.getIdLong());
		return entity != null && entity.isBlacklisted();
	}

	public boolean hasDnt(@NotNull UserSnowflake user) {
		BlacklistEntity entity = getEntity(user.getIdLong());
		return entity != null && entity.isDnt();
	}

	public void addUser(@NotNull User user, @Nullable String reason) {
		addToBlacklist(Scope.USER, user.getIdLong(), reason);
	}

	public void addGuild(@NotNull Guild guild, @Nullable String reason) {
		addToBlacklist(Scope.GUILD, guild.getIdLong(), reason);
	}

	public void remove(long id) {
		blacklistMap.remove(id);

		try {
			bot.getDBUtil().blacklist.remove(id);
		} catch (SQLException e) {
			App.getLogger().error("Failed to sync blacklist with the database: {}", e.getMessage(), e);
		}
	}

	@Nullable
	public BlacklistEntity getEntity(long id) {
		return blacklistMap.get(id);
	}

	public void addToBlacklist(Scope scope, long id, @Nullable String reason) {
		addToBlacklist(scope, id, reason, false);
	}

	public void addToBlacklist(Scope scope, long id, @Nullable String reason, @Nullable OffsetDateTime expiresIn) {
		BlacklistEntity entity = getEntity(id);
		if (entity != null) {
			remove(id);
		}

		blacklistMap.put(id, new BlacklistEntity(scope, id, reason, expiresIn, false));

		try {
			bot.getDBUtil().blacklist.add(id, scope, expiresIn==null ? OffsetDateTime.now().plusYears(30) : expiresIn, reason, false);
		} catch (SQLException e) {
			App.getLogger().error("Failed to sync blacklist with the database: {}", e.getMessage(), e);
		}
	}

	public void addToBlacklist(Scope scope, long id, @Nullable String reason, boolean dnt) {
		if (scope.equals(Scope.GUILD)) {
			throw new IllegalArgumentException("Cannot add to blacklist with DNT when scope is GUILD");
		}
		BlacklistEntity entity = getEntity(id);
		if (entity != null) {
			remove(id);
		}

		blacklistMap.put(id, new BlacklistEntity(scope, id, reason, null, dnt));

		// Process data for removal
		try {
			bot.getDBUtil().access.removeUser(id);
			bot.getDBUtil().levels.deleteUser(id);
			bot.getDBUtil().user.remove(id);
		} catch (SQLException e) {
			App.getLogger().error("Failed to process data deletion for user: {}", id);
		}

		try {
			bot.getDBUtil().blacklist.add(id, scope, OffsetDateTime.now().plusYears(30), reason, dnt);
		} catch (SQLException e) {
			App.getLogger().error("Failed to sync blacklist with the database: {}", e.getMessage(), e);
		}
	}

	/**
	 * Get blacklist entries.
	 * @return modifiable map of blacklist entries.
	 */
	public Map<Long, BlacklistEntity> getBlacklistEntities() {
		return blacklistMap;
	}

	public synchronized void syncBlacklistWithDatabase() {
		blacklistMap.clear();
		try {
			for (var map : bot.getDBUtil().blacklist.load()) {
				long id = requireNonNull(map.get("id"));
				Scope scope = Scope.fromId(getOrDefault(map.get("type"), 0));
				String reason = getOrDefault(map.get("reason"), null);
				OffsetDateTime expiresIn = resolveOrDefault(map.get("expiresIn"), o -> Instant.ofEpochSecond(castLong(o)).atOffset(ZoneOffset.UTC), null);
				if (expiresIn != null && expiresIn.isBefore(OffsetDateTime.now().plusYears(1))) {
					expiresIn = null;
				}
				boolean dnt = CastUtil.getOrDefault(map.get("dnt"), 0) == 1;

				blacklistMap.put(id, new BlacklistEntity(scope, id, reason, expiresIn, dnt));
			}
		} catch (Exception e) {
			App.getLogger().error("Failed to sync blacklist with the database: {}", e.getMessage(), e);
		}
	}
}
