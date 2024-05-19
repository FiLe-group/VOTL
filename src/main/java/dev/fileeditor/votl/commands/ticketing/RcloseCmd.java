package dev.fileeditor.votl.commands.ticketing;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;

import dev.fileeditor.votl.App;
import dev.fileeditor.votl.base.command.SlashCommandEvent;
import dev.fileeditor.votl.commands.CommandBase;
import dev.fileeditor.votl.objects.CmdAccessLevel;
import dev.fileeditor.votl.objects.CmdModule;
import dev.fileeditor.votl.objects.constants.CmdCategory;

import dev.fileeditor.votl.utils.message.TimeUtil;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.MessageEmbed;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.entities.UserSnowflake;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import net.dv8tion.jda.api.interactions.components.buttons.Button;

public class RcloseCmd extends CommandBase {
	
	public RcloseCmd(App bot) {
		super(bot);
		this.name = "rclose";
		this.path = "bot.ticketing.rclose";
		this.options = List.of(
			new OptionData(OptionType.STRING, "reason", lu.getText(path+".reason.help")).setMaxLength(200)
		);
		this.module = CmdModule.TICKETING;
		this.category = CmdCategory.TICKETING;
		this.accessLevel = CmdAccessLevel.HELPER;
	}

	private final int CLOSE_AFTER_DELAY = 12; // hours

	@Override
	protected void execute(SlashCommandEvent event) {
		long channelId = event.getChannel().getIdLong();
		Long authorId = bot.getDBUtil().tickets.getUserId(channelId);
		if (authorId == null) {
			// If this channel is not a ticket
			createError(event, path+".not_ticket");
			return;
		}
		if (bot.getDBUtil().tickets.isClosed(channelId)) {
			// Ticket is closed
			event.getChannel().delete().queue();
			return;
		}
		if (bot.getDBUtil().tickets.getTimeClosing(channelId) > 0) {
			// If request already exists (if there is no cancel button - GG)
			createError(event, path+".already_requested");
			return;
		}

		event.deferReply().queue();
		
		Guild guild = event.getGuild();
		UserSnowflake user = User.fromId(bot.getDBUtil().tickets.getUserId(channelId));
		Instant closeTime = Instant.now().plus(CLOSE_AFTER_DELAY, ChronoUnit.HOURS);

		MessageEmbed embed = new EmbedBuilder()
			.setColor(bot.getDBUtil().getGuildSettings(guild).getColor())
			.setDescription(bot.getLocaleUtil().getLocalized(guild.getLocale(), "bot.ticketing.listener.close_request")
				.replace("{user}", user.getAsMention())
				.replace("{time}", TimeUtil.formatTime(closeTime, false)))
			.build();

		Button close = Button.primary("ticket:close", bot.getLocaleUtil().getLocalized(guild.getLocale(), "ticket.close"));
		Button cancel = Button.secondary("ticket:cancel", bot.getLocaleUtil().getLocalized(guild.getLocale(), "ticket.cancel"));
		
		event.getHook().editOriginal("||%s||".formatted(user.getAsMention())).setEmbeds(embed).setActionRow(close, cancel).queue();
		bot.getDBUtil().tickets.setRequestStatus(channelId, closeTime.getEpochSecond());
	}

}
