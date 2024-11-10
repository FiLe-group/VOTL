package dev.fileeditor.votl.commands.ticketing;

import java.util.List;

import dev.fileeditor.votl.base.command.SlashCommandEvent;
import dev.fileeditor.votl.commands.CommandBase;
import dev.fileeditor.votl.objects.CmdModule;
import dev.fileeditor.votl.objects.constants.CmdCategory;
import dev.fileeditor.votl.objects.constants.Constants;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;

public class CloseCmd extends CommandBase {

	public CloseCmd() {
		this.name = "close";
		this.path = "bot.ticketing.close";
		this.options = List.of(
			new OptionData(OptionType.STRING, "reason", lu.getText(path+".reason.help")).setMaxLength(200)
		);
		this.module = CmdModule.TICKETING;
		this.category = CmdCategory.TICKETING;
	}

	@Override
	protected void execute(SlashCommandEvent event) {
		event.deferReply().queue();
		long channelId = event.getChannel().getIdLong();
		Long authorId = bot.getDBUtil().tickets.getUserId(channelId);
		if (authorId == null) {
			// If this channel is not a ticket
			editError(event, path+".not_ticket");
			return;
		}
		if (bot.getDBUtil().tickets.isClosed(channelId)) {
			// Ticket is closed
			event.getChannel().delete().queue();
			return;
		}

		String reason = event.optString(
			"reason",
			bot.getDBUtil().tickets.getUserId(channelId).equals(event.getUser().getIdLong())
				? lu.getLocalized(event.getGuildLocale(), "bot.ticketing.listener.closed_author")
				: lu.getLocalized(event.getGuildLocale(), "bot.ticketing.listener.closed_support")
		);

		event.getHook().editOriginalEmbeds(bot.getEmbedUtil().getEmbed(Constants.COLOR_SUCCESS)
			.setDescription(lu.getLocalized(event.getGuildLocale(), "bot.ticketing.listener.delete_countdown"))
			.build()
		).queue(msg -> {
			bot.getTicketUtil().closeTicket(channelId, event.getUser(), reason, failure -> {
				msg.editMessageEmbeds(bot.getEmbedUtil().getError(event, "bot.ticketing.listener.close_failed")).queue();
				bot.getAppLogger().error("Couldn't close ticket with channelID:{}", channelId, failure);
			});
		});
	}

}
