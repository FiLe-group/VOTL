package dev.fileeditor.votl.utils.database.managers;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import dev.fileeditor.votl.objects.MediaType;
import dev.fileeditor.votl.objects.constants.Constants;
import dev.fileeditor.votl.utils.database.ConnectionUtil;
import dev.fileeditor.votl.utils.database.LiteBase;
import org.jetbrains.annotations.Nullable;

import java.sql.SQLException;
import java.util.*;
import java.util.stream.Collectors;

import static dev.fileeditor.votl.utils.CastUtil.*;

public class MediaChannelsManager extends LiteBase {

	private final Set<String> columns = Set.of(
		"channelId", "allowedMedia", "allowedText", "maxAttachments"
	);

	// Cache - per guild, per channel
	private final Cache<Long, Map<Long, MediaChannelSettings>> cache = Caffeine.newBuilder()
		.maximumSize(Constants.DEFAULT_CACHE_SIZE)
		.build();
	private final Map<Long, MediaChannelSettings> emptySettings = Collections.emptyMap();

	public MediaChannelsManager(ConnectionUtil cu) {
		super(cu, "mediaChannels");
	}

	public void addChannel(long guildId, long channelId, EnumSet<MediaType> allowedMedia, boolean allowedText, int maxAttachments) throws SQLException {
		cache.invalidate(guildId);
		execute("INSERT INTO %s(guildId, channelId, allowedMedia, allowedText, maxAttachments) VALUES (%s, %s, %s, %s, %s)".formatted(
			table, guildId, channelId, MediaType.encode(allowedMedia), allowedText?1:0, maxAttachments
		));
	}

	public void removeChannel(long guildId, long channelId) throws SQLException {
		cache.invalidate(guildId);
		execute("DELETE FROM %s WHERE (channelId=%s)".formatted(table, channelId));
	}

	public void removeGuild(long guildId) throws SQLException {
		cache.invalidate(guildId);
		execute("DELETE FROM %s WHERE (guildId=%s)".formatted(table, guildId));
	}

	public Map<Long, MediaChannelSettings> getChannels(long guildId) {
		var cachedData = cache.getIfPresent(guildId);
		if (cachedData != null) return cachedData;

		var data = getData(guildId);
		if (data.isEmpty()) {
			cache.put(guildId, emptySettings);
			return emptySettings;
		}

		var guildMap = data.stream()
			.collect(Collectors.toMap(v -> castLong(v.get("channelId")), MediaChannelSettings::new));

		cache.put(guildId, guildMap);
		return guildMap;
	}

	@Nullable
	public MediaChannelSettings getChannel(long guildId, long channelId) {
		return getChannels(guildId).get(channelId);
	}

	private List<Map<String, Object>> getData(long guildId) {
		return select("SELECT * FROM %s WHERE (guildId=%d)".formatted(table, guildId), columns);
	}


	public static class MediaChannelSettings {
		private final EnumSet<MediaType> allowedMedia;
		private final boolean allowedText;
		private final int maxAttachments;

		public MediaChannelSettings() {
			this.allowedMedia = EnumSet.allOf(MediaType.class);
			this.allowedText = true;
			this.maxAttachments = -1; // Disable check
		}

		public MediaChannelSettings(Map<String, Object> data) {
			this.allowedMedia = MediaType.decode(getOrDefault(data.get("allowedMedia"), 0));
			this.allowedText = getOrDefault(data.get("allowedText"), 1) == 1;
			this.maxAttachments = getOrDefault(data.get("maxAttachments"), -1);
		}

		public EnumSet<MediaType> getAllowedMedia() {
			return allowedMedia;
		}

		public boolean allowedText() {
			return allowedText;
		}

		public int getMaxAttachments() {
			return maxAttachments;
		}
	}

}
