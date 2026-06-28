package dev.fileeditor.votl.utils.database.managers;

import static dev.fileeditor.votl.utils.CastUtil.getOrDefault;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import dev.fileeditor.votl.objects.constants.Constants;
import dev.fileeditor.votl.utils.database.ConnectionUtil;
import dev.fileeditor.votl.utils.database.LiteBase;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.sql.SQLException;
import java.util.Map;
import java.util.Set;

public class CustomRoleSettingsManager extends LiteBase {

	private static final Set<String> COLUMNS = Set.of(
		"requestsChannelId", "reviewerRoleId", "positionRoleId",
		"nitroAutoGrant", "nitroExpireDays", "nitroRenewDays"
	);

	private final Cache<Long, CustomRoleSettings> cache = Caffeine.newBuilder()
		.maximumSize(Constants.DEFAULT_CACHE_SIZE)
		.build();
	private final CustomRoleSettings blankSettings = new CustomRoleSettings();

	public CustomRoleSettingsManager(ConnectionUtil cu) {
		super(cu, "customRoleSettings");
	}

	@NotNull
	public CustomRoleSettings getSettings(long guildId) {
		return cache.get(guildId, id -> applyOrDefault(
			selectOne("SELECT * FROM %s WHERE (guildId=%d)".formatted(table, id), COLUMNS),
			CustomRoleSettings::new,
			blankSettings
		));
	}

	public void setRequestsChannel(long guildId, @Nullable Long channelId) throws SQLException {
		invalidate(guildId);
		execute("INSERT INTO %s(guildId, requestsChannelId) VALUES (%d, %s) ON CONFLICT(guildId) DO UPDATE SET requestsChannelId=%<s"
			.formatted(table, guildId, channelId == null ? "NULL" : channelId));
	}

	public void setReviewerRole(long guildId, @Nullable Long roleId) throws SQLException {
		invalidate(guildId);
		execute("INSERT INTO %s(guildId, reviewerRoleId) VALUES (%d, %s) ON CONFLICT(guildId) DO UPDATE SET reviewerRoleId=%<s"
			.formatted(table, guildId, roleId == null ? "NULL" : roleId));
	}

	public void setPositionRole(long guildId, @Nullable Long roleId) throws SQLException {
		invalidate(guildId);
		execute("INSERT INTO %s(guildId, positionRoleId) VALUES (%d, %s) ON CONFLICT(guildId) DO UPDATE SET positionRoleId=%<s"
			.formatted(table, guildId, roleId == null ? "NULL" : roleId));
	}

	public void setNitroAutoGrant(long guildId, boolean enabled) throws SQLException {
		invalidate(guildId);
		execute("INSERT INTO %s(guildId, nitroAutoGrant) VALUES (%d, %d) ON CONFLICT(guildId) DO UPDATE SET nitroAutoGrant=%<d"
			.formatted(table, guildId, enabled ? 1 : 0));
	}

	public void setNitroExpireDays(long guildId, int days) throws SQLException {
		invalidate(guildId);
		execute("INSERT INTO %s(guildId, nitroExpireDays) VALUES (%d, %d) ON CONFLICT(guildId) DO UPDATE SET nitroExpireDays=%<d"
			.formatted(table, guildId, days));
	}

	public void setNitroRenewDays(long guildId, int days) throws SQLException {
		invalidate(guildId);
		execute("INSERT INTO %s(guildId, nitroRenewDays) VALUES (%d, %d) ON CONFLICT(guildId) DO UPDATE SET nitroRenewDays=%<d"
			.formatted(table, guildId, days));
	}

	public void removeGuild(long guildId) throws SQLException {
		invalidate(guildId);
		execute("DELETE FROM %s WHERE (guildId=%d)".formatted(table, guildId));
	}

	private void invalidate(long guildId) {
		cache.invalidate(guildId);
	}

	public static class CustomRoleSettings {
		private final Long requestsChannelId;
		private final Long reviewerRoleId;
		private final Long positionRoleId;
		private final boolean nitroAutoGrant;
		private final int nitroExpireDays;
		private final int nitroRenewDays;

		public CustomRoleSettings() {
			this.requestsChannelId = null;
			this.reviewerRoleId = null;
			this.positionRoleId = null;
			this.nitroAutoGrant = false;
			this.nitroExpireDays = 10;
			this.nitroRenewDays = 7;
		}

		public CustomRoleSettings(Map<String, Object> data) {
			this.requestsChannelId = getOrDefault(data.get("requestsChannelId"), null);
			this.reviewerRoleId = getOrDefault(data.get("reviewerRoleId"), null);
			this.positionRoleId = getOrDefault(data.get("positionRoleId"), null);
			this.nitroAutoGrant = getOrDefault(data.get("nitroAutoGrant"), 0) == 1;
			this.nitroExpireDays = getOrDefault(data.get("nitroExpireDays"), 10);
			this.nitroRenewDays = getOrDefault(data.get("nitroRenewDays"), 7);
		}

		@Nullable
		public Long getRequestsChannelId() { return requestsChannelId; }

		@Nullable
		public Long getReviewerRoleId() { return reviewerRoleId; }

		@Nullable
		public Long getPositionRoleId() { return positionRoleId; }

		public boolean isNitroAutoGrant() { return nitroAutoGrant; }

		public int getNitroExpireDays() { return nitroExpireDays; }

		public int getNitroRenewDays() { return nitroRenewDays; }

		@SuppressWarnings("BooleanMethodIsAlwaysInverted")
		public boolean isConfigured() {
			return requestsChannelId != null && reviewerRoleId != null;
		}
	}

}
