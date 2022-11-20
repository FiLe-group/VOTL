package votl.listeners;

import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.Objects;

import votl.App;
import votl.objects.command.SlashCommandEvent;
import votl.objects.constants.Constants;
import votl.utils.database.DBUtil;
import votl.utils.exception.CheckException;
import votl.utils.message.LocaleUtil;

import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.MessageEmbed;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;

public class LogListener {
	
	private final App bot;
	private final LocaleUtil lu;
	private final DBUtil db;

	public LogListener(App bot) {
		this.bot = bot;
		this.lu = bot.getLocaleUtil();
		this.db = bot.getDBUtil();
	}

	@SuppressWarnings("null")
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

		Map<String, Object> ban = db.ban.getInfo(banId.toString());
		if (ban.isEmpty()) {
			bot.getLogger().warn("That is not supposed to happen... Ban ID: %s", banId);
			return;
		}
		Instant start = Timestamp.valueOf(ban.get("timeStart").toString()).toInstant();
		Duration duration = Duration.parse(ban.get("duration").toString());
		
		MessageEmbed embed = bot.getEmbedUtil().getEmbed().setColor(Constants.COLOR_FAILURE)
			.setAuthor("Case "+banId+" | Ban | "+target.getAsTag(), null, target.getAvatarUrl())
			.addField("User", target.getAsMention(), true)
			.addField("Moderator", moderator.getAsMention(), true)
			.addField("Duration", duration.isZero() ? lu.getText(event, "bot.moderation.case.permanently") : 
				lu.getText(event, "bot.moderation.case.temporary")
					.replace("{time}", bot.getMessageUtil().formatTime(start.plus(duration), false)), true)
			.addField("Reason", ban.get("reason").toString(), true)
			.setFooter("ID: "+target.getId())
			.setTimestamp(start)
			.build();
		
		channel.sendMessageEmbeds(embed).queue();
	}

}
