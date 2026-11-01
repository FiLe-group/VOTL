package dev.fileeditor.votl.utils.database.managers;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import dev.fileeditor.votl.objects.ExpType;
import dev.fileeditor.votl.objects.constants.Constants;
import dev.fileeditor.votl.utils.database.ConnectionUtil;
import dev.fileeditor.votl.utils.database.LiteBase;
import dev.fileeditor.votl.utils.level.LevelUtil;
import dev.fileeditor.votl.utils.level.PlayerObject;
import net.dv8tion.jda.api.entities.Guild;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.math.BigInteger;
import java.sql.SQLException;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static dev.fileeditor.votl.utils.CastUtil.*;

public class LevelManager extends LiteBase {
	private final String TABLE_SETTINGS = "levelSettings";
	private final String TABLE_PLAYERS = "levelPlayers";

	// cache
	@SuppressWarnings("NullableProblems")
	private final Cache<String, PlayerData> playersCache = Caffeine.newBuilder()
		.expireAfterAccess(5, TimeUnit.MINUTES)
		.build();
	@SuppressWarnings("NullableProblems")
	private final Cache<Long, LevelSettings> settingsCache = Caffeine.newBuilder()
		.maximumSize(Constants.DEFAULT_CACHE_SIZE)
		.build();
	private final LevelSettings blankSettings = new LevelSettings();

	public LevelManager(ConnectionUtil cu) {
		super(cu, null);
	}

	// Settings
	public LevelSettings getSettings(Guild guild) {
		return getSettings(guild.getIdLong());
	}

	@NotNull
	public LevelSettings getSettings(long guildId) {
		var settings = settingsCache.get(guildId, id -> applyOrDefault(getSettingsData(id), LevelSettings::new, blankSettings));
		return settings== null ? blankSettings : settings;
	}

	private Map<String, Object> getSettingsData(long guildId) {
		return selectOne("SELECT * FROM %s WHERE (guildId=%d)".formatted(TABLE_SETTINGS, guildId), Set.of("enabled", "exemptChannels", "voiceEnabled"));
	}

	public void remove(long guildId) throws SQLException {
		invalidateSettings(guildId);
		execute("DELETE FROM %s WHERE (guildId=%d)".formatted(TABLE_SETTINGS, guildId));
	}

	public void setEnabled(long guildId, boolean enabled) throws SQLException {
		invalidateSettings(guildId);
		execute("INSERT INTO %s(guildId, enabled) VALUES (%d, %d) ON CONFLICT(guildId) DO UPDATE SET enabled=%<d".formatted(TABLE_SETTINGS, guildId, enabled?1:0));
	}

	public void setExemptChannels(long guildId, @Nullable String channelIds) throws SQLException {
		invalidateSettings(guildId);
		execute("INSERT INTO %s(guildId, exemptChannels) VALUES (%d, %s) ON CONFLICT(guildId) DO UPDATE SET exemptChannels=%<s".formatted(TABLE_SETTINGS, guildId, quote(channelIds)));
	}

	public void removeExemptChannel(long guildId, long channelId) throws SQLException {
		LevelSettings settings = getSettings(guildId);
		if (!settings.isExemptChannel(channelId)) return;

		setExemptChannels(guildId, settings.getExemptChannels()
			.stream()
			.filter(id -> id != channelId)
			.map(String::valueOf)
			.collect(Collectors.joining(";"))
		);
	}

	public void setVoiceEnabled(long guildId, boolean enabled) throws SQLException {
		invalidateSettings(guildId);
		execute("INSERT INTO %s(guildId, voiceEnabled) VALUES (%d, %d) ON CONFLICT(guildId) DO UPDATE SET voiceEnabled=%<d".formatted(TABLE_SETTINGS, guildId, enabled?1:0));
	}

	public void invalidateSettings(long guildId) {
		settingsCache.invalidate(guildId);
	}

	// Guild levels
	@NotNull
	public PlayerData getPlayer(long guildId, long userId) {
		String key = PlayerObject.asKey(guildId, userId);
		return playersCache.get(key, _ -> getPlayerData(guildId, userId));
	}

	@Nullable
	public PlayerData getPlayer(PlayerObject player) {
		return playersCache.getIfPresent(player.asKey());
	}

	@NotNull
	private PlayerData getPlayerData(long guildId, long userId) {
		return new PlayerData(selectOne("SELECT * FROM %s WHERE (guildId=%d AND userId=%d)".formatted(TABLE_PLAYERS, guildId, userId), Set.of("textExp", "voiceExp", "lastUpdate")));
	}

	public void updatePlayer(PlayerObject player, PlayerData playerData) throws SQLException {
		// globalExp is incremented, so only send what was gained since the last flush
		final long addedGlobal = playerData.getAddedGlobalExperience();
		execute("INSERT INTO %s(guildId, userId, textExp, voiceExp, globalExp, lastUpdate) VALUES (%d, %d, %d, %d, %d, %d) ON CONFLICT(guildId, userId) DO UPDATE SET textExp=%4$d, voiceExp=%5$d, globalExp=globalExp+%6$d, lastUpdate=%7$d;"
			.formatted(
				TABLE_PLAYERS, player.guildId, player.userId,
				playerData.getExperience(ExpType.TEXT), playerData.getExperience(ExpType.VOICE), addedGlobal, playerData.getLastUpdate()
			)
		);
		playerData.flushedGlobalExperience(addedGlobal);
	}

	public void addVoiceTime(PlayerObject player, long duration) {
		try {
			execute("INSERT INTO %s(guildId, userId, voiceTime) VALUES (%d, %d, %d) ON CONFLICT(guildId, userId) DO UPDATE SET voiceTime=voiceTime+%<d;"
				.formatted(TABLE_PLAYERS, player.guildId, player.userId, duration)
			);
		} catch (SQLException ignored) {}
	}

	public long getSumGlobalExp(long userId) {
		Long data = selectOne("SELECT SUM(globalExp) AS sumGlobalExp FROM %s WHERE (userId=%d)".formatted(TABLE_PLAYERS, userId), "sumGlobalExp", Long.class);
		return data==null?0:data;
	}

	public Integer getServerRank(long guildId, long userId, ExpType expType) {
		String type = expType==ExpType.TEXT?"textExp":"voiceExp";
		// Same as DENSE_RANK over the guild, but only counts the higher scores through the (guildId, exp) index
		return selectOne("SELECT (SELECT COUNT(DISTINCT %1$s) FROM %2$s WHERE (guildId=%3$d AND %1$s>p.%1$s))+1 AS rank FROM %2$s p WHERE (guildId=%3$d AND userId=%4$d AND %1$s>0)"
			.formatted(type, TABLE_PLAYERS, guildId, userId), "rank", Integer.class);
	}

	@SuppressWarnings("unused")
	public Integer getGlobalRank(long userId) {
		return selectOne("WITH rankedUsers AS (SELECT userId, SUM(globalExp) as totalExp, DENSE_RANK() OVER (ORDER BY SUM(globalExp) DESC) AS rank FROM %s GROUP BY userId) SELECT rank FROM rankedUsers WHERE (userId=%d)"
			.formatted(TABLE_PLAYERS, userId), "rank", Integer.class);
	}

	@NotNull
	public TopInfo getServerTop(long guildId, ExpType expType, int limit) {
		if (limit < 1 || limit > 20) {
			throw new IllegalArgumentException("limit must be between 1 and 20");
		}
		final boolean fetchText = switch (expType) {
			case TEXT, TOTAL -> true;
			default -> false;
		};
		final boolean fetchVoice = switch (expType) {
			case VOICE, TOTAL -> true;
			default -> false;
		};

		StringBuilder query = new StringBuilder("WITH rankedUsers AS (SELECT userId, guildId");

		if (fetchText) query.append(", textExp, ROW_NUMBER() OVER (PARTITION BY guildId ORDER BY textExp DESC) AS textRank");
		if (fetchVoice) query.append(", voiceExp, ROW_NUMBER() OVER (PARTITION BY guildId ORDER BY voiceExp DESC) AS voiceRank");

		query.append(" FROM ").append(TABLE_PLAYERS)
			.append(" WHERE guildId=").append(guildId).append(")")
			.append(" SELECT userId");

		if (fetchText) query.append(", textExp, textRank");
		if (fetchVoice) query.append(", voiceExp, voiceRank");
		query.append(" FROM rankedUsers");

		query.append(" WHERE ");
		if (fetchText && fetchVoice) {
			query.append("textRank <= ").append(limit).append(" OR voiceRank <= ").append(limit);
		} else if (fetchText) {
			query.append("textRank <= ").append(limit);
		} else if (fetchVoice) {
			query.append("voiceRank <= ").append(limit);
		}

		Set<String> keys = new HashSet<>();
		keys.add("userId");
		if (fetchText) {
			keys.add("textExp");
			keys.add("textRank");
		}
		if (fetchVoice) {
			keys.add("voiceExp");
			keys.add("voiceRank");
		}

		return new TopInfo(select(query.toString(), keys), limit);
	}

	public void deleteUser(long guildId, long userId) throws SQLException {
		playersCache.invalidate(PlayerObject.asKey(guildId, userId));
		execute("DELETE FROM %s WHERE (guildId=%d AND userId=%d)".formatted(TABLE_PLAYERS, guildId, userId));
	}

	public void deleteUser(long userId) throws SQLException {
		execute("DELETE FROM %s WHERE (userId=%d)".formatted(TABLE_PLAYERS, userId));
	}

	public void deleteGuild(long guildId) throws SQLException {
		execute("DELETE FROM %s WHERE (guildId=%d)".formatted(TABLE_PLAYERS, guildId));
	}

	public static class LevelSettings {
		private final boolean enabled, voiceEnabled;
		private final Set<Long> exemptChannels;

		public LevelSettings() {
			this.enabled = false;
			this.exemptChannels = Set.of();
			this.voiceEnabled = true;
		}

		public LevelSettings(Map<String, Object> data) {
			this.enabled = (int) requireNonNull(data.get("enabled"))==1;
			this.exemptChannels = resolveOrDefault(
				data.get("exemptChannels"),
				o -> Stream.of(String.valueOf(o).split(";"))
					.map(Long::parseLong)
					.collect(Collectors.toSet()),
				Set.of()
			);
			this.voiceEnabled = (int) requireNonNull(data.get("voiceEnabled"))==1;
		}

		public boolean isEnabled() {
			return enabled;
		}

		public Set<Long> getExemptChannels() {
			return exemptChannels;
		}

		public boolean isExemptChannel(long channelId) {
			return exemptChannels.contains(channelId);
		}

		public boolean isVoiceEnabled() {
			return enabled && voiceEnabled;
		}
	}

	public static class PlayerData {
		private long textExperience = 0;
		private long voiceExperience = 0;
		// Gained since the last flush. Added on event threads while the flush job reads it, hence atomic.
		private final AtomicLong addedGlobalExperience = new AtomicLong();
		private long lastUpdate = 0;

		PlayerData(Map<String, Object> data) {
			if (data != null) {
				BigInteger exp = new BigInteger(getOrDefault(String.valueOf(data.get("textExp")), "0"));
				if (exp.compareTo(BigInteger.valueOf(LevelUtil.getHardCap())) >= 0) {
					this.textExperience = LevelUtil.getHardCap();
				} else {
					this.textExperience = exp.longValue();
				}
				exp = new BigInteger(getOrDefault(String.valueOf(data.get("voiceExp")), "0"));
				if (exp.compareTo(BigInteger.valueOf(LevelUtil.getHardCap())) >= 0) {
					this.voiceExperience = LevelUtil.getHardCap();
				} else {
					this.voiceExperience = exp.longValue();
				}
				this.lastUpdate = getOrDefault(data.get("lastUpdate"), 0L);
			}
		}

		public long getExperience(ExpType expType) {
			return switch (expType) {
				case TEXT -> textExperience;
				case VOICE -> voiceExperience;
				case TOTAL -> textExperience+voiceExperience;
			};
		}

		public long getAddedGlobalExperience() {
			return addedGlobalExperience.get();
		}

		/**
		 * Marks {@code amount} as written to the DB. Subtracted rather than reset, so experience
		 * gained while the flush was running is kept for the next one.
		 */
		public void flushedGlobalExperience(long amount) {
			addedGlobalExperience.addAndGet(-amount);
		}

		public void setExperience(long experience, ExpType expType) {
			switch (expType) {
				case TEXT -> textExperience = experience;
				case VOICE -> voiceExperience = experience;
			}
			this.lastUpdate = Instant.now().toEpochMilli();
		}

		public void incrementExperienceBy(long amount, ExpType expType) {
			switch (expType) {
				case TEXT -> textExperience += amount;
				case VOICE -> voiceExperience += amount;
			}
			this.addedGlobalExperience.addAndGet(amount);
			this.lastUpdate = Instant.now().toEpochMilli();
		}

		public void decreaseExperienceBy(long amount, ExpType expType) {
			switch (expType) {
				case TEXT -> textExperience -= amount;
				case VOICE -> voiceExperience -= amount;
			}
			this.lastUpdate = Instant.now().toEpochMilli();
		}

		public void clearExperience() {
			this.textExperience = 0;
			this.voiceExperience = 0;
			this.lastUpdate = Instant.now().toEpochMilli();
		}

		public long getLastUpdate() {
			return lastUpdate;
		}
	}

	public static class TopInfo {
		private final Map<Integer, TopUser> textTop = new HashMap<>();
		private final Map<Integer, TopUser> voiceTop = new HashMap<>();

		public TopInfo(List<Map<String, Object>> data, int limitRank) {
			for (Map<String, Object> row : data) {
				long userId = requireNonNull(row.get("userId"));

				if (row.containsKey("textExp")) {
					long exp = castLong(row.get("textExp"));
					int rank = requireNonNull(row.get("textRank"));
					if (exp > 0 && rank <= limitRank) {
						textTop.put(rank, new TopUser(userId, exp));
					}
				}

				if (row.containsKey("voiceExp")) {
					int rank = requireNonNull(row.get("voiceRank"));
					long exp = castLong(row.get("voiceExp"));
					if (exp > 0 && rank <= limitRank) {
						voiceTop.put(rank, new TopUser(userId, exp));
					}
				}
			}
		}

		public Map<Integer, TopUser> getTextTop() {
			return textTop;
		}

		public Map<Integer, TopUser> getVoiceTop() {
			return voiceTop;
		}
	}

	public record TopUser(long userId, long exp) {}
}
