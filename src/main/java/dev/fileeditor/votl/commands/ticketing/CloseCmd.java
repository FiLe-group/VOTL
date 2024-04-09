package dev.fileeditor.votl.commands.ticketing;

import dev.fileeditor.votl.App;
import dev.fileeditor.votl.base.command.SlashCommandEvent;
import dev.fileeditor.votl.commands.CommandBase;
import dev.fileeditor.votl.objects.CmdModule;
import dev.fileeditor.votl.objects.constants.CmdCategory;
import dev.fileeditor.votl.objects.constants.Constants;

public class CloseCmd extends CommandBase {

	public CloseCmd(App bot) {
		super(bot);
		this.name = "close";
		this.path = "bot.ticketing.close";
		this.module = CmdModule.TICKETING;
		this.category = CmdCategory.TICKETING;
	}

	@Override
	protected void execute(SlashCommandEvent event) {
		long channelId = event.getChannel().getIdLong();
		Long authorId = bot.getDBUtil().tickets.getUserId(channelId);
		if (authorId == null) {
			// If this channel is not a ticket
			createError(event, path+".not_ticket");
			return;
		}
		if (!bot.getDBUtil().tickets.isOpened(channelId)) {
			// Ticket is closed
			event.getChannel().delete().queue();
			return;
		}
		String reason = bot.getDBUtil().tickets.getUserId(channelId).equals(event.getUser().getIdLong()) ? "Closed by ticket's author" : "Closed by Support";
		event.replyEmbeds(bot.getEmbedUtil().getEmbed(Constants.COLOR_SUCCESS)
			.setDescription(lu.getLocalized(event.getGuildLocale(), "bot.ticketing.listener.delete_countdown"))
			.build()
		).queue(hook -> {	
			bot.getTicketUtil().closeTicket(channelId, event.getUser(), reason, failure -> {
				hook.editOriginalEmbeds(bot.getEmbedUtil().getError(event, "bot.ticketing.listener.close_failed")).queue();
				bot.getAppLogger().error("Couldn't close ticket with channelID:"+channelId, failure);
			});
		});
	}

}
