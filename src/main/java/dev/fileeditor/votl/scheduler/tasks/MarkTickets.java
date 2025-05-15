package dev.fileeditor.votl.scheduler.tasks;

import dev.fileeditor.votl.App;
import dev.fileeditor.votl.contracts.scheduler.Task;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.MessageEmbed;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.entities.UserSnowflake;
import net.dv8tion.jda.api.entities.channel.middleman.GuildMessageChannel;
import net.dv8tion.jda.api.interactions.components.buttons.Button;
import net.dv8tion.jda.api.utils.TimeFormat;
import net.dv8tion.jda.api.utils.TimeUtil;

import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;

public class MarkTickets implements Task {

	private final Integer CLOSE_AFTER_HOURS = 12;

	@Override
	public void handle(App bot) {
		bot.getDBUtil().tickets.getOpenedChannels().forEach(channelId -> {
			GuildMessageChannel channel = bot.JDA.getChannelById(GuildMessageChannel.class, channelId);
			if (channel == null) {
				bot.getDBUtil().tickets.forceCloseTicket(channelId);
				return;
			}

			Guild guild = channel.getGuild();
			Duration autocloseTime = bot.getDBUtil().getTicketSettings(guild).getAutocloseTime();
			if (autocloseTime.isZero()) return;

			if (TimeUtil.getTimeCreated(channel.getLatestMessageIdLong()).isBefore(Instant.now().minus(autocloseTime).atOffset(ZoneOffset.UTC))) {
				UserSnowflake user = User.fromId(bot.getDBUtil().tickets.getUserId(channelId));
				Instant closeTime = Instant.now().plus(CLOSE_AFTER_HOURS, ChronoUnit.HOURS);

				MessageEmbed embed = new EmbedBuilder()
					.setColor(bot.getDBUtil().getGuildSettings(guild).getColor())
					.setDescription(bot.getLocaleUtil().getLocalized(guild.getLocale(), "bot.ticketing.listener.close_auto")
						.replace("{user}", user.getAsMention())
						.replace("{time}", TimeFormat.RELATIVE.format(closeTime))
					)
					.build();

				Button close = Button.primary("ticket:close", bot.getLocaleUtil().getLocalized(guild.getLocale(), "ticket.close"));
				Button cancel = Button.secondary("ticket:cancel", bot.getLocaleUtil().getLocalized(guild.getLocale(), "ticket.cancel"));

				bot.getDBUtil().tickets.setRequestStatus(channelId, closeTime.getEpochSecond());
				channel.sendMessage("||%s||".formatted(user.getAsMention())).addEmbeds(embed).addActionRow(close, cancel).queue();
			}
		});
	}

}
