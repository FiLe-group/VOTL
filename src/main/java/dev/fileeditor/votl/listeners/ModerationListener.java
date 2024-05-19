package dev.fileeditor.votl.listeners;

import dev.fileeditor.votl.App;
import dev.fileeditor.votl.objects.CaseType;
import dev.fileeditor.votl.objects.annotation.Nonnull;
import dev.fileeditor.votl.objects.logs.LogType;
import dev.fileeditor.votl.utils.database.DBUtil;
import dev.fileeditor.votl.utils.database.managers.CaseManager.CaseData;

import net.dv8tion.jda.api.audit.ActionType;
import net.dv8tion.jda.api.audit.AuditLogChange;
import net.dv8tion.jda.api.audit.AuditLogEntry;
import net.dv8tion.jda.api.audit.AuditLogKey;
import net.dv8tion.jda.api.events.guild.GuildBanEvent;
import net.dv8tion.jda.api.events.guild.GuildUnbanEvent;
import net.dv8tion.jda.api.events.guild.member.update.GuildMemberUpdateTimeOutEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;

public class ModerationListener extends ListenerAdapter {

	private final App bot;
	private final DBUtil db;

	public ModerationListener(App bot) {
		this.bot = bot;
		this.db = bot.getDBUtil();
	}

	@Override
	public void onGuildBan(@Nonnull GuildBanEvent event) {
		// Log
		if (!db.getLogSettings(event.getGuild()).enabled(LogType.MODERATION)) return;
		event.getGuild().retrieveAuditLogs()
			.type(ActionType.BAN)
			.limit(1)
			.queue(list -> {
				if (list.isEmpty()) return;
				AuditLogEntry entry = list.get(0);
				if (entry.getUser().equals(event.getJDA().getSelfUser())) return;  // Ignore self
				bot.getLogger().mod.onUserBan(entry, event.getUser());
			});
	}

	@Override
    public void onGuildUnban(@Nonnull GuildUnbanEvent event) {
		CaseData banData = db.cases.getMemberActive(event.getUser().getIdLong(), event.getGuild().getIdLong(), CaseType.BAN);
		if (banData != null) {
			db.cases.setInactive(banData.getCaseId());
		}
		// Log
		if (!db.getLogSettings(event.getGuild()).enabled(LogType.MODERATION)) return;
		event.getGuild().retrieveAuditLogs()
			.type(ActionType.UNBAN)
			.limit(1)
			.queue(list -> {
				if (list.isEmpty()) return;
				AuditLogEntry entry = list.get(0);
				if (entry.getUser().equals(event.getJDA().getSelfUser())) return;  // Ignore self
				bot.getLogger().mod.onUserUnban(entry, event.getUser());
			});
	}

	@Override
	public void onGuildMemberUpdateTimeOut(@Nonnull GuildMemberUpdateTimeOutEvent event) {
		if (event.getNewTimeOutEnd() == null) {
			// timeout removed by moderator
			CaseData timeoutData = db.cases.getMemberActive(event.getUser().getIdLong(), event.getGuild().getIdLong(), CaseType.MUTE);
			if (timeoutData != null) {
				db.cases.setInactive(timeoutData.getCaseId());
			}
			// Log removal
			if (!db.getLogSettings(event.getGuild()).enabled(LogType.MODERATION)) return;
			event.getGuild().retrieveAuditLogs()
				.type(ActionType.MEMBER_UPDATE)
				.limit(1)
				.queue(list -> {
					if (list.isEmpty()) return;
					AuditLogEntry entry = list.get(0);
					AuditLogChange change = entry.getChangeByKey(AuditLogKey.MEMBER_TIME_OUT);
					if (change == null) return;
					// TODO
				});
		} else {
			// Log
			if (!db.getLogSettings(event.getGuild()).enabled(LogType.MODERATION)) return;
			event.getGuild().retrieveAuditLogs()
				.type(ActionType.MEMBER_UPDATE)
				.limit(1)
				.queue(list -> {
					if (list.isEmpty()) return;
					AuditLogEntry entry = list.get(0);
					AuditLogChange change = entry.getChangeByKey(AuditLogKey.MEMBER_TIME_OUT);
					if (change == null) return;
					// TODO
				});
		}
	}

}
