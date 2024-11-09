package dev.fileeditor.votl.listeners;

import net.dv8tion.jda.api.events.guild.GuildJoinEvent;
import net.dv8tion.jda.api.events.guild.GuildLeaveEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;

import dev.fileeditor.votl.App;
import dev.fileeditor.votl.utils.database.DBUtil;
import org.jetbrains.annotations.NotNull;

public class GuildListener extends ListenerAdapter {

	private final App bot;
	private final DBUtil db;

	public GuildListener(App bot) {
		this.bot = bot;
		this.db = bot.getDBUtil();
	}

	@Override
	public void onGuildJoin(@NotNull GuildJoinEvent event) {
		String guildId = event.getGuild().getId();
		bot.getAppLogger().info("Joined guild '{}'({})", event.getGuild().getName(), guildId);
	}

	@Override
	public void onGuildLeave(@NotNull GuildLeaveEvent event) {
		long guildId = event.getGuild().getIdLong();
		bot.getAppLogger().info("Left guild '%s'(%s)".formatted(event.getGuild().getName(), guildId));

		// Deletes every information connected to this guild from bot's DB (except ban tables)
		// May be dangerous, but provides privacy
		for (Integer groupId : db.group.getGuildGroups(guildId)) {
			try {
				bot.getLogger().group.onGuildLeft(event.getGuild(), groupId);
			} catch (Exception ignored) {}
		}
		String ownerIcon = event.getGuild().getIconUrl();
		for (Integer groupId : db.group.getOwnedGroups(guildId)) {
			try {
				bot.getLogger().group.onDeletion(guildId, ownerIcon, groupId);
			} catch (Exception ignored) {}
			db.group.clearGroup(groupId);
		}
		db.group.removeGuildFromGroups(guildId);
		db.group.deleteGuildGroups(guildId);

		db.access.removeAll(guildId);
		db.webhook.removeAll(guildId);
		db.verifySettings.remove(guildId);
		db.ticketSettings.remove(guildId);
		db.roles.removeAll(guildId);
		db.guildVoice.remove(guildId);
		db.ticketPanels.deleteAll(guildId);
		db.ticketTags.deleteAll(guildId);
		db.tempRoles.removeAll(guildId);
		db.autopunish.removeGuild(guildId);
		db.strikes.removeGuild(guildId);
		db.logs.removeGuild(guildId);
		db.logExceptions.removeGuild(guildId);
		db.modifyRole.removeAll(guildId);
		db.games.removeGuild(guildId);
		db.persistent.removeGuild(guildId);
		
		db.guildSettings.remove(guildId);

		bot.getAppLogger().info("Automatically removed guild '%s'(%s) from db.".formatted(event.getGuild().getName(), guildId));
	}
}
