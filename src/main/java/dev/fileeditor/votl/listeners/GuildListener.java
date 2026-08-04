package dev.fileeditor.votl.listeners;

import ch.qos.logback.classic.Logger;
import dev.fileeditor.votl.utils.ConsoleColor;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.channel.ChannelType;
import net.dv8tion.jda.api.events.channel.ChannelDeleteEvent;
import net.dv8tion.jda.api.events.guild.GuildJoinEvent;
import net.dv8tion.jda.api.events.guild.GuildLeaveEvent;
import net.dv8tion.jda.api.events.guild.invite.GuildInviteCreateEvent;
import net.dv8tion.jda.api.events.guild.invite.GuildInviteDeleteEvent;
import net.dv8tion.jda.api.events.role.RoleDeleteEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;

import dev.fileeditor.votl.App;
import dev.fileeditor.votl.utils.database.DBUtil;
import dev.fileeditor.votl.utils.database.managers.GuildSettingsManager.GuildSettings;
import dev.fileeditor.votl.utils.database.managers.GuildVoiceManager.VoiceSettings;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.slf4j.LoggerFactory;

import java.sql.SQLException;

public class GuildListener extends ListenerAdapter {

	private final Logger log = (Logger) LoggerFactory.getLogger(GuildListener.class);

	private final App bot;
	private final DBUtil db;

	public GuildListener(App bot) {
		this.bot = bot;
		this.db = bot.getDBUtil();
	}

	@Override
	public void onGuildJoin(@NotNull GuildJoinEvent event) {
		Guild guild = event.getGuild();
		if (bot.getBlacklist().isBlacklisted(guild)) {
			guild.leave().queue();
			log.info(ConsoleColor.format("%redAuto-left new guild '{}'({}) BLACKLIST!%reset"), guild.getName(), guild.getId());
		} else {
			log.info(ConsoleColor.format("%greenJoined guild '{}'({})%reset"), guild.getName(), guild.getId());
			if (db.getGuildSettings(guild).isInviteTrackerEnabled()) {
				bot.getInviteTracker().prime(guild, 0);
			}
		}
	}

	@Override
	public void onGuildInviteCreate(@NotNull GuildInviteCreateEvent event) {
		bot.getInviteTracker().onInviteCreate(event);
	}

	@Override
	public void onGuildInviteDelete(@NotNull GuildInviteDeleteEvent event) {
		bot.getInviteTracker().onInviteDelete(event.getGuild().getIdLong(), event.getCode());
	}

	@Override
	public void onGuildLeave(@NotNull GuildLeaveEvent event) {
		long guildId = event.getGuild().getIdLong();
		log.info(ConsoleColor.format("%redLeft guild '{}'({})%reset"), event.getGuild().getName(), guildId);

		// Deletes every information connected to this guild from bot's DB (except ban tables)
		// May be dangerous, but provides privacy
		for (Integer groupId : db.group.getGuildGroups(guildId)) {
			try {
				bot.getGuildLogger().group.onGuildLeft(event.getGuild(), groupId);
			} catch (Exception ignored) {}
		}
		String ownerIcon = event.getGuild().getIconUrl();
		for (Integer groupId : db.group.getOwnedGroups(guildId)) {
			try {
				bot.getGuildLogger().group.onDeletion(guildId, ownerIcon, groupId);
				db.group.clearGroup(groupId);
			} catch (Exception ignored) {}
		}
		ignoreExc(() -> db.group.removeGuildFromGroups(guildId));
		ignoreExc(() -> db.group.deleteGuildGroups(guildId));

		ignoreExc(() -> db.accessGroups.removeGuild(guildId));
		ignoreExc(() -> db.webhook.removeAll(guildId));
		ignoreExc(() -> db.verifySettings.remove(guildId));
		ignoreExc(() -> db.ticketSettings.remove(guildId));
		ignoreExc(() -> db.roles.removeAll(guildId));
		ignoreExc(() -> db.guildVoice.remove(guildId));
		ignoreExc(() -> db.ticketPanels.deleteAll(guildId));
		ignoreExc(() -> db.ticketTags.deleteAll(guildId));
		ignoreExc(() -> db.tempRoles.removeAll(guildId));
		ignoreExc(() -> db.autopunish.removeGuild(guildId));
		ignoreExc(() -> db.strikes.removeGuild(guildId));
		ignoreExc(() -> db.logs.removeGuild(guildId));
		ignoreExc(() -> db.logExemptions.removeGuild(guildId));
		ignoreExc(() -> db.modifyRole.removeAll(guildId));
		ignoreExc(() -> db.games.removeGuild(guildId));
		ignoreExc(() -> db.persistent.removeGuild(guildId));
		ignoreExc(() -> db.modReport.removeGuild(guildId));
		ignoreExc(() -> db.customRoles.removeGuild(guildId));
		ignoreExc(() -> db.customRoleSettings.removeGuild(guildId));
		ignoreExc(() -> db.customRoleRequests.removeGuild(guildId));
		ignoreExc(() -> db.customRoleAccess.removeGuild(guildId));
		ignoreExc(() -> db.mediaChannels.removeGuild(guildId));
		ignoreExc(() -> db.autoRole.removeGuild(guildId));
		ignoreExc(() -> db.rankRoles.removeGuild(guildId));
		ignoreExc(() -> bot.getInviteTracker().remove(guildId));

		ignoreExc(() -> db.guildSettings.remove(guildId));

		log.info("Automatically removed guild '{}'({}) from db.", event.getGuild().getName(), guildId);
	}

	@Override
	public void onChannelDelete(@NotNull ChannelDeleteEvent event) {
		if (!event.isFromGuild()) return;

		long guildId = event.getGuild().getIdLong();
		long channelId = event.getChannel().getIdLong();

		// Removes every reference to this channel from bot's DB
		ignoreExc(() -> db.logExemptions.removeExemption(guildId, channelId));
		ignoreExc(() -> db.logs.removeChannel(guildId, channelId));
		ignoreExc(() -> db.mediaChannels.removeChannel(guildId, channelId));
		ignoreExc(() -> db.games.removeChannel(channelId));
		ignoreExc(() -> db.modReport.removeChannel(channelId));
		ignoreExc(() -> db.levels.removeExemptChannel(guildId, channelId));
		db.voice.remove(channelId);
		db.tickets.forceCloseTicket(channelId);

		GuildSettings guildSettings = db.getGuildSettings(guildId);
		if (matches(guildSettings.getReportChannelId(), channelId))
			ignoreExc(() -> db.guildSettings.setReportChannelId(guildId, null));
		if (matches(guildSettings.getDramaChannelId(), channelId))
			ignoreExc(() -> db.guildSettings.setDramaChannelId(guildId, null));

		if (matches(db.customRoleSettings.getSettings(guildId).getRequestsChannelId(), channelId))
			ignoreExc(() -> db.customRoleSettings.setRequestsChannel(guildId, null));

		VoiceSettings voiceSettings = db.guildVoice.getSettings(guildId);
		if (matches(voiceSettings.getChannelId(), channelId))
			ignoreExc(() -> db.guildVoice.removeChannel(guildId));

		if (event.getChannelType() == ChannelType.CATEGORY) {
			// Category deletion does not delete its children, only unsets their parent
			if (matches(voiceSettings.getCategoryId(), channelId))
				ignoreExc(() -> db.guildVoice.removeCategory(guildId));
			ignoreExc(() -> db.ticketTags.clearLocation(channelId));
		}
	}

	private boolean matches(@Nullable Long settingId, long channelId) {
		return settingId != null && settingId == channelId;
	}

	@Override
	public void onRoleDelete(@NotNull RoleDeleteEvent event) {
		long guildId = event.getGuild().getIdLong();
		long roleId = event.getRole().getIdLong();

		ignoreExc(() -> db.autoRole.removeRole(guildId, roleId));
		ignoreExc(() -> db.accessGroups.removeRoleFromAll(roleId));
		ignoreExc(() -> db.rankRoles.removeRoleFromAll(roleId));
		ignoreExc(() -> db.customRoles.remove(roleId));
		ignoreExc(() -> db.persistent.removeRole(guildId, roleId));
		ignoreExc(() -> db.roles.remove(roleId));
		ignoreExc(() -> db.tempRoles.removeRole(roleId));
	}

	private void ignoreExc(RunnableExc runnable) {
		try {
			runnable.run();
		} catch (SQLException ignored) {}
	}

	@FunctionalInterface public interface RunnableExc { void run() throws SQLException; }
}
