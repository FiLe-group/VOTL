package dev.fileeditor.votl.utils.database.managers;

import dev.fileeditor.votl.utils.database.ConnectionUtil;
import dev.fileeditor.votl.utils.database.LiteBase;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.entities.MessageEmbed;
import net.dv8tion.jda.api.utils.data.DataArray;
import net.dv8tion.jda.api.utils.messages.MessageCreateBuilder;
import net.dv8tion.jda.api.utils.messages.MessageCreateData;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.sql.SQLException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static dev.fileeditor.votl.utils.CastUtil.castLong;
import static dev.fileeditor.votl.utils.CastUtil.getOrDefault;

public class RepeatMessageManager extends LiteBase {

	private static final Set<String> COLUMNS = Set.of(
		"repeatId", "guildId", "channelId", "sourceId", "content", "embeds",
		"interval", "nextRun", "skipIfLast", "lastMessageId"
	);

	/** A repeated announcement must never turn into a recurring mass ping. */
	private static final EnumSet<Message.MentionType> ALLOWED_MENTIONS = EnumSet.of(
		Message.MentionType.USER, Message.MentionType.ROLE, Message.MentionType.CHANNEL,
		Message.MentionType.EMOJI, Message.MentionType.SLASH_COMMAND
	);

	public RepeatMessageManager(ConnectionUtil cu) {
		super(cu, "repeatMessages");
	}

	public int add(long guildId, long channelId, long sourceId, @Nullable String content,
	               @Nullable String embeds, long intervalSec, long nextRun, boolean skipIfLast) throws SQLException {
		return executeWithRow("INSERT INTO %s(guildId, channelId, sourceId, content, embeds, interval, nextRun, skipIfLast) VALUES (%d, %d, %d, %s, %s, %d, %d, %d)"
			.formatted(table, guildId, channelId, sourceId, quote(content), quote(embeds), intervalSec, nextRun, skipIfLast ? 1 : 0));
	}

	public void remove(long guildId, int repeatId) throws SQLException {
		execute("DELETE FROM %s WHERE (guildId=%d AND repeatId=%d)".formatted(table, guildId, repeatId));
	}

	public void removeChannel(long guildId, long channelId) throws SQLException {
		execute("DELETE FROM %s WHERE (guildId=%d AND channelId=%d)".formatted(table, guildId, channelId));
	}

	public void removeGuild(long guildId) throws SQLException {
		execute("DELETE FROM %s WHERE (guildId=%d)".formatted(table, guildId));
	}

	public int countGuild(long guildId) {
		return count("SELECT COUNT(*) FROM %s WHERE (guildId=%d)".formatted(table, guildId));
	}

	@NotNull
	public List<RepeatMessage> getGuild(long guildId) {
		return map(select("SELECT * FROM %s WHERE (guildId=%d) ORDER BY repeatId".formatted(table, guildId), COLUMNS));
	}

	@Nullable
	public RepeatMessage get(long guildId, int repeatId) {
		Map<String, Object> data = selectOne(
			"SELECT * FROM %s WHERE (guildId=%d AND repeatId=%d)".formatted(table, guildId, repeatId), COLUMNS
		);
		return data == null ? null : new RepeatMessage(data);
	}

	@NotNull
	public List<RepeatMessage> getExpired() {
		return map(select("SELECT * FROM %s WHERE (nextRun<=%d)".formatted(table, Instant.now().getEpochSecond()), COLUMNS));
	}

	/** Moves the entry to its next run. {@code lastMessageId} is left untouched when null. */
	public void updateAfterRun(int repeatId, long nextRun, @Nullable Long lastMessageId) throws SQLException {
		execute("UPDATE %s SET nextRun=%d%s WHERE (repeatId=%d)".formatted(
			table, nextRun, lastMessageId == null ? "" : ", lastMessageId="+lastMessageId, repeatId
		));
	}

	public void updateLastMessage(int repeatId, long lastMessageId) throws SQLException {
		execute("UPDATE %s SET lastMessageId=%d WHERE (repeatId=%d)".formatted(table, lastMessageId, repeatId));
	}

	private List<RepeatMessage> map(List<Map<String, Object>> data) {
		List<RepeatMessage> list = new ArrayList<>(data.size());
		data.forEach(row -> list.add(new RepeatMessage(row)));
		return list;
	}

	/** Encodes a message's embeds for storage. Returns null when there are none. */
	@Nullable
	public static String encodeEmbeds(List<MessageEmbed> embeds) {
		if (embeds.isEmpty()) return null;
		return DataArray.fromCollection(embeds.stream().map(MessageEmbed::toData).toList()).toString();
	}


	public static class RepeatMessage {
		private final int repeatId;
		private final long guildId;
		private final long channelId;
		private final long sourceId;
		private final String content;
		private final String embeds;
		private final long interval;
		private final long nextRun;
		private final boolean skipIfLast;
		private final Long lastMessageId;

		public RepeatMessage(Map<String, Object> data) {
			this.repeatId = getOrDefault(data.get("repeatId"), 0);
			this.guildId = getOrDefault(data.get("guildId"), 0L);
			this.channelId = getOrDefault(data.get("channelId"), 0L);
			this.sourceId = getOrDefault(data.get("sourceId"), 0L);
			this.content = (String) data.get("content");
			this.embeds = (String) data.get("embeds");
			this.interval = getOrDefault(data.get("interval"), 0L);
			this.nextRun = getOrDefault(data.get("nextRun"), 0L);
			this.skipIfLast = getOrDefault(data.get("skipIfLast"), 0) == 1;
			this.lastMessageId = castLong(data.get("lastMessageId"));
		}

		public int getRepeatId() {
			return repeatId;
		}

		public long getGuildId() {
			return guildId;
		}

		public long getChannelId() {
			return channelId;
		}

		public long getSourceId() {
			return sourceId;
		}

		@Nullable
		public String getContent() {
			return content;
		}

		/** Interval between two runs, in seconds. */
		public long getInterval() {
			return interval;
		}

		public long getNextRun() {
			return nextRun;
		}

		/** Whether the run is skipped while the channel's newest message is still our previous copy. */
		public boolean isSkipIfLast() {
			return skipIfLast;
		}

		@Nullable
		public Long getLastMessageId() {
			return lastMessageId;
		}

		@NotNull
		public List<MessageEmbed> getEmbeds() {
			if (embeds == null || embeds.isBlank()) return List.of();
			DataArray array = DataArray.fromJson(embeds);
			List<MessageEmbed> list = new ArrayList<>(array.length());
			for (int i = 0; i < array.length(); i++) {
				list.add(EmbedBuilder.fromData(array.getObject(i)).build());
			}
			return list;
		}

		/** Rebuilds the stored snapshot into a sendable message. */
		@NotNull
		public MessageCreateData toMessageData() {
			MessageCreateBuilder builder = new MessageCreateBuilder()
				.setAllowedMentions(ALLOWED_MENTIONS);
			if (content != null && !content.isBlank()) builder.setContent(content);
			List<MessageEmbed> parsed = getEmbeds();
			if (!parsed.isEmpty()) builder.setEmbeds(parsed);
			return builder.build();
		}
	}

}
