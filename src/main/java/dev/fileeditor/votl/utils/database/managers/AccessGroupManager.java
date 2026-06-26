package dev.fileeditor.votl.utils.database.managers;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import dev.fileeditor.votl.objects.AccessLimits;
import dev.fileeditor.votl.objects.AccessPermission;
import dev.fileeditor.votl.objects.AccessResult;
import dev.fileeditor.votl.objects.constants.Constants;
import dev.fileeditor.votl.utils.database.ConnectionUtil;
import dev.fileeditor.votl.utils.database.LiteBase;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.sql.SQLException;
import java.time.Duration;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import static dev.fileeditor.votl.utils.CastUtil.castLong;

public class AccessGroupManager extends LiteBase {

	private static final String T_GROUPS = "accessGroups";
	private static final String T_ROLES  = "accessGroupRoles";
	private static final String T_USERS  = "accessGroupUsers";

	@SuppressWarnings("NullableProblems")
	private final Cache<Long, List<GroupData>> guildCache = Caffeine.newBuilder()
		.maximumSize(Constants.DEFAULT_CACHE_SIZE)
		.build();

	public AccessGroupManager(ConnectionUtil cu) {
		super(cu, null);
	}

	// ---- Group CRUD ----

	public void createGroup(long guildId, String name) throws SQLException {
		invalidate(guildId);
		execute("INSERT INTO \"%s\"(guildId,name,permissions) VALUES (%d,%s,0)"
			.formatted(T_GROUPS, guildId, quote(name)));
	}

	public void deleteGroup(int groupId, long guildId) throws SQLException {
		invalidate(guildId);
		execute("DELETE FROM \"%s\" WHERE groupId=%d".formatted(T_GROUPS, groupId));
	}

	public void renameGroup(int groupId, long guildId, String newName) throws SQLException {
		invalidate(guildId);
		execute("UPDATE \"%s\" SET name=%s WHERE groupId=%d".formatted(T_GROUPS, quote(newName), groupId));
	}

	public void setPermissions(int groupId, long guildId, long permBitmask) throws SQLException {
		invalidate(guildId);
		execute("UPDATE \"%s\" SET permissions=%d WHERE groupId=%d".formatted(T_GROUPS, permBitmask, groupId));
	}

	public void setMaxBanDuration(int groupId, long guildId, @Nullable Long seconds) throws SQLException {
		invalidate(guildId);
		execute("UPDATE \"%s\" SET maxBanDuration=%s WHERE groupId=%d"
			.formatted(T_GROUPS, seconds == null ? "NULL" : seconds, groupId));
	}

	public void setMaxMuteDuration(int groupId, long guildId, @Nullable Long seconds) throws SQLException {
		invalidate(guildId);
		execute("UPDATE \"%s\" SET maxMuteDuration=%s WHERE groupId=%d"
			.formatted(T_GROUPS, seconds == null ? "NULL" : seconds, groupId));
	}

	// ---- Membership ----

	public void addRole(int groupId, long guildId, long roleId) throws SQLException {
		invalidate(guildId);
		execute("INSERT OR IGNORE INTO \"%s\"(groupId,roleId) VALUES (%d,%d)".formatted(T_ROLES, groupId, roleId));
	}

	public void removeRole(int groupId, long guildId, long roleId) throws SQLException {
		invalidate(guildId);
		execute("DELETE FROM \"%s\" WHERE groupId=%d AND roleId=%d".formatted(T_ROLES, groupId, roleId));
	}

	public void addUser(int groupId, long guildId, long userId) throws SQLException {
		invalidate(guildId);
		execute("INSERT OR IGNORE INTO \"%s\"(groupId,guildId,userId) VALUES (%d,%d,%d)".formatted(T_USERS, groupId, guildId, userId));
	}

	public void removeUser(int groupId, long guildId, long userId) throws SQLException {
		invalidate(guildId);
		execute("DELETE FROM \"%s\" WHERE groupId=%d AND userId=%d".formatted(T_USERS, groupId, userId));
	}

	public void removeRoleFromAll(long roleId) {
		guildCache.invalidateAll();
		try {
			execute("DELETE FROM \"%s\" WHERE roleId=%d".formatted(T_ROLES, roleId));
		} catch (SQLException ignored) {}
	}

	public void removeUserFromAll(long userId) {
		guildCache.invalidateAll();
		try {
			execute("DELETE FROM \"%s\" WHERE userId=%d".formatted(T_USERS, userId));
		} catch (SQLException ignored) {}
	}

	public void removeUserFromGuild(long guildId, long userId) {
		invalidate(guildId);
		try {
			execute("DELETE FROM \"%s\" WHERE groupId IN (SELECT groupId FROM \"%s\" WHERE guildId=%d) AND userId=%d"
				.formatted(T_USERS, T_GROUPS, guildId, userId));
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
	public AccessResult resolveForMember(long guildId, long userId, List<Long> roleIds) {
		List<GroupData> groups = getGroupsForGuild(guildId);
		if (groups.isEmpty()) return AccessResult.EMPTY;

		return groups.stream()
			.filter(g -> matchesMember(g, userId, roleIds))
			.map(this::toResult)
			.reduce(AccessResult.EMPTY, AccessResult::merge);
	}

	@NotNull
	public List<GroupData> getGroupsForGuild(long guildId) {
		var result = guildCache.get(guildId, this::loadGroupsForGuild);
		return result == null ? List.of() : result;
	}

	@Nullable
	public GroupData getGroup(long guildId, String name) {
		return getGroupsForGuild(guildId).stream()
			.filter(g -> g.name().equalsIgnoreCase(name))
			.findFirst().orElse(null);
	}

	@Nullable
	public GroupData getGroupById(int groupId) {
		Map<String, Object> row = selectOne(
			"SELECT groupId,guildId,name,permissions,maxBanDuration,maxMuteDuration FROM \"%s\" WHERE groupId=%d"
				.formatted(T_GROUPS, groupId),
			Set.of("groupId", "guildId", "name", "permissions", "maxBanDuration", "maxMuteDuration")
		);
		return row == null ? null : parseGroup(row);
	}

	public int countGroups(long guildId) {
		return count("SELECT COUNT(*) FROM \"%s\" WHERE guildId=%d".formatted(T_GROUPS, guildId));
	}

	public List<Long> getRolesForGroup(int groupId) {
		return select("SELECT roleId FROM \"%s\" WHERE groupId=%d".formatted(T_ROLES, groupId), "roleId", Long.class);
	}

	public List<Long> getUsersForGroup(int groupId) {
		return select("SELECT userId FROM \"%s\" WHERE groupId=%d".formatted(T_USERS, groupId), "userId", Long.class);
	}

	public boolean isRoleInGroup(int groupId, long roleId) {
		return selectOne("SELECT roleId FROM \"%s\" WHERE groupId=%d AND roleId=%d"
			.formatted(T_ROLES, groupId, roleId), "roleId", Long.class) != null;
	}

	/** Returns all role IDs that belong to any group with the given permission flag set. */
	@NotNull
	public List<Long> getRolesWithPermission(long guildId, AccessPermission perm) {
		return getGroupsForGuild(guildId).stream()
			.filter(g -> (g.permissions() & perm.toBit()) != 0L)
			.flatMap(g -> getRolesForGroup(g.groupId()).stream())
			.distinct()
			.collect(Collectors.toList());
	}

	public boolean isUserInGroup(int groupId, long userId) {
		return selectOne("SELECT userId FROM \"%s\" WHERE groupId=%d AND userId=%d"
			.formatted(T_USERS, groupId, userId), "userId", Long.class) != null;
	}

	// ---- Internal ----

	private boolean matchesMember(GroupData g, long userId, List<Long> roleIds) {
		if (isUserInGroup(g.groupId(), userId)) return true;
		for (long roleId : roleIds) {
			if (isRoleInGroup(g.groupId(), roleId)) return true;
		}
		return false;
	}

	private AccessResult toResult(GroupData g) {
		EnumSet<AccessPermission> perms = EnumSet.noneOf(AccessPermission.class);
		for (AccessPermission p : AccessPermission.values()) {
			if ((g.permissions() & p.toBit()) != 0L) perms.add(p);
		}
		AccessLimits limits = new AccessLimits(
			g.maxBanSeconds() == null ? null : Duration.ofSeconds(g.maxBanSeconds()),
			g.maxMuteSeconds() == null ? null : Duration.ofSeconds(g.maxMuteSeconds())
		);
		return new AccessResult(perms, limits);
	}

	private List<GroupData> loadGroupsForGuild(long guildId) {
		return select(
			"SELECT groupId,guildId,name,permissions,maxBanDuration,maxMuteDuration FROM \"%s\" WHERE guildId=%d"
				.formatted(T_GROUPS, guildId),
			Set.of("groupId", "guildId", "name", "permissions", "maxBanDuration", "maxMuteDuration")
		).stream().map(this::parseGroup).collect(Collectors.toList());
	}

	private GroupData parseGroup(Map<String, Object> row) {
		Object rawBan  = row.get("maxBanDuration");
		Object rawMute = row.get("maxMuteDuration");
		return new GroupData(
			((Number) row.get("groupId")).intValue(),
			castLong(row.get("guildId")),
			(String) row.get("name"),
			castLong(row.get("permissions")),
			rawBan  == null ? null : ((Number) rawBan).longValue(),
			rawMute == null ? null : ((Number) rawMute).longValue()
		);
	}

	private void invalidate(long guildId) {
		guildCache.invalidate(guildId);
	}

	public record GroupData(
		int groupId,
		long guildId,
		String name,
		long permissions,
		@Nullable Long maxBanSeconds,
		@Nullable Long maxMuteSeconds
	) {}
}
