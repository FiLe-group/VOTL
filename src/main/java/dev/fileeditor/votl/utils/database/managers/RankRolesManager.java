package dev.fileeditor.votl.utils.database.managers;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import dev.fileeditor.votl.objects.constants.Constants;
import dev.fileeditor.votl.utils.database.ConnectionUtil;
import dev.fileeditor.votl.utils.database.LiteBase;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.sql.SQLException;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import static dev.fileeditor.votl.utils.CastUtil.castLong;

public class RankRolesManager extends LiteBase {

	private static final String T_GROUPS = "rankGroups";
	private static final String T_ROLES  = "rankGroupRoles";

	@SuppressWarnings("NullableProblems")
	private final Cache<Long, List<RankGroup>> guildCache = Caffeine.newBuilder()
		.maximumSize(Constants.DEFAULT_CACHE_SIZE)
		.build();

	public RankRolesManager(ConnectionUtil cu) {
		super(cu, null);
	}

	// ---- Group CRUD ----

	public void createGroup(long guildId, String name) throws SQLException {
		invalidate(guildId);
		execute("INSERT INTO \"%s\"(guildId,name) VALUES (%d,%s)".formatted(T_GROUPS, guildId, quote(name)));
	}

	public void deleteGroup(int groupId, long guildId) throws SQLException {
		invalidate(guildId);
		execute("DELETE FROM \"%s\" WHERE groupId=%d".formatted(T_GROUPS, groupId));
	}

	// ---- Membership (ordered, lowest rank first) ----

	public void addRole(int groupId, long guildId, long roleId) throws SQLException {
		invalidate(guildId);
		execute("INSERT OR IGNORE INTO \"%s\"(groupId,roleId) VALUES (%d,%d)".formatted(T_ROLES, groupId, roleId));
	}

	public void removeRole(int groupId, long guildId, long roleId) throws SQLException {
		invalidate(guildId);
		execute("DELETE FROM \"%s\" WHERE groupId=%d AND roleId=%d".formatted(T_ROLES, groupId, roleId));
	}

	public void removeRoleFromAll(long roleId) {
		guildCache.invalidateAll();
		try {
			execute("DELETE FROM \"%s\" WHERE roleId=%d".formatted(T_ROLES, roleId));
		} catch (SQLException ignored) {}
	}

	public void removeGuild(long guildId) {
		invalidate(guildId);
		try {
			execute("DELETE FROM \"%s\" WHERE guildId=%d".formatted(T_GROUPS, guildId));
		} catch (SQLException ignored) {}
	}

	// ---- Lookup ----

	@NotNull
	public List<RankGroup> getGroupsForGuild(long guildId) {
		var result = guildCache.get(guildId, this::loadGroupsForGuild);
		return result == null ? List.of() : result;
	}

	@Nullable
	public RankGroup getGroup(long guildId, String name) {
		return getGroupsForGuild(guildId).stream()
			.filter(g -> g.name().equalsIgnoreCase(name))
			.findFirst().orElse(null);
	}

	public int countGroups(long guildId) {
		return count("SELECT COUNT(*) FROM \"%s\" WHERE guildId=%d".formatted(T_GROUPS, guildId));
	}

	// ---- Internal ----

	/**
	 * Loads every rank group for the guild along with its ordered role ladder in two queries
	 * total (instead of one query per group), so {@code /promote}/{@code /demote} can walk the
	 * ladder purely in memory.
	 */
	private List<RankGroup> loadGroupsForGuild(long guildId) {
		List<Map<String, Object>> rows = select(
			"SELECT groupId,guildId,name FROM \"%s\" WHERE guildId=%d".formatted(T_GROUPS, guildId),
			Set.of("groupId", "guildId", "name")
		);
		if (rows.isEmpty()) return List.of();

		Map<Integer, List<Long>> rolesByGroup = select(
			"SELECT r.groupId AS groupId, r.roleId AS roleId FROM \"%s\" r JOIN \"%s\" g ON r.groupId=g.groupId WHERE g.guildId=%d ORDER BY r.entryId ASC"
				.formatted(T_ROLES, T_GROUPS, guildId),
			Set.of("groupId", "roleId")
		).stream().collect(Collectors.groupingBy(
			row -> ((Number) row.get("groupId")).intValue(),
			Collectors.mapping(row -> castLong(row.get("roleId")), Collectors.toList())
		));

		return rows.stream()
			.map(row -> {
				int groupId = ((Number) row.get("groupId")).intValue();
				return new RankGroup(
					groupId,
					castLong(row.get("guildId")),
					(String) row.get("name"),
					rolesByGroup.getOrDefault(groupId, List.of())
				);
			})
			.collect(Collectors.toList());
	}

	private void invalidate(long guildId) {
		guildCache.invalidate(guildId);
	}

	/** {@code roleIds} is ordered lowest rank first, highest rank last. */
	public record RankGroup(
		int groupId,
		long guildId,
		String name,
		List<Long> roleIds
	) {}
}
