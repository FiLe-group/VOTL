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
import dev.fileeditor.votl.objects.MediaType;
import dev.fileeditor.votl.objects.logs.LogType;
import dev.fileeditor.votl.objects.logs.MessageData;
import dev.fileeditor.votl.utils.CastUtil;

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
		// Media channel check
		if (event.getChannelType() == ChannelType.TEXT) {
			var mediaSettings = bot.getDBUtil().mediaChannels.getChannel(guildId, event.getChannel().getIdLong());
			assert event.getMember() != null;
			if (mediaSettings != null && !bot.getCheckUtil().hasAccess(event.getMember(), AccessPermission.ADMIN)) {
				var message = event.getMessage();
				if (message.getContentRaw().isEmpty() && message.getAttachments().isEmpty()) {
					replyMediaChannel(message, "reason_not_media");
					return;
				}

				// Attachments checks
				if (!message.getAttachments().isEmpty()) {
					var attachments = message.getAttachments();
					// Check if attachment limit is reached
					if (mediaSettings.getMaxAttachments() > -1 && attachments.size() > mediaSettings.getMaxAttachments()) {
						replyMediaChannel(message, "reason_max_attachements", attachments.size(), mediaSettings.getMaxAttachments());
						return;
					}
					// Check if attachment type is allowed
					for (var a : attachments) {
						var mediaType = MediaType.fromExtension(a.getFileExtension());
						if (mediaType.isEmpty() || !mediaSettings.getAllowedMedia().contains(mediaType.get())) {
							replyMediaChannel(message, "reason_bad_attachement", "."+a.getFileExtension());
							return;
						}
					}
				}
				// Text checks
				if (!message.getContentRaw().isEmpty()) {
					// Check if contains no links
					if (mediaSettings.getAllowedMedia().isEmpty() && MessageUtil.hasLink(message.getContentRaw())) {
						replyMediaChannel(message, "reason_has_links");
						return;
					}
					// Check if contains only 1 link and no other text
					if (!mediaSettings.allowedText()) {
						var mediaType = MediaLinkUtil.detectMediaType(message.getContentRaw());
						if (mediaType.isEmpty() || !mediaSettings.getAllowedMedia().contains(mediaType.get())) {
							replyMediaChannel(message, "reason_bad_link");
							return;
						}
					}
				}
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
