package dev.fileeditor.votl.listeners;

import java.time.Instant;
import java.time.OffsetDateTime;

import dev.fileeditor.votl.App;
import dev.fileeditor.votl.objects.annotation.Nonnull;
import dev.fileeditor.votl.objects.logs.LogType;
import dev.fileeditor.votl.utils.database.DBUtil;

import net.dv8tion.jda.api.audit.ActionType;
import net.dv8tion.jda.api.audit.AuditLogEntry;
import net.dv8tion.jda.api.entities.channel.middleman.GuildChannel;
import net.dv8tion.jda.api.events.guild.member.GuildMemberJoinEvent;
import net.dv8tion.jda.api.events.guild.member.GuildMemberRemoveEvent;
import net.dv8tion.jda.api.events.guild.member.update.GuildMemberUpdateNicknameEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;

public class MemberListener extends ListenerAdapter {

	private final App bot;
	private final DBUtil db;

	public MemberListener(App bot) {
		this.bot = bot;
		this.db = bot.getDBUtil();
	}
	
	@Override
	public void onGuildMemberJoin(@Nonnull GuildMemberJoinEvent event) {
		// Log
		if (db.getLogSettings(event.getGuild()).enabled(LogType.MEMBER)) {
			bot.getLogger().member.onJoined(event.getMember());
		}
	}
	
	@Override
	public void onGuildMemberRemove(@Nonnull GuildMemberRemoveEvent event) {
		// Log
		if (db.getLogSettings(event.getGuild()).enabled(LogType.MEMBER)) {
			event.getGuild().retrieveAuditLogs()
				.type(ActionType.KICK)
				.limit(1)
				.queue(list -> {
					if (!list.isEmpty()) {
						AuditLogEntry entry = list.get(0);
						if (!entry.getUser().equals(event.getJDA().getSelfUser()) && entry.getTargetIdLong() == event.getUser().getIdLong()
							&& entry.getTimeCreated().isAfter(OffsetDateTime.now().minusSeconds(15))) {
							bot.getLogger().mod.onUserKick(entry, event.getUser());
						}
					}
					bot.getLogger().member.onLeft(event.getGuild(), event.getMember(), event.getUser());
				},
				failure -> {
					bot.getAppLogger().warn("Unable to retrieve audit log for member kick.", failure);
					bot.getLogger().member.onLeft(event.getGuild(), event.getMember(), event.getUser());
				});
		}
		// When user leaves guild, check if there are any records in DB that would be better to remove.
		// This does not consider clearing User DB, when bot leaves guild.
		long guildId = event.getGuild().getIdLong();
		long userId = event.getUser().getIdLong();

		if (db.access.getUserLevel(guildId, userId) != null) {
			db.access.removeUser(guildId, userId);
		}
		db.user.remove(event.getUser().getIdLong());
		if (db.getTicketSettings(event.getGuild()).autocloseLeftEnabled()) {
			db.tickets.getOpenedChannel(userId, guildId).forEach(channelId -> {
				db.tickets.closeTicket(Instant.now(), channelId, "Ticket's author left the server");
				GuildChannel channel = event.getGuild().getGuildChannelById(channelId);
				if (channel != null) channel.delete().reason("Author left").queue();
			});
		}
	}

	@Override
	public void onGuildMemberUpdateNickname(@Nonnull GuildMemberUpdateNicknameEvent event) {
		if (db.getLogSettings(event.getGuild()).enabled(LogType.MEMBER)) {
			bot.getLogger().member.onNickChange(event.getMember(), event.getOldValue(), event.getNewValue());
		}
	}
	
}