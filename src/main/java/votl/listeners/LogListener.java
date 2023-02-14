package votl.listeners;

import java.util.Map;
import java.util.Objects;

import votl.App;
import votl.objects.command.SlashCommandEvent;
import votl.utils.LogUtil;
import votl.utils.database.DBUtil;
import votl.utils.exception.CheckException;

import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.MessageEmbed;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.entities.Guild.Ban;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;

public class LogListener {
	
	private final App bot;
	private final DBUtil db;
	private final LogUtil logUtil;

	public LogListener(App bot) {
		this.bot = bot;
		this.db = bot.getDBUtil();
		this.logUtil = bot.getLogUtil();
	}

	public void onBan(SlashCommandEvent event, User target, Member moderator, Integer banId) {
		String guildId = Objects.requireNonNull(event.getGuild()).getId();

		String channelId = db.guild.getLogChannel(guildId);
		if (channelId == null) {
			return;
		}
		TextChannel channel = event.getJDA().getTextChannelById(channelId);
		if (channel == null) {
			return;
		}
		
		try {
			bot.getCheckUtil().hasPermissions(event, true, channel, new Permission[]{Permission.MESSAGE_SEND, Permission.MESSAGE_EMBED_LINKS});
		} catch (CheckException ex) {
			return;
		}

		Map<String, Object> ban = db.ban.getInfo(banId);
		if (ban.isEmpty()) {
			bot.getLogger().warn("That is not supposed to happen... Ban ID: %s", banId);
			return;
		}

		MessageEmbed embed = logUtil.getBanEmbed(event.getGuildLocale(), ban, true);
		
		channel.sendMessageEmbeds(embed).queue();
	}

	public void onUnban(SlashCommandEvent event, Member moderator, Ban banData, String reason) {
		String guildId = Objects.requireNonNull(event.getGuild()).getId();

		String channelId = db.guild.getLogChannel(guildId);
		if (channelId == null) {
			return;
		}
		TextChannel channel = event.getJDA().getTextChannelById(channelId);
		if (channel == null) {
			return;
		}

		try {
			bot.getCheckUtil().hasPermissions(event, true, channel, new Permission[]{Permission.MESSAGE_SEND, Permission.MESSAGE_EMBED_LINKS});
		} catch (CheckException ex) {
			return;
		}

		MessageEmbed embed = logUtil.getUnbanEmbed(event.getUserLocale(), banData, moderator, reason);

		channel.sendMessageEmbeds(embed).queue();
	}

	// To be done
	public void onAutoUnban(SlashCommandEvent event, User target, Integer banId) {}

	public void onSyncBan(SlashCommandEvent event, User target) {}

	public void onSyncUnban(SlashCommandEvent event, User target) {}

}
