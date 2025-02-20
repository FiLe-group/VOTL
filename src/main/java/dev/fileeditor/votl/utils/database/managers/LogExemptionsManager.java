package dev.fileeditor.votl.utils.database.managers;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import dev.fileeditor.votl.objects.constants.Constants;
import dev.fileeditor.votl.utils.FixedCache;
import dev.fileeditor.votl.utils.database.ConnectionUtil;
import dev.fileeditor.votl.utils.database.LiteBase;

public class LogExemptionsManager extends LiteBase {

	// Cache
	private final FixedCache<Long, Set<Long>> cache = new FixedCache<>(Constants.DEFAULT_CACHE_SIZE);
	
	public LogExemptionsManager(ConnectionUtil cu) {
		super(cu, "logExceptions");
	}

	public boolean addExemption(long guildId, long targetId) {
		invalidateCache(guildId);
		return execute("INSERT INTO %s(guildId, targetId) VALUES (%s, %s)".formatted(table, guildId, targetId));
	}

	public boolean removeExemption(long guildId, long targetId) {
		invalidateCache(guildId);
		return execute("DELETE FROM %s WHERE (guildId=%s AND targetId=%s)".formatted(table, guildId, targetId));
	}

	public void removeGuild(long guildId) {
		invalidateCache(guildId);
		execute("DELETE FROM %s WHERE (guildId=%s)".formatted(table, guildId));
	}

	public boolean isExemption(long guildId, long targetId) {
		return getExemptions(guildId).contains(targetId);
	}

	public Set<Long> getExemptions(long guildId) {
		if (cache.contains(guildId))
			return cache.get(guildId);
		List<Long> data = select("SELECT * FROM %s WHERE (guildId=%d)".formatted(table, guildId), "targetId", Long.class);
		Set<Long> dataSet = data.isEmpty() ? Set.of() : new HashSet<>(data);
		cache.put(guildId, dataSet);
		return dataSet;
	}

	private void invalidateCache(long guildId) {
		cache.pull(guildId);
	}

}
