package bot.listeners;

import java.util.EnumSet;
import java.util.concurrent.TimeUnit;

import javax.annotation.Nonnull;

import bot.App;
import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.events.guild.voice.GuildVoiceJoinEvent;
import net.dv8tion.jda.api.events.guild.voice.GuildVoiceLeaveEvent;
import net.dv8tion.jda.api.events.guild.voice.GuildVoiceMoveEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;

public class VoiceListener extends ListenerAdapter {
	
	private final App bot;

	public VoiceListener(App bot) {
		this.bot = bot;
	}

	@Override
	public void onGuildVoiceJoin(@Nonnull GuildVoiceJoinEvent event) {
		String voiceID = bot.getDBUtil().guildVoiceGetChannel(event.getGuild().getId());
		if (voiceID != null && voiceID.equals(event.getChannelJoined().getId())) {
			handleVoice(event.getGuild(), event.getMember());
		}
	}

	@Override
	public void onGuildVoiceMove(@Nonnull GuildVoiceMoveEvent event) {
		if (bot.getDBUtil().isVoiceChannelExists(event.getChannelLeft().getId()) && event.getChannelLeft().getMembers().isEmpty()) {
			event.getChannelLeft().delete().queueAfter(2000, TimeUnit.MILLISECONDS);
			bot.getDBUtil().channelRemove(event.getChannelLeft().getId());
		}

		String voiceID = bot.getDBUtil().guildVoiceGetChannel(event.getGuild().getId());
		if (voiceID != null && voiceID.equals(event.getChannelJoined().getId())) {
			handleVoice(event.getGuild(), event.getMember());
		}
	}

	@Override
	public void onGuildVoiceLeave(@Nonnull GuildVoiceLeaveEvent event) {
		if (bot.getDBUtil().isVoiceChannelExists(event.getChannelLeft().getId()) && event.getChannelLeft().getMembers().isEmpty()) {
			event.getChannelLeft().delete().queueAfter(500, TimeUnit.MILLISECONDS);
			bot.getDBUtil().channelRemove(event.getChannelLeft().getId());
		}
	}

	private void handleVoice(Guild guild, Member member) {
		String guildID = guild.getId();
		String userID = member.getId();

		if (bot.getDBUtil().isVoiceChannel(userID)) {
			member.getUser().openPrivateChannel()
				.queue(channel -> channel.sendMessage(bot.getMsg(guildID, "bot.voice.listener.cooldown")).queue());
			return;
		}
		String CategoryID = bot.getDBUtil().guildVoiceGetCategory(guildID);
		if (CategoryID == null) return;
		String channelName = bot.getDBUtil().userGetName(userID);
		Integer channelLimit = bot.getDBUtil().userGetLimit(userID);
		String defaultChannelName = bot.getDBUtil().guildVoiceGetName(guildID);
		Integer defaultChannelLimit = bot.getDBUtil().guildVoiceGetLimit(guildID);
		String name = null;
		Integer limit = null;
		if (channelName == null) {
			if (defaultChannelName == null) {
				name = bot.getMsg(guildID, "bot.voice.listener.default_name", member.getUser().getName(), false);
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
