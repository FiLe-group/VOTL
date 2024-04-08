package dev.fileeditor.votl.utils.database.managers;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;

import dev.fileeditor.votl.utils.database.ConnectionUtil;
import dev.fileeditor.votl.utils.database.LiteBase;

public class TempRoleManager extends LiteBase {
	
	public TempRoleManager(ConnectionUtil cu) {
		super(cu, "tempRoles");
	}

	public void add(long guildId, long roleId, long userId, boolean deleteAfter, Instant expiresAt) {
		execute("INSERT INTO %s(guildId, roleId, userId, deleteAfter, expiresAt) VALUES (%s, %s, %s, %d, %d)"
			.formatted(table, guildId, roleId, userId, (deleteAfter ? 1 : 0), expiresAt.getEpochSecond()));
	}

	public void remove(long roleId, long userId) {
		execute("DELETE FROM %s WHERE (roleId=%s AND userId=%s)".formatted(table, roleId, userId));
	}

	public void removeRole(long roleId) {
		execute("DELETE FROM %s WHERE (roleId=%s)".formatted(table, roleId));
	}

	public void removeAll(long guildId) {
		execute("DELETE FROM %s WHERE (guildId=%s)".formatted(table, guildId));
	}

	public void updateTime(long roleId, long userId, Instant expiresAt) {
		execute("UPDATE %s SET expiresAt=%s WHERE (roleId=%s AND userId=%s)".formatted(table, expiresAt.getEpochSecond(), roleId, userId));
	}

	public Instant expireAt(long roleId, long userId) {
		Integer data = selectOne("SELECT expiresAt FROM %s WHERE (roleId=%s AND userId=%s)".formatted(table, roleId, userId), "expiresAt", Integer.class);
		if (data == null) return null;
		return Instant.ofEpochSecond(data);
	}

	public List<Map<String, Object>> expiredRoles() {
		return select("SELECT roleId, userId FROM %s WHERE (expiresAt<=%d)".formatted(table, Instant.now().getEpochSecond()), Set.of("roleId", "userId"));
	}

	public List<Map<String, Object>> getAll(long guildId) {
		return select("SELECT * FROM %s WHERE (guildId=%s)".formatted(table, guildId), Set.of("roleId", "userId", "expireAfter"));
	}

	public boolean shouldDelete(long roleId) {
		Integer data = selectOne("SELECT deleteAfter FROM %s WHERE (roleId=%s)".formatted(table, roleId), "deleteAfter", Integer.class);
		return data==null ? false : data==1;
	}
}
