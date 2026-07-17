package dev.fileeditor.votl.utils.database.managers;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import dev.fileeditor.votl.objects.constants.Constants;
import dev.fileeditor.votl.utils.CastUtil;
import dev.fileeditor.votl.utils.database.ConnectionUtil;
import dev.fileeditor.votl.utils.database.LiteBase;
import org.jetbrains.annotations.NotNull;

import java.sql.SQLException;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

public class AutoRoleManager extends LiteBase {
	// cache
	@SuppressWarnings("NullableProblems")
	private final Cache<Long, List<AutoRoleData>> cache = Caffeine.newBuilder()
		.maximumSize(Constants.DEFAULT_CACHE_SIZE)
		.build();

	public AutoRoleManager(ConnectionUtil cu) {
		super(cu, "autoRole");
	}

	public void add(long guildId, long triggerRoleId, long targetRoleId) throws SQLException {
		invalidateCache(guildId);
		execute("INSERT INTO %s(guildId, triggerRoleId, targetRoleId) VALUES (%d, %d, %d)"
			.formatted(table, guildId, triggerRoleId, targetRoleId));
	}

	public void remove(long guildId, long triggerRoleId, long targetRoleId) throws SQLException {
		invalidateCache(guildId);
		execute("DELETE FROM %s WHERE (guildId=%d AND triggerRoleId=%d AND targetRoleId=%d)"
			.formatted(table, guildId, triggerRoleId, targetRoleId));
	}

	public void removeGuild(long guildId) throws SQLException {
		invalidateCache(guildId);
		execute("DELETE FROM %s WHERE (guildId=%d)".formatted(table, guildId));
	}

	public void removeRole(long guildId, long roleId) throws SQLException {
		invalidateCache(guildId);
		execute("DELETE FROM %s WHERE (guildId=%d AND (triggerRoleId=%d OR targetRoleId=%d))"
			.formatted(table, guildId, roleId, roleId));
	}

	public boolean exists(long guildId, long triggerRoleId, long targetRoleId) {
		return getPairs(guildId).stream()
			.anyMatch(data -> data.getTriggerRoleId() == triggerRoleId && data.getTargetRoleId() == targetRoleId);
	}

	@NotNull
	public List<AutoRoleData> getPairs(long guildId) {
		return cache.get(guildId, this::getData);
	}

	@NotNull
	public Set<Long> getTargets(long guildId, long triggerRoleId) {
		return getPairs(guildId).stream()
			.filter(data -> data.getTriggerRoleId() == triggerRoleId)
			.map(AutoRoleData::getTargetRoleId)
			.collect(Collectors.toSet());
	}

	@NotNull
	public Set<Long> getTriggersFor(long guildId, long targetRoleId) {
		return getPairs(guildId).stream()
			.filter(data -> data.getTargetRoleId() == targetRoleId)
			.map(AutoRoleData::getTriggerRoleId)
			.collect(Collectors.toSet());
	}

	public int countPairs(long guildId) {
		return getPairs(guildId).size();
	}

	private List<AutoRoleData> getData(long guildId) {
		List<Map<String, Object>> data = select("SELECT * FROM %s WHERE (guildId=%d)".formatted(table, guildId),
			Set.of("triggerRoleId", "targetRoleId"));
		return data.stream().map(AutoRoleData::new).toList();
	}

	private void invalidateCache(long guildId) {
		cache.invalidate(guildId);
	}

	public static class AutoRoleData {
		private final long triggerRoleId;
		private final long targetRoleId;

		public AutoRoleData(Map<String, Object> map) {
			this.triggerRoleId = CastUtil.castLong(map.get("triggerRoleId"));
			this.targetRoleId = CastUtil.castLong(map.get("targetRoleId"));
		}

		public long getTriggerRoleId() {
			return triggerRoleId;
		}

		public long getTargetRoleId() {
			return targetRoleId;
		}
	}
}
