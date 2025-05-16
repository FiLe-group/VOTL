package dev.fileeditor.votl.blacklist;

import dev.fileeditor.votl.App;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.User;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.time.Instant;
import java.util.*;

public class Blacklist {

	private final App bot;
	private final Map<Long, BlacklistEntity> blacklist;
	private final Ratelimit ratelimit;

	public Blacklist(App bot) {
		this.bot = bot;

		this.blacklist = new HashMap<>();
		this.ratelimit = new Ratelimit(this);
	}

	public Ratelimit getRatelimit() {
		return ratelimit;
	}

	public boolean isBlacklisted(@NotNull User user) {
		BlacklistEntity entity = getEntity(user.getIdLong());
		return entity != null && entity.isBlacklisted();
	}

	public boolean isBlacklisted(@NotNull Guild guild) {
		BlacklistEntity entity = getEntity(guild.getIdLong());
		return entity != null && entity.isBlacklisted();
	}

	public void addUser(@NotNull User user, @Nullable String reason) {
		addToBlacklist(Scope.USER, user.getIdLong(), reason);
	}

	public void addGuild(@NotNull Guild guild, @Nullable String reason) {
		addToBlacklist(Scope.GUILD, guild.getIdLong(), reason);
	}

	public void remove(long id) {
		blacklist.remove(id);

		// TODO database
	}

	@Nullable
	public BlacklistEntity getEntity(long id) {
		return blacklist.get(id);
	}

	public void addToBlacklist(Scope scope, long id, @Nullable String reason) {
		addToBlacklist(scope, id, reason, null);
	}

	public void addToBlacklist(Scope scope, long id, @Nullable String reason, @Nullable Instant expiresIn) {
		BlacklistEntity entity = getEntity(id);
		if (entity != null) {
			remove(id);
		}

		blacklist.put(id, new BlacklistEntity(scope, id, reason, expiresIn));

		// TODO sync with database
	}

	public Map<Long, BlacklistEntity> getBlacklistEntities() {
		return Collections.unmodifiableMap(blacklist);
	}

	// TODO sync with database
}
