package votl.listeners;

import java.util.Map;

import javax.annotation.Nonnull;

import votl.App;
import votl.utils.database.DBUtil;

import net.dv8tion.jda.api.entities.MessageEmbed;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import net.dv8tion.jda.api.events.guild.GuildJoinEvent;
import net.dv8tion.jda.api.events.guild.GuildLeaveEvent;
import net.dv8tion.jda.api.events.guild.GuildUnbanEvent;
import net.dv8tion.jda.api.events.guild.member.GuildMemberRemoveEvent;
import net.dv8tion.jda.api.exceptions.InsufficientPermissionException;
import net.dv8tion.jda.api.hooks.ListenerAdapter;

public class GuildListener extends ListenerAdapter {

	private final App bot;
	private final DBUtil db;

	public GuildListener(App bot) {
		this.bot = bot;
		this.db = bot.getDBUtil();
	}

	@Override
	public void onGuildJoin(@Nonnull GuildJoinEvent event) {
		String guildId = event.getGuild().getId();
		// check if not exists in DB and adds it
		if (db.guild.add(guildId)) {
			bot.getLogger().info("Automatically added guild '"+event.getGuild().getName()+"'("+guildId+") to db");
		}
		bot.getLogger().info("Joined guild '"+event.getGuild().getName()+"'("+guildId+")");
	}

	@Override
	public void onGuildLeave(@Nonnull GuildLeaveEvent event) {
		String guildId = event.getGuild().getId();

		// Deletes every information connected to this guild from bot's DB (except ban tables)
		// May be dangerous, but provides privacy
		for (Integer groupId : db.group.getGuildGroups(guildId)) {
			String groupName = db.group.getName(groupId);
			String guildName = event.getGuild().getName();
			String masterId = db.group.getMaster(groupId);
			String masterIcon = event.getJDA().getGuildById(masterId).getIconUrl();

			String masterChannelId = db.guild.getGroupLogChannel(masterId);
			if (masterChannelId != null) {
				TextChannel channel = event.getJDA().getTextChannelById(masterChannelId);
				if (channel != null) {
					try {
						MessageEmbed masterEmbed = bot.getLogUtil().getGroupLeaveMasterEmbed(channel.getGuild().getLocale(), masterId, masterIcon, guildName, guildId, groupId, groupName);
						channel.sendMessageEmbeds(masterEmbed).queue();
					} catch (InsufficientPermissionException ex) {}
				}
			}
		}
		for (Map<String,Object> group : db.group.getMasterGroups(guildId)) {
			Integer groupId = (Integer) group.get("groupId");
			String groupName = (String) group.get("name");
			for (String gid : db.group.getGroupGuildIds(groupId)) {
				String channelId = db.guild.getGroupLogChannel(gid);
				if (channelId == null) {
					continue;
				}
				TextChannel channel = event.getJDA().getTextChannelById(channelId);
				if (channel == null) {
					continue;
				}

				try {
					MessageEmbed embed = bot.getLogUtil().getGroupDeletedEmbed(channel.getGuild().getLocale(), guildId, event.getGuild().getIconUrl(), groupId, groupName);
					channel.sendMessageEmbeds(embed).queue();
				} catch (InsufficientPermissionException ex) {
					continue;
				}
			}
			db.group.clearGroup(groupId);
		}
		db.group.removeFromGroups(guildId);
		db.group.deleteAll(guildId);
		
		db.access.removeAll(guildId);
		db.module.removeAll(guildId);
		db.webhook.removeAll(guildId);
		db.verify.clearGuild(guildId);
		db.verify.remove(guildId);
		if (db.guild.exists(guildId) || db.guildVoice.exists(guildId)) {
			db.guild.remove(guildId);
		}

		bot.getLogger().info("Automatically removed guild '"+event.getGuild().getName()+"'("+guildId+") from db.");
		bot.getLogger().info("Left guild '"+event.getGuild().getName()+"'("+guildId+")");
	}

	@Override
	public void onGuildUnban(@Nonnull GuildUnbanEvent event) {
		Map<String, Object> banData = db.ban.getMemberExpirable(event.getUser().getId(), event.getGuild().getId());
		if (!banData.isEmpty()) {
			db.ban.setInactive(Integer.valueOf(banData.get("badId").toString()));
		}
	}

	@Override
	public void onGuildMemberRemove(@Nonnull GuildMemberRemoveEvent event) {
		// When user leaves guild, check if there are any records in DB that would be better to remove.
		// This does not consider clearing User DB, when bot leaves guild.
		String guildId = event.getGuild().getId();
		String userId = event.getUser().getId();

		db.access.remove(guildId, userId);
		if (event.getUser().getMutualGuilds().size() == 0) {
			db.user.remove(userId);
		}
	}
}
