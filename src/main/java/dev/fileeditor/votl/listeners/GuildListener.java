package dev.fileeditor.votl.listeners;

import ch.qos.logback.classic.Logger;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.events.guild.GuildJoinEvent;
import net.dv8tion.jda.api.events.guild.GuildLeaveEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;

import dev.fileeditor.votl.App;
import dev.fileeditor.votl.utils.database.DBUtil;
import org.jetbrains.annotations.NotNull;
import org.slf4j.LoggerFactory;

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
		if (bot.getCheckUtil().isBlacklisted(guild)) {
			guild.leave().queue();
			log.info("Auto-left new guild '{}'({}) BLACKLIST!", guild.getName(), guild.getId());
		} else {
			log.info("Joined guild '{}'({})", guild.getName(), guild.getId());
		}
	}

	@Override
	public void onGuildLeave(@NotNull GuildLeaveEvent event) {
		long guildId = event.getGuild().getIdLong();
		log.info("Left guild '{}'({})", event.getGuild().getName(), guildId);

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
		db.logExemptions.removeGuild(guildId);
		db.modifyRole.removeAll(guildId);
		db.games.removeGuild(guildId);
		db.persistent.removeGuild(guildId);
		
		db.guildSettings.remove(guildId);

		log.info("Automatically removed guild '{}'({}) from db.", event.getGuild().getName(), guildId);
	}
}
