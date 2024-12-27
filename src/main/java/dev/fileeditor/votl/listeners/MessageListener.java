package dev.fileeditor.votl.listeners;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;

import dev.fileeditor.votl.App;
import dev.fileeditor.votl.objects.constants.Constants;
import dev.fileeditor.votl.objects.logs.LogType;
import dev.fileeditor.votl.objects.logs.MessageData;
import dev.fileeditor.votl.utils.CastUtil;

import dev.fileeditor.votl.utils.FixedExpirableCache;
import net.dv8tion.jda.api.audit.ActionType;
import net.dv8tion.jda.api.audit.AuditLogEntry;
import net.dv8tion.jda.api.audit.AuditLogOption;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.channel.ChannelType;
import net.dv8tion.jda.api.entities.channel.concrete.Category;
import net.dv8tion.jda.api.events.message.MessageBulkDeleteEvent;
import net.dv8tion.jda.api.events.message.MessageDeleteEvent;
import net.dv8tion.jda.api.events.message.MessageReceivedEvent;
import net.dv8tion.jda.api.events.message.MessageUpdateEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import org.jetbrains.annotations.NotNull;

public class MessageListener extends ListenerAdapter {

	// Cache
	private final FixedExpirableCache<Long, MessageData> cache = new FixedExpirableCache<>(Constants.DEFAULT_CACHE_SIZE*60, 5*24*3600); // store for max 5 days

	private final App bot;
	
	public MessageListener(App bot) {
		this.bot = bot;
	}

	@Override
	public void onMessageReceived(@NotNull MessageReceivedEvent event) {
		if (event.getAuthor().isBot() || !event.isFromGuild()) return; //ignore bots and Private messages
		
		// cache message if not exception channel
		final Guild guild = event.getGuild();
		if (bot.getDBUtil().getLogSettings(event.getGuild()).enabled(LogType.MESSAGE)
			&& !bot.getDBUtil().logExceptions.isException(guild.getIdLong(), event.getChannel().getIdLong()))
		{
			cache.put(event.getMessageIdLong(), new MessageData(event.getMessage()));
		}
	}

	
	@Override
	public void onMessageUpdate(@NotNull MessageUpdateEvent event) {
		if (event.getAuthor().isBot() || !event.isFromGuild()) return;
		if (!bot.getDBUtil().getLogSettings(event.getGuild()).enabled(LogType.MESSAGE)) return;

		final long guildId = event.getGuild().getIdLong();
		if (bot.getDBUtil().logExceptions.isException(guildId, event.getChannel().getIdLong())) return;
		if (event.getChannel().getType().equals(ChannelType.TEXT)) {
			Category parentCategory = event.getChannel().asTextChannel().getParentCategory();
			if (parentCategory != null) {
				if (bot.getDBUtil().logExceptions.isException(guildId, parentCategory.getIdLong())) return;
			}
		}
		
		final long messageId = event.getMessageIdLong();
		MessageData oldData = cache.get(messageId);
		MessageData newData = new MessageData(event.getMessage());
		cache.put(event.getMessageIdLong(), newData);

		bot.getLogger().message.onMessageUpdate(event.getMember(), event.getGuildChannel(), messageId, oldData, newData);
	}

	@Override
	public void onMessageDelete(@NotNull MessageDeleteEvent event) {
		if (!event.isFromGuild()) return;
		if (!bot.getDBUtil().getLogSettings(event.getGuild()).enabled(LogType.MESSAGE)) return;

		final long messageId = event.getMessageIdLong();

		MessageData data = cache.get(messageId);
		if (data != null) cache.pull(messageId);

		final long guildId = event.getGuild().getIdLong();
		if (bot.getDBUtil().logExceptions.isException(guildId, event.getChannel().getIdLong())) return;
		if (event.getChannel().getType().equals(ChannelType.TEXT)) {
			Category parentCategory = event.getChannel().asTextChannel().getParentCategory();
			if (parentCategory != null) {
				if (bot.getDBUtil().logExceptions.isException(guildId, parentCategory.getIdLong())) return;
			}
		}

		event.getGuild().retrieveAuditLogs()
			.type(ActionType.MESSAGE_DELETE)
			.limit(1)
			.queue(list -> {
				if (!list.isEmpty() && data != null) {
					AuditLogEntry entry = list.get(0);
					if (entry.getTargetIdLong() == data.getAuthorId() && entry.getTimeCreated().isAfter(OffsetDateTime.now().minusSeconds(15))) {
						bot.getLogger().message.onMessageDelete(event.getGuildChannel(), messageId, data, entry.getUserIdLong());
						return;
					}
				}
				bot.getLogger().message.onMessageDelete(event.getGuildChannel(), messageId, data, null);
			},
			failure -> {
				bot.getAppLogger().warn("Failed to queue audit log for message deletion.", failure);
				bot.getLogger().message.onMessageDelete(event.getGuildChannel(), messageId, data, null);
			});
	}

	@Override
	public void onMessageBulkDelete(@NotNull MessageBulkDeleteEvent event) {
		if (!bot.getDBUtil().getLogSettings(event.getGuild()).enabled(LogType.MESSAGE)) return;

		final List<Long> messageIds = event.getMessageIds().stream().map(CastUtil::castLong).toList();
		if (messageIds.isEmpty()) return;

		List<MessageData> messages = new ArrayList<>();
		messageIds.forEach(id -> {
			if (cache.contains(id)) {
				messages.add(cache.get(id));
				cache.pull(id);
			}
		});
		event.getGuild().retrieveAuditLogs()
			.type(ActionType.MESSAGE_BULK_DELETE)
			.limit(1)
			.queue(list -> {
				if (list.isEmpty()) {
					bot.getLogger().message.onMessageBulkDelete(event.getChannel(), String.valueOf(messageIds.size()), messages, null);
				} else {
					AuditLogEntry entry = list.get(0);
					String count = entry.getOption(AuditLogOption.COUNT);
					if (entry.getTimeCreated().isAfter(OffsetDateTime.now().minusSeconds(15)))
						bot.getLogger().message.onMessageBulkDelete(event.getChannel(), count, messages, entry.getUserIdLong());
					else
						bot.getLogger().message.onMessageBulkDelete(event.getChannel(), count, messages, null);
				}
			});
	}

	public void shutdown() {
		cache.shutdown();
	}

}
