package dev.fileeditor.votl.utils.database.managers;

import dev.fileeditor.votl.utils.database.ConnectionUtil;
import dev.fileeditor.votl.utils.database.LiteBase;

import java.time.Instant;

public class ModifyRoleManager extends LiteBase {

	public ModifyRoleManager(ConnectionUtil cu) {
		super(cu, "menuSelectRoles");
	}

	public boolean create(long guildId, long userId, long targetId, Instant expiresAfter) {
		return execute("INSERT INTO %s(guildId, userId, targetId, expiresAfter, roles) VALUES (%s, %s, %s, %s, \":::\") ON CONFLICT(guildId, userId, targetId) DO UPDATE SET expiresAfter = %<s, roles = \":::\""
			.formatted(table, guildId, userId, targetId, expiresAfter.getEpochSecond()));
	}

	public void update(long guildId, long userId, long targetId, String newRoles, Instant expiresAfter) {
		execute("UPDATE %S SET expiresAfter=%s, roles=%s WHERE (guildId=%s AND userId=%s AND targetId=%s)"
			.formatted(table, expiresAfter.getEpochSecond(), quote(newRoles), guildId, userId, targetId));
	}

	public void remove(long guildId, long userId, long targetId) {
		execute("DELETE FROM %s WHERE (guildId=%s AND userId=%s AND targetId=%s)".formatted(table, guildId, userId, targetId));
	}

	public void removeAll(long guildId) {
		execute("DELETE FROM %s WHERE (guildId=%s)".formatted(table, guildId));
	}

	public void removeExpired() {
		execute("DELETE FROM %s WHERE (expiresAfter=%s)".formatted(table, Instant.now().getEpochSecond()));
	}

	public String getRoles(long guildId, long userId, long targetId) {
		return selectOne("SELECT roles FROM %s WHERE (guildId=%s AND userId=%s AND targetId=%s)".formatted(table, guildId, userId, targetId), "roles", String.class);
	}

	public Boolean isExpired(long guildId, long userId, long targetId) {
		Long data = selectOne("SELECT expiresAfter FROM %s WHERE (guildId=%s AND userId=%s AND targetId=%s)".formatted(table, guildId, userId, targetId), "expiresAfter", Long.class);
		if (data == null) return true;
		boolean expired = Instant.ofEpochSecond(data).isBefore(Instant.now());
		if (expired) remove(guildId, userId, targetId);
		return expired;
	}

}
