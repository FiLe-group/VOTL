package dev.fileeditor.votl.listeners;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

import ch.qos.logback.classic.Logger;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import dev.fileeditor.votl.App;
import dev.fileeditor.votl.objects.AccessPermission;
import dev.fileeditor.votl.objects.MediaChannelMode;
import dev.fileeditor.votl.objects.MediaType;
import dev.fileeditor.votl.objects.logs.LogType;
import dev.fileeditor.votl.objects.logs.MessageData;
import dev.fileeditor.votl.utils.CastUtil;

import dev.fileeditor.votl.utils.database.managers.MediaChannelsManager.MediaChannelSettings;
import dev.fileeditor.votl.utils.message.MediaLinkUtil;
import dev.fileeditor.votl.utils.message.MessageUtil;
import net.dv8tion.jda.api.audit.ActionType;
import net.dv8tion.jda.api.audit.AuditLogEntry;
import net.dv8tion.jda.api.audit.AuditLogOption;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.entities.channel.ChannelType;
import net.dv8tion.jda.api.events.message.MessageBulkDeleteEvent;
import net.dv8tion.jda.api.events.message.MessageDeleteEvent;
import net.dv8tion.jda.api.events.message.MessageReceivedEvent;
import net.dv8tion.jda.api.events.message.MessageUpdateEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import org.jetbrains.annotations.NotNull;
import org.slf4j.LoggerFactory;

public class MessageListener extends ListenerAdapter {

	private final Logger log = (Logger) LoggerFactory.getLogger(MessageListener.class);

	// Cache
	@SuppressWarnings("NullableProblems")
	private final Cache<Long, MessageData> cache = Caffeine.newBuilder()
		.expireAfterWrite(5, TimeUnit.DAYS)
		.maximumSize(5000)
		.build();

	private final App bot;
	
	public MessageListener(App bot) {
		this.bot = bot;
	}

	@Override
	public void onMessageReceived(@NotNull MessageReceivedEvent event) {
		if (event.getAuthor().isBot() || !event.isFromGuild()) return; // ignore bots and Private messages

		final long guildId = event.getGuild().getIdLong();

		// Role ticket - fire the delayed reviewer ping on the requester's first reply
		if (event.getChannelType().isThread()) {
			long channelId = event.getChannel().getIdLong();
			var tickets = bot.getDBUtil().tickets;
			if (tickets.isRoleTicket(channelId) && !tickets.isRolePinged(channelId) && !tickets.isClosed(channelId)) {
				Long requesterId = tickets.getUserId(channelId);
				if (requesterId != null && requesterId == event.getAuthor().getIdLong()) {
					List<Long> supportRoleIds = bot.getDBUtil().ticketSettings.getSettings(guildId).getRoleSupportIds();
					tickets.setRolePinged(channelId);
					bot.getTicketUtil().sendRoleTicketPing(event.getChannel().asThreadChannel(), null, supportRoleIds);
				}
			}
		}

		// Media channel check
		if (event.getChannelType() == ChannelType.TEXT) {
			var mediaSettings = bot.getDBUtil().mediaChannels.getChannel(guildId, event.getChannel().getIdLong());
			assert event.getMember() != null;
			if (mediaSettings != null && !bot.getCheckUtil().hasAccess(event.getMember(), AccessPermission.ADMIN)) {
				if (enforceMediaChannel(event.getMessage(), mediaSettings)) return;
			}
		}

		if (bot.getBlacklist().hasDnt(event.getAuthor())) return; // DNT

		// cache message if not exception channel
		if (bot.getDBUtil().getLogSettings(event.getGuild()).enabled(LogType.MESSAGE)) {
			// check channel
			if (!bot.getDBUtil().logExemptions.isExemption(guildId, event.getChannel().getIdLong())) {
				// check category
				long categoryId = switch (event.getChannelType()) {
					case TEXT, VOICE, STAGE, NEWS -> event.getGuildChannel().asStandardGuildChannel().getParentCategoryIdLong();
					case GUILD_PUBLIC_THREAD, GUILD_NEWS_THREAD -> event.getChannel().asThreadChannel().getParentChannel()
						.asStandardGuildChannel().getParentCategoryIdLong();
					default -> 0;
				};
				if (categoryId == 0 || !bot.getDBUtil().logExemptions.isExemption(guildId, categoryId)) {
					cache.put(event.getMessageIdLong(), new MessageData(event.getMessage()));
				}
			}
		}

		// reward player
		if (!bot.getBlacklist().isBlacklisted(event.getAuthor())) {
			bot.getLevelUtil().rewardMessagePlayer(event);
		}
	}

	/**
	 * Applies the channel's mode to a message, removing it if it doesn't fit.
	 *
	 * @return {@code true} if the message broke the channel's rules and was removed.
	 */
	private boolean enforceMediaChannel(Message message, MediaChannelSettings settings) {
		// Slash commands are interactions and not messages, so nothing a member sends here may stay
		if (settings.getMode() == MediaChannelMode.COMMANDS_ONLY) {
			replyMediaChannel(message, "reason_commands_only");
			return true;
		}

		var attachments = message.getAttachments();
		var content = MediaLinkUtil.scanContent(message.getContentRaw());

		// Comments only - no attachments and no links, media or not
		if (settings.getMode() == MediaChannelMode.COMMENTS_ONLY) {
			if (!attachments.isEmpty()) {
				replyMediaChannel(message, "reason_has_attachments");
				return true;
			}
			if (content.hasLinks()) {
				replyMediaChannel(message, "reason_has_links");
				return true;
			}
			if (!content.hasText()) {
				replyMediaChannel(message, "reason_not_text");
				return true;
			}
			return false;
		}

		boolean hasMedia = !attachments.isEmpty() || content.hasMediaLinks();

		// Media modes - the message must carry media, as an attachment or as a link Discord displays.
		// Restricted media mode doesn't require it - text-only messages are left alone.
		if (settings.getMode().requiresMedia() && !hasMedia) {
			replyMediaChannel(message, "reason_not_media");
			return true;
		}

		if (hasMedia) {
			// Check if attachment limit is reached
			if (settings.getMaxAttachments() > -1 && attachments.size() > settings.getMaxAttachments()) {
				replyMediaChannel(message, "reason_max_attachements", attachments.size(), settings.getMaxAttachments());
				return true;
			}
			// Check if attachment type is allowed
			for (var a : attachments) {
				var mediaType = MediaType.fromExtension(a.getFileExtension());
				if (mediaType.isEmpty() || !settings.getAllowedMedia().contains(mediaType.get())) {
					replyMediaChannel(message, "reason_bad_attachement", "."+a.getFileExtension());
					return true;
				}
			}
			// Check if linked media type is allowed
			for (var mediaType : content.mediaLinks()) {
				if (!settings.getAllowedMedia().contains(mediaType)) {
					replyMediaChannel(message, "reason_bad_link");
					return true;
				}
			}
		}

		// Media only - anything besides the media itself is a comment
		if (settings.getMode() == MediaChannelMode.MEDIA_ONLY && (content.hasText() || content.otherLinks() > 0)) {
			replyMediaChannel(message, "reason_no_comments");
			return true;
		}

		return false;
	}

	private void replyMediaChannel(Message message, String pathEnd, Object... args) {
		var reason = MessageUtil.limitString(
			bot.getLocaleUtil().getGuildText(message.getGuild(), "bot.tool.media_channel.listener."+pathEnd)
				.formatted(args),
			512
		);

		message.reply(bot.getLocaleUtil().getGuildText(message.getGuild(), "bot.tool.media_channel.listener.reply_title")+"\n> "+reason)
			.queue(m -> m.delete().queueAfter(5, TimeUnit.SECONDS), _ -> {});
		message.delete()
			.reason(reason)
			.queueAfter(2, TimeUnit.SECONDS);
	}

	
	@Override
	public void onMessageUpdate(@NotNull MessageUpdateEvent event) {
		if (event.getAuthor().isBot() || !event.isFromGuild()) return;
		if (!bot.getDBUtil().getLogSettings(event.getGuild()).enabled(LogType.MESSAGE)) return;
		if (bot.getBlacklist().hasDnt(event.getAuthor())) return; // DNT

		final long guildId = event.getGuild().getIdLong();
		// check channel
		if (bot.getDBUtil().logExemptions.isExemption(guildId, event.getChannel().getIdLong())) return;
		// check category
		long categoryId = switch (event.getChannelType()) {
			case TEXT, VOICE, STAGE, NEWS -> event.getGuildChannel().asStandardGuildChannel().getParentCategoryIdLong();
			case GUILD_PUBLIC_THREAD, GUILD_NEWS_THREAD -> event.getChannel().asThreadChannel().getParentChannel()
				.asStandardGuildChannel().getParentCategoryIdLong();
			default -> 0;
		};
		if (categoryId != 0 && bot.getDBUtil().logExemptions.isExemption(guildId, categoryId)) {
			return;
		}
		
		final long messageId = event.getMessageIdLong();
		MessageData oldData = cache.getIfPresent(messageId);
		MessageData newData = new MessageData(event.getMessage());
		cache.put(event.getMessageIdLong(), newData);

		bot.getGuildLogger().message.onMessageUpdate(event.getMember(), event.getGuildChannel(), messageId, oldData, newData);
	}

	@Override
	public void onMessageDelete(@NotNull MessageDeleteEvent event) {
		if (!event.isFromGuild()) return;
		if (!bot.getDBUtil().getLogSettings(event.getGuild()).enabled(LogType.MESSAGE)) return;

		final long messageId = event.getMessageIdLong();

		MessageData data = cache.getIfPresent(messageId);
		if (data != null) cache.invalidate(messageId);

		final long guildId = event.getGuild().getIdLong();
		// check channel
		if (bot.getDBUtil().logExemptions.isExemption(guildId, event.getChannel().getIdLong())) return;
		// check category
		long categoryId = switch (event.getChannelType()) {
			case TEXT, VOICE, STAGE, NEWS -> event.getGuildChannel().asStandardGuildChannel().getParentCategoryIdLong();
			case GUILD_PUBLIC_THREAD, GUILD_NEWS_THREAD -> event.getChannel().asThreadChannel().getParentChannel()
				.asStandardGuildChannel().getParentCategoryIdLong();
			default -> 0;
		};
		if (categoryId != 0 && bot.getDBUtil().logExemptions.isExemption(guildId, categoryId)) {
			return;
		}

		event.getGuild().retrieveAuditLogs()
			.type(ActionType.MESSAGE_DELETE)
			.limit(1)
			.queue(list -> {
				if (!list.isEmpty() && data != null) {
					AuditLogEntry entry = list.getFirst();
					if (entry.getTargetIdLong() == data.getAuthorId() && entry.getTimeCreated().isAfter(OffsetDateTime.now().minusSeconds(4))) {
						bot.getGuildLogger().message.onMessageDelete(event.getGuildChannel(), messageId, data, entry.getUserIdLong());
						return;
					}
				}
				bot.getGuildLogger().message.onMessageDelete(event.getGuildChannel(), messageId, data, null);
			},
			failure -> {
				log.warn("Failed to queue audit log for message deletion.", failure);
				bot.getGuildLogger().message.onMessageDelete(event.getGuildChannel(), messageId, data, null);
			});
	}

	@Override
	public void onMessageBulkDelete(@NotNull MessageBulkDeleteEvent event) {
		if (!bot.getDBUtil().getLogSettings(event.getGuild()).enabled(LogType.MESSAGE)) return;

		final List<Long> messageIds = event.getMessageIds().stream().map(CastUtil::castLong).toList();
		if (messageIds.isEmpty()) return;

		List<MessageData> messages = new ArrayList<>();
		cache.getAllPresent(messageIds).forEach((k, v) -> {
			messages.add(v);
			cache.invalidate(k);
		});
		event.getGuild().retrieveAuditLogs()
			.type(ActionType.MESSAGE_BULK_DELETE)
			.limit(1)
			.queue(list -> {
				if (list.isEmpty()) {
					bot.getGuildLogger().message.onMessageBulkDelete(event.getChannel(), String.valueOf(messageIds.size()), messages, null);
				} else {
					AuditLogEntry entry = list.getFirst();
					String count = entry.getOption(AuditLogOption.COUNT);
					if (entry.getTimeCreated().isAfter(OffsetDateTime.now().minusSeconds(4)))
						bot.getGuildLogger().message.onMessageBulkDelete(event.getChannel(), count, messages, entry.getUserIdLong());
					else
						bot.getGuildLogger().message.onMessageBulkDelete(event.getChannel(), count, messages, null);
				}
			});
	}

}
