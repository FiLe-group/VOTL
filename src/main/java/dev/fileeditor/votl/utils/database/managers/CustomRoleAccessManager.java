package dev.fileeditor.votl.utils.database.managers;

import static dev.fileeditor.votl.utils.CastUtil.castLong;
import static dev.fileeditor.votl.utils.CastUtil.getOrDefault;

import dev.fileeditor.votl.utils.database.ConnectionUtil;
import dev.fileeditor.votl.utils.database.LiteBase;
import org.jetbrains.annotations.Nullable;

import java.sql.SQLException;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class CustomRoleAccessManager extends LiteBase {

	private static final Set<String> FIELDS = Set.of("userId", "guildId", "grantedBy", "expiresAt", "isNitro");

	public CustomRoleAccessManager(ConnectionUtil cu) {
		super(cu, "customRoleAccess");
	}

	/** Upsert — safe to call repeatedly (e.g. nitro re-grant). */
	public void grant(long userId, long guildId, long grantedBy, long expiresAt, boolean isNitro) throws SQLException {
		execute("INSERT INTO %s(userId, guildId, grantedBy, expiresAt, isNitro) VALUES (%d, %d, %d, %d, %d) ON CONFLICT(userId, guildId) DO UPDATE SET grantedBy=%4$d, expiresAt=%5$d, isNitro=%6$d"
			.formatted(table, userId, guildId, grantedBy, expiresAt, isNitro ? 1 : 0));
	}

	public void revoke(long userId, long guildId) throws SQLException {
		execute("DELETE FROM %s WHERE (userId=%d AND guildId=%d)".formatted(table, userId, guildId));
	}

	/** Returns true if the user has a non-expired access record. */
	public boolean hasAccess(long userId, long guildId) {
		Long expiresAt = selectOne(
			"SELECT expiresAt FROM %s WHERE (userId=%d AND guildId=%d)".formatted(table, userId, guildId),
			"expiresAt", Long.class
		);
		if (expiresAt == null) return false;
		return expiresAt == 0 || expiresAt > Instant.now().getEpochSecond();
	}

	@Nullable
	public AccessRecord getAccess(long userId, long guildId) {
		return applyOrDefault(
			selectOne("SELECT * FROM %s WHERE (userId=%d AND guildId=%d)".formatted(table, userId, guildId), FIELDS),
			AccessRecord::new,
			null
		);
	}

	public List<AccessRecord> getGuildAccess(long guildId) {
		return select("SELECT * FROM %s WHERE (guildId=%d)".formatted(table, guildId), FIELDS)
			.stream().map(AccessRecord::new).toList();
	}

	public List<AccessRecord> getActiveNitroAccess() {
		return select("SELECT * FROM %s WHERE (isNitro=1)".formatted(table), FIELDS)
			.stream().map(AccessRecord::new).toList();
	}

	public void updateExpiry(long userId, long guildId, long expiresAt) throws SQLException {
		execute("UPDATE %s SET expiresAt=%d WHERE (userId=%d AND guildId=%d)".formatted(table, expiresAt, userId, guildId));
	}

	public void removeGuild(long guildId) throws SQLException {
		execute("DELETE FROM %s WHERE (guildId=%d)".formatted(table, guildId));
	}

	public static class AccessRecord {
		public final long userId;
		public final long guildId;
		public final long grantedBy;
		public final long expiresAt;
		public final boolean isNitro;

		public AccessRecord(Map<String, Object> data) {
			this.userId = castLong(data.get("userId"));
			this.guildId = castLong(data.get("guildId"));
			this.grantedBy = castLong(data.get("grantedBy"));
			this.expiresAt = getOrDefault(data.get("expiresAt"), 0L);
			this.isNitro = getOrDefault(data.get("isNitro"), 0) == 1;
		}

		public boolean isExpired() {
			return expiresAt != 0 && expiresAt <= Instant.now().getEpochSecond();
		}

		/** expiresAt == 0 means permanent. */
		public boolean isPermanent() {
			return expiresAt == 0;
		}
	}

}
