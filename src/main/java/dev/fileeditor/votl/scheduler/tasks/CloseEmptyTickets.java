package dev.fileeditor.votl.scheduler.tasks;

import ch.qos.logback.classic.Logger;
import dev.fileeditor.votl.App;
import dev.fileeditor.votl.contracts.scheduler.Task;
import net.dv8tion.jda.api.entities.channel.middleman.GuildMessageChannel;
import net.dv8tion.jda.api.requests.ErrorResponse;
import org.slf4j.LoggerFactory;

public class CloseEmptyTickets implements Task {

	private static final Logger LOG = (Logger) LoggerFactory.getLogger(CloseEmptyTickets.class);

	// How many latest messages to search for author's reply
	private static final int HISTORY_LIMIT = 10;

	@Override
	public void handle(App bot) {
		bot.getDBUtil().tickets.getReplyExpiredTickets().forEach(channelId -> {
			GuildMessageChannel channel = bot.JDA.getChannelById(GuildMessageChannel.class, channelId);
			if (channel == null) {
				bot.getTicketUtil().handleMissingChannel(channelId);
				return;
			}
			Long authorId = bot.getDBUtil().tickets.getUserId(channelId);
			if (authorId == null) return;

			channel.getIterableHistory()
				.takeAsync(HISTORY_LIMIT)
				.thenAcceptAsync(list -> {
					boolean authorReplied = list.stream()
						.anyMatch(msg -> msg.getAuthor().getIdLong() == authorId);

					if (authorReplied) {
						// Author has replied - stop waiting
						bot.getDBUtil().tickets.setWaitTime(channelId, -1L);
					} else {
						// No reply from author - close ticket
						bot.getTicketUtil().closeTicket(channelId, null, "activity", failure -> {
							bot.getDBUtil().tickets.setWaitTime(channelId, -1L);
							if (ErrorResponse.UNKNOWN_MESSAGE.test(failure) || ErrorResponse.UNKNOWN_CHANNEL.test(failure)) return;
							LOG.error("Failed to delete channel {}", channelId, failure);
						});
					}
				})
				.exceptionally(failure -> {
					// Stop waiting, so it is not retried (and logged) every run
					bot.getDBUtil().tickets.setWaitTime(channelId, -1L);
					LOG.warn("Failed to check reply in ticket {}", channelId, failure);
					return null;
				});
		});
	}

}
