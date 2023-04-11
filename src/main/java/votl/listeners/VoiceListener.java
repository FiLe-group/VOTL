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
		String masterVoiceID = bot.getDBUtil().guildVoice.getChannel(event.getGuild().getId());
		AudioChannelUnion channelJoined = event.getChannelJoined();
		if (channelJoined != null && channelJoined.getId().equals(masterVoiceID)) {
			handleVoiceCreate(event.getGuild(), event.getMember());
		}

		AudioChannelUnion channelLeft = event.getChannelLeft();
		if (channelLeft != null && bot.getDBUtil().voice.existsChannel(channelLeft.getId()) && channelLeft.getMembers().isEmpty()) {
			channelLeft.delete().reason("Custom channel, empty").queueAfter(500, TimeUnit.MILLISECONDS);
			bot.getDBUtil().voice.remove(channelLeft.getId());
		}
	}

	private void handleVoiceCreate(Guild guild, Member member) {
		String guildId = guild.getId();
		String userId = member.getId();
		DiscordLocale guildLocale = guild.getLocale();

		if (bot.getDBUtil().voice.existsUser(userId)) {
			member.getUser().openPrivateChannel()
				.queue(channel -> channel.sendMessage(lu.getLocalized(guildLocale, "bot.voice.listener.cooldown")).queue());
			return;
		}
		String CategoryID = bot.getDBUtil().guildVoice.getCategory(guildId);
		if (CategoryID == null) return;
		String channelName = bot.getDBUtil().user.getName(userId);
		Integer channelLimit = bot.getDBUtil().user.getLimit(userId);
		String defaultChannelName = bot.getDBUtil().guildVoice.getName(guildId);
		Integer defaultChannelLimit = bot.getDBUtil().guildVoice.getLimit(guildId);
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
			.reason(member.getUser().getAsTag()+" custom channel")
			.setUserlimit(limit)
			.syncPermissionOverrides()
			.addPermissionOverride(member, EnumSet.of(Permission.MANAGE_CHANNEL), null)
			.queue(
				channel -> {
					bot.getDBUtil().voice.add(userId, channel.getId());
					guild.moveVoiceMember(member, channel).queueAfter(500, TimeUnit.MICROSECONDS);
				}
			);
	}
}
