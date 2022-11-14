package votl.listeners;

import java.util.EnumSet;
import java.util.concurrent.TimeUnit;

import javax.annotation.Nonnull;

import votl.App;
import votl.utils.message.LocaleUtil;

import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.channel.unions.AudioChannelUnion;
import net.dv8tion.jda.api.events.guild.voice.GuildVoiceUpdateEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import net.dv8tion.jda.api.interactions.DiscordLocale;

public class VoiceListener extends ListenerAdapter {
	
	private final App bot;
	private final LocaleUtil lu;

	public VoiceListener(App bot) {
		this.bot = bot;
		this.lu = bot.getLocaleUtil();
	}

	public void onGuildVoiceUpdate(@Nonnull GuildVoiceUpdateEvent event) {
		String masterVoiceID = bot.getDBUtil().guildVoiceGetChannel(event.getGuild().getId());
		AudioChannelUnion channelJoined = event.getChannelJoined();
		if (channelJoined != null && channelJoined.getId().equals(masterVoiceID)) {
			handleVoiceCreate(event.getGuild(), event.getMember());
		}

		AudioChannelUnion channelLeft = event.getChannelLeft();
		if (channelLeft != null && bot.getDBUtil().isVoiceChannelExists(channelLeft.getId()) && channelLeft.getMembers().isEmpty()) {
			channelLeft.delete().queueAfter(500, TimeUnit.MILLISECONDS);
			bot.getDBUtil().channelRemove(channelLeft.getId());
		}
	}

	private void handleVoiceCreate(Guild guild, Member member) {
		String guildId = guild.getId();
		String userID = member.getId();
		DiscordLocale guildLocale = guild.getLocale();

		if (bot.getDBUtil().isVoiceChannel(userID)) {
			member.getUser().openPrivateChannel()
				.queue(channel -> channel.sendMessage(lu.getLocalized(guildLocale, "bot.voice.listener.cooldown")).queue());
			return;
		}
		String CategoryID = bot.getDBUtil().guildVoiceGetCategory(guildId);
		if (CategoryID == null) return;
		String channelName = bot.getDBUtil().userGetName(userID);
		Integer channelLimit = bot.getDBUtil().userGetLimit(userID);
		String defaultChannelName = bot.getDBUtil().guildVoiceGetName(guildId);
		Integer defaultChannelLimit = bot.getDBUtil().guildVoiceGetLimit(guildId);
		String name = null;
		Integer limit = null;
		if (channelName == null) {
			if (defaultChannelName == null) {
				name = lu.getLocalized(guildLocale, "bot.voice.listener.default_name", member.getUser().getName(), false);
			} else {
				name = defaultChannelName;
			}
		} else {
			name = channelName;
		}
		if (channelLimit == null) {
			if (defaultChannelLimit == null) {
				limit = 0;
			} else {
				limit = defaultChannelLimit;
			}
		} else {
			limit = channelLimit;
		}
		guild.createVoiceChannel(name, guild.getCategoryById(CategoryID))
			.setUserlimit(limit)
			.syncPermissionOverrides()
			.addMemberPermissionOverride(member.getIdLong(), EnumSet.of(Permission.MANAGE_CHANNEL), null)
			.queue(
				channel -> {
					bot.getDBUtil().channelAdd(userID, channel.getId());
					guild.moveVoiceMember(member, channel).queueAfter(500, TimeUnit.MICROSECONDS);
				}
			);
	}
}
