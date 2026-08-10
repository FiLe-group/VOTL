package dev.fileeditor.votl.scheduler.tasks;

import ch.qos.logback.classic.Logger;
import dev.fileeditor.votl.App;
import dev.fileeditor.votl.contracts.scheduler.Task;
import dev.fileeditor.votl.utils.database.managers.RepeatMessageManager.RepeatMessage;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import org.slf4j.LoggerFactory;

import java.sql.SQLException;
import java.time.Instant;
import java.util.Optional;

public class RepeatMessages implements Task {

	private static final Logger LOG = (Logger) LoggerFactory.getLogger(RepeatMessages.class);

	@Override
	public void handle(App bot) {
		for (RepeatMessage data : bot.getDBUtil().repeatMessages.getExpired()) {
			TextChannel channel = bot.JDA.getTextChannelById(data.getChannelId());
			if (channel == null) {
				// Channel is gone (or the bot lost access to it) - drop the entry
				LOG.warn("Channel for repeat message '{}' not found, deleting.", data.getRepeatId());
				remove(bot, data);
				continue;
			}
			if (!channel.canTalk()) {
				LOG.warn("Cannot send repeat message '{}' in channel '{}', deleting.", data.getRepeatId(), channel.getId());
				remove(bot, data);
				continue;
			}

			// Advance the schedule before sending, and from *now* - a long downtime must not
			// produce a burst of catch-up messages.
			long nextRun = Instant.now().getEpochSecond() + data.getInterval();
			try {
				bot.getDBUtil().repeatMessages.updateAfterRun(data.getRepeatId(), nextRun, null);
			} catch (SQLException ex) {
				LOG.error("Failed to reschedule repeat message '{}', skipping this run.", data.getRepeatId(), ex);
				continue;
			}

			if (data.isSkipIfLast() && data.getLastMessageId() != null) {
				// Only post when somebody has spoken since our previous copy
				channel.getHistory().retrievePast(1).queue(history -> {
					if (!history.isEmpty() && history.getFirst().getIdLong() == Optional.ofNullable(data.getLastMessageId()).orElse(0L)) return;
					send(bot, channel, data);
				}, failure -> LOG.warn("Failed to read history for repeat message '{}'.", data.getRepeatId(), failure));
			} else {
				send(bot, channel, data);
			}
		}
	}

	private void send(App bot, TextChannel channel, RepeatMessage data) {
		channel.sendMessage(data.toMessageData()).queue(
			message -> {
				try {
					bot.getDBUtil().repeatMessages.updateLastMessage(data.getRepeatId(), message.getIdLong());
				} catch (SQLException ignored) {}
			},
			failure -> LOG.warn("Failed to send repeat message '{}'.", data.getRepeatId(), failure)
		);
	}

	private void remove(App bot, RepeatMessage data) {
		try {
			bot.getDBUtil().repeatMessages.remove(data.getGuildId(), data.getRepeatId());
		} catch (SQLException ignored) {}
	}

}
