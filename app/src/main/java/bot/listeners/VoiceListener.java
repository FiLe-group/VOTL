package bot.listeners;

import java.util.EnumSet;
import java.util.concurrent.TimeUnit;

import bot.App;
import net.dv8tion.jda.api.Permission;
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
	public void onGuildVoiceJoin(GuildVoiceJoinEvent event) {
		String guildID = event.getGuild().getId();
		String voiceID = bot.getDBUtil().guildVoiceGetChannel(guildID);
		
		if (voiceID != null && voiceID.equals(event.getChannelJoined().getId())) {
			if (bot.getDBUtil().isVoiceChannel(event.getMember().getId())) {
				event.getMember().getUser().openPrivateChannel()
					.queue(channel -> channel.sendMessage(bot.getMsg(event.getGuild().getId(), "bot.voice.listener.cooldown")).queue());
				return;
			}
			String CategoryID = bot.getDBUtil().guildVoiceGetCategory(guildID);
			if (CategoryID == null) return;
			String channelName = bot.getDBUtil().userGetName(guildID);
			Integer channelLimit = bot.getDBUtil().userGetLimit(guildID);
			String defaultChannelName = bot.getDBUtil().guildVoiceGetName(guildID);
			Integer defaultChannelLimit = bot.getDBUtil().guildVoiceGetLimit(guildID);
			String name = null;
			Integer limit = null;
			if (channelName == null) {
				if (defaultChannelName == null) {
					name = bot.getMsg(event.getGuild().getId(), "bot.voice.listener.default_name", event.getMember().getUser().getName(), false);
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
			event.getGuild().createVoiceChannel(name, event.getGuild().getCategoryById(CategoryID))
				.setUserlimit(limit)
				.syncPermissionOverrides()
				.addMemberPermissionOverride(event.getMember().getIdLong(), EnumSet.of(Permission.MANAGE_CHANNEL), null)
				.queue(
					channel -> {
						bot.getDBUtil().channelAdd(event.getMember().getId(), channel.getId());
						event.getGuild().moveVoiceMember(event.getMember(), channel).queueAfter(500, TimeUnit.MICROSECONDS);
					}
				);
		}
	}

	@Override
	public void onGuildVoiceMove(GuildVoiceMoveEvent event) {
		if (bot.getDBUtil().isVoiceChannelExists(event.getChannelLeft().getId()) && event.getChannelLeft().getMembers().isEmpty()) {
			event.getChannelLeft().delete().queue();
			bot.getDBUtil().channelRemove(event.getChannelLeft().getId());
		}
	}

	@Override
	public void onGuildVoiceLeave(GuildVoiceLeaveEvent event) {
		if (bot.getDBUtil().isVoiceChannelExists(event.getChannelLeft().getId()) && event.getChannelLeft().getMembers().isEmpty()) {
			event.getChannelLeft().delete().queue();
			bot.getDBUtil().channelRemove(event.getChannelLeft().getId());
		}
	}
}
