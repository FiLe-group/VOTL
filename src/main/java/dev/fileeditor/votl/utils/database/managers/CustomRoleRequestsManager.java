package dev.fileeditor.votl.utils.database.managers;

import static dev.fileeditor.votl.utils.CastUtil.castLong;
import static dev.fileeditor.votl.utils.CastUtil.getOrDefault;

import dev.fileeditor.votl.utils.database.ConnectionUtil;
import dev.fileeditor.votl.utils.database.LiteBase;
import org.jetbrains.annotations.Nullable;

import java.sql.SQLException;
import java.time.Instant;
import java.util.Map;
import java.util.Set;

public class CustomRoleRequestsManager extends LiteBase {

	private static final Set<String> FIELDS = Set.of(
		"requestId", "guildId", "userId", "roleName",
		"color1", "color2", "colorNotes", "iconUrl",
		"status", "reviewerId", "rejectReason", "createdAt", "messageId"
	);

	public CustomRoleRequestsManager(ConnectionUtil cu) {
		super(cu, "customRoleRequests");
	}

	public long create(long guildId, long userId, String roleName,
	                   @Nullable String color1, @Nullable String color2,
	                   @Nullable String colorNotes, @Nullable String iconUrl) throws SQLException {
		return executeWithRow(
			"INSERT INTO %s(guildId, userId, roleName, color1, color2, colorNotes, iconUrl, createdAt) VALUES (%d, %d, %s, %s, %s, %s, %s, %d)"
				.formatted(table, guildId, userId,
					quote(roleName), quote(color1), quote(color2), quote(colorNotes), quote(iconUrl),
					Instant.now().getEpochSecond())
		);
	}

	@Nullable
	public CustomRoleRequest getById(long requestId) {
		return applyOrDefault(
			selectOne("SELECT * FROM %s WHERE (requestId=%d)".formatted(table, requestId), FIELDS),
			CustomRoleRequest::new,
			null
		);
	}

	@Nullable
	public CustomRoleRequest getPendingByUser(long userId, long guildId) {
		return applyOrDefault(
			selectOne("SELECT * FROM %s WHERE (userId=%d AND guildId=%d AND status=0)".formatted(table, userId, guildId), FIELDS),
			CustomRoleRequest::new,
			null
		);
	}

	public void setMessageId(long requestId, long messageId) throws SQLException {
		execute("UPDATE %s SET messageId=%d WHERE (requestId=%d)".formatted(table, messageId, requestId));
	}

	public void approve(long requestId, long reviewerId) throws SQLException {
		execute("UPDATE %s SET status=1, reviewerId=%d WHERE (requestId=%d)".formatted(table, reviewerId, requestId));
	}

	public void reject(long requestId, long reviewerId, String reason) throws SQLException {
		execute("UPDATE %s SET status=2, reviewerId=%d, rejectReason=%s WHERE (requestId=%d)"
			.formatted(table, reviewerId, quote(reason), requestId));
	}

	public void updateDetails(long requestId, String roleName,
	                          @Nullable String color1, @Nullable String color2,
	                          @Nullable String iconUrl) throws SQLException {
		execute("UPDATE %s SET roleName=%s, color1=%s, color2=%s, iconUrl=%s WHERE (requestId=%d)"
			.formatted(table, quote(roleName), quote(color1), quote(color2), quote(iconUrl), requestId));
	}

	public void removeGuild(long guildId) throws SQLException {
		execute("DELETE FROM %s WHERE (guildId=%d)".formatted(table, guildId));
	}

	public static class CustomRoleRequest {
		public final long requestId;
		public final long guildId;
		public final long userId;
		public final String roleName;
		@Nullable public final String color1;
		@Nullable public final String color2;
		@Nullable public final String colorNotes;
		@Nullable public final String iconUrl;
		public final int status;
		@Nullable public final Long reviewerId;
		@Nullable public final String rejectReason;
		public final long createdAt;
		@Nullable public final Long messageId;

		public CustomRoleRequest(Map<String, Object> data) {
			this.requestId = castLong(data.get("requestId"));
			this.guildId = castLong(data.get("guildId"));
			this.userId = castLong(data.get("userId"));
			this.roleName = getOrDefault(data.get("roleName"), "");
			this.color1 = getOrDefault(data.get("color1"), null);
			this.color2 = getOrDefault(data.get("color2"), null);
			this.colorNotes = getOrDefault(data.get("colorNotes"), null);
			this.iconUrl = getOrDefault(data.get("iconUrl"), null);
			this.status = getOrDefault(data.get("status"), 0);
			this.reviewerId = castLong(data.get("reviewerId"));
			this.rejectReason = getOrDefault(data.get("rejectReason"), null);
			this.createdAt = castLong(data.get("createdAt"));
			this.messageId = castLong(data.get("messageId"));
		}

		@SuppressWarnings("BooleanMethodIsAlwaysInverted")
		public boolean isPending()  { return status == 0; }
		public boolean isApproved() { return status == 1; }
		public boolean isRejected() { return status == 2; }
	}

}
