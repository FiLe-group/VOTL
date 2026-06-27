package dev.fileeditor.votl.utils.database.managers;

import dev.fileeditor.votl.utils.database.ConnectionUtil;
import dev.fileeditor.votl.utils.database.LiteBase;

import org.jetbrains.annotations.Nullable;

import java.sql.SQLException;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class CustomRolesManager extends LiteBase {

	private static final Set<String> FIELDS = Set.of("roleId", "ownerId", "guildId", "allowedModify", "expires", "isNitro", "renewAt");

	public CustomRolesManager(ConnectionUtil cu) {
		super(cu, "customRoles");
	}

	public void add(long roleId, long ownerId, long guildId, boolean allowedModify) throws SQLException {
		execute("INSERT INTO %s(roleId, ownerId, guildId, allowedModify) VALUES (%d, %d, %d, %d)"
			.formatted(table, roleId, ownerId, guildId, allowedModify ? 1 : 0));
	}

	public void add(long roleId, long ownerId, long guildId, boolean allowedModify, long expiresAt, boolean isNitro, long renewAt) throws SQLException {
		execute("INSERT INTO %s(roleId, ownerId, guildId, allowedModify, expires, isNitro, renewAt) VALUES (%d, %d, %d, %d, %d, %d, %d)"
			.formatted(table, roleId, ownerId, guildId, allowedModify ? 1 : 0, expiresAt, isNitro ? 1 : 0, renewAt));
	}

	@Nullable
	public Long getByOwner(long userId, long guildId) {
		return selectOne("SELECT roleId FROM %s WHERE (ownerId=%d AND guildId=%d)".formatted(table, userId, guildId), "roleId", Long.class);
	}

	public List<Map<String, Object>> getAll(long guildId) {
		return select("SELECT * FROM %s WHERE (guildId=%d)".formatted(table, guildId), FIELDS);
	}

	public void remove(long roleId) throws SQLException {
		execute("DELETE FROM %s WHERE (roleId=%d)".formatted(table, roleId));
	}

	public void setExpires(long roleId, long expiresAt) throws SQLException {
		execute("UPDATE %s SET expires=%s WHERE (roleId=%d)".formatted(table, expiresAt, roleId));
	}

	public void setRenewAt(long roleId, long renewAt) throws SQLException {
		execute("UPDATE %s SET renewAt=%s WHERE (roleId=%d)".formatted(table, renewAt, roleId));
	}

	public List<Map<String, Object>> getExpired() {
		return select("SELECT roleId, ownerId, guildId FROM %s WHERE (expires>0 AND expires<=%d)".formatted(table, Instant.now().getEpochSecond()),
			Set.of("roleId", "ownerId", "guildId"));
	}

	public List<Map<String, Object>> getNitroForRenewal() {
		return select("SELECT roleId, ownerId, guildId FROM %s WHERE (isNitro=1 AND renewAt>0 AND renewAt<=%d)".formatted(table, Instant.now().getEpochSecond()),
			Set.of("roleId", "ownerId", "guildId"));
	}

	public void removeGuild(long guildId) throws SQLException {
		execute("DELETE FROM %s WHERE (guildId=%d)".formatted(table, guildId));
	}

}
