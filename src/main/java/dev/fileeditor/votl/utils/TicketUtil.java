package dev.fileeditor.votl.utils;

import java.time.Instant;
import java.util.Collections;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

import dev.fileeditor.votl.App;
import dev.fileeditor.votl.objects.constants.Constants;
import dev.fileeditor.votl.utils.database.DBUtil;
import dev.fileeditor.votl.utils.transcripts.DiscordHtmlTranscripts;

import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.MessageEmbed;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.entities.channel.middleman.GuildMessageChannel;
import net.dv8tion.jda.api.entities.emoji.Emoji;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import net.dv8tion.jda.api.exceptions.ErrorHandler;
import net.dv8tion.jda.api.interactions.components.ActionRow;
import net.dv8tion.jda.api.interactions.components.buttons.Button;
import net.dv8tion.jda.api.requests.ErrorResponse;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class TicketUtil {
	private final App bot;
	private final DBUtil db;

	public TicketUtil(App bot) {
		this.bot = bot;
		this.db = bot.getDBUtil();
	}

	public void closeTicket(long channelId, @Nullable User userClosed, @Nullable String reasonClosed, @NotNull Consumer<? super Throwable> closeHandle) {
		GuildMessageChannel channel = bot.JDA.getChannelById(GuildMessageChannel.class, channelId);
		if (channel == null) return;

		Guild guild = channel.getGuild();
		Instant now = Instant.now();

		if (db.tickets.isRoleTicket(channelId)) {
			channel.delete().reason(reasonClosed).queueAfter(4, TimeUnit.SECONDS, done -> {
				db.tickets.closeTicket(now, channelId, reasonClosed);

				Long authorId = db.tickets.getUserId(channelId);

				bot.getLogger().ticket.onClose(guild, channel, userClosed, authorId);
			}, failure -> {
				bot.getAppLogger().warn("Error while closing ticket, unable to delete", failure);
				closeHandle.accept(failure);
			});
		} else {
			DiscordHtmlTranscripts transcripts = DiscordHtmlTranscripts.getInstance();
			transcripts.queueCreateTranscript(channel,
				file -> {
					channel.delete().reason(reasonClosed).queueAfter(4, TimeUnit.SECONDS, done -> {
						db.tickets.closeTicket(now, channelId, reasonClosed);

						Long authorId = db.tickets.getUserId(channelId);

						bot.JDA.retrieveUserById(authorId).queue(user -> {
							user.openPrivateChannel().queue(pm -> {
								MessageEmbed embed = bot.getLogEmbedUtil().ticketClosedPmEmbed(guild.getLocale(), channel, now, userClosed, reasonClosed);
								if (file == null) {
									pm.sendMessageEmbeds(embed).queue(null, new ErrorHandler().ignore(ErrorResponse.CANNOT_SEND_TO_USER));
								} else {
									pm.sendMessageEmbeds(embed).setFiles(file).queue(null, new ErrorHandler().ignore(ErrorResponse.CANNOT_SEND_TO_USER));
								}
							});
						});

						bot.getLogger().ticket.onClose(guild, channel, userClosed, authorId, file);
					}, failure -> {
						bot.getAppLogger().warn("Error while closing ticket, unable to delete", failure);
						closeHandle.accept(failure);
					});
				},
				closeHandle
			);
		}
	}

	public void createTicket(ButtonInteractionEvent event, GuildMessageChannel channel, String mentions, String message) {
		channel.sendMessage(mentions).queue(msg -> msg.delete().queueAfter(5, TimeUnit.SECONDS, null, new ErrorHandler().ignore(ErrorResponse.UNKNOWN_CHANNEL)));

		MessageEmbed embed = new EmbedBuilder().setColor(db.getGuildSettings(event.getGuild()).getColor())
			.setDescription(message)
			.build();
		Button close = Button.danger("ticket:close", bot.getLocaleUtil().getLocalized(event.getGuildLocale(), "ticket.close")).withEmoji(Emoji.fromUnicode("🔒")).asDisabled();
		Button claim = Button.primary("ticket:claim", bot.getLocaleUtil().getLocalized(event.getGuildLocale(), "ticket.claim"));
		channel.sendMessageEmbeds(embed).setAllowedMentions(Collections.emptyList()).addActionRow(close, claim).queue(msg -> {
			msg.editMessageComponents(ActionRow.of(close.asEnabled(), claim)).queueAfter(15, TimeUnit.SECONDS, null, new ErrorHandler().ignore(ErrorResponse.UNKNOWN_CHANNEL));
		});

		// Send reply
		event.getHook().sendMessageEmbeds(new EmbedBuilder().setColor(Constants.COLOR_SUCCESS)
			.setDescription(bot.getLocaleUtil().getText(event, "bot.ticketing.listener.created").replace("{channel}", channel.getAsMention()))
			.build()
		).setEphemeral(true).queue();
		// Log
		bot.getLogger().ticket.onCreate(event.getGuild(), channel, event.getUser());
	}
}
