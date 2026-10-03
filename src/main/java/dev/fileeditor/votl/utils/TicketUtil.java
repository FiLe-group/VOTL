package dev.fileeditor.votl.utils;

import java.sql.SQLException;
import java.time.Instant;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

import dev.fileeditor.votl.App;
import dev.fileeditor.votl.objects.constants.Constants;
import dev.fileeditor.votl.utils.database.DBUtil;
import dev.fileeditor.votl.utils.database.managers.TicketSettingsManager;
import dev.fileeditor.votl.utils.database.managers.TicketTagManager;
import dev.fileeditor.votl.utils.transcripts.DiscordHtmlTranscripts;

import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.MessageEmbed;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.entities.channel.middleman.GuildMessageChannel;
import net.dv8tion.jda.api.entities.emoji.Emoji;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import net.dv8tion.jda.api.exceptions.ErrorHandler;
import net.dv8tion.jda.api.components.actionrow.ActionRow;
import net.dv8tion.jda.api.components.buttons.Button;
import net.dv8tion.jda.api.requests.ErrorResponse;
import net.dv8tion.jda.api.requests.Route;
import net.dv8tion.jda.api.utils.data.DataObject;
import net.dv8tion.jda.internal.requests.RestActionImpl;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class TicketUtil {
	private final App bot;
	private final DBUtil db;

	public TicketUtil(App bot) {
		this.bot = bot;
		this.db = bot.getDBUtil();
	}

	public void closeTicket(long channelId, @Nullable User userClosed, @Nullable String reasonClosed, @NotNull Consumer<? super Throwable> failureHandler) {
		GuildMessageChannel channel = bot.JDA.getChannelById(GuildMessageChannel.class, channelId);
		if (channel == null) return; // already gone :(

		TicketSettingsManager.TicketSettings settings = bot.getDBUtil().getTicketSettings(channel.getGuild());
		TicketSettingsManager.TranscriptsMode transcriptsMode = settings.getTranscriptsMode();
		// Send the ticket author a link instead of the file. Enabled per server, and requires Zipline config
		final boolean useZipline = settings.ziplineEnabled() && bot.getZiplineUtil().isEnabled();
		if (db.tickets.isRoleTicket(channelId)) {
			// Role request ticket
			if (transcriptsMode.equals(TicketSettingsManager.TranscriptsMode.ALL)) {
				// With transcript
				// Role tickets have no author DM, so nothing to link
				createTranscript(channel, false,
					transcript -> closeTicketRole(channel, userClosed, reasonClosed, failureHandler, transcript),
					failureHandler
				);
			} else {
				// Without transcript
				closeTicketRole(channel, userClosed, reasonClosed, failureHandler, null);
			}
		} else {
			// Standard ticket
			if (transcriptsMode.equals(TicketSettingsManager.TranscriptsMode.NONE)) {
				// Without transcript
				closeTicketStandard(channel, userClosed, reasonClosed, failureHandler, null);
			} else {
				// With transcript
				createTranscript(channel, useZipline,
					transcript -> closeTicketStandard(channel, userClosed, reasonClosed, failureHandler, transcript),
					failureHandler
				);
			}
		}
	}

	/**
	 * Generated transcript.
	 * @param url Zipline link sent to the author, null if not uploaded - then the author receives the file
	 * @param expiresAt when the link stops working, null if unknown
	 */
	public record TicketTranscript(DiscordHtmlTranscripts.Transcript transcript, @Nullable String url, @Nullable Instant expiresAt) {}

	/**
	 * Generates the transcript.
	 * @param useZipline upload the transcript to Zipline, so the author receives a link instead of the file
	 * @param action receives the transcript, or null if there was nothing to transcribe
	 */
	private void createTranscript(GuildMessageChannel channel, boolean useZipline, Consumer<TicketTranscript> action, Consumer<? super Throwable> failureHandler) {
		final ZiplineUtil zipline = bot.getZiplineUtil();
		DiscordHtmlTranscripts.getInstance().queueCreateTranscript(channel, useZipline, transcript -> {
			if (transcript == null) {
				action.accept(null);
				return;
			}
			if (!useZipline) {
				action.accept(new TicketTranscript(transcript, null, null));
				return;
			}
			zipline.uploadTranscript(transcript.data())
				.thenAccept(url -> action.accept(new TicketTranscript(transcript, url, url == null ? null : zipline.getTranscriptExpiresAt())))
				.exceptionally(ex -> {
					failureHandler.accept(ex);
					return null;
				});
		}, failureHandler);
	}

	private void closeTicketRole(@NotNull GuildMessageChannel channel, @Nullable User userClosed, String reasonClosed, @NotNull Consumer<? super Throwable> failureHandler, @Nullable TicketTranscript transcript) {
		final Instant now = Instant.now();
		final Guild guild = channel.getGuild();
		final String finalReason = reasonClosed==null ? "-" : (
			reasonClosed.equals("activity") || reasonClosed.equals("time")
				? bot.getLocaleUtil().getLocalized(bot.getLocaleUtil().getGuildLocale(guild), "logger.ticket.autoclosed")
				: reasonClosed
		);

		try {
			channel.delete().reason(finalReason).queueAfter(4, TimeUnit.SECONDS, _ ->
				{
					try	{
						db.tickets.closeTicket(now, channel.getIdLong(), finalReason);
					} catch (SQLException ignored) {}

					long authorId = db.tickets.getUserId(channel.getIdLong());

					bot.getGuildLogger().ticket.onClose(guild, channel, userClosed, authorId, finalReason, transcript);
				},
				failureHandler
			);
		} catch (Throwable t) {
			failureHandler.accept(t);
		}
	}

	private void closeTicketStandard(@NotNull GuildMessageChannel channel, @Nullable User userClosed, String reasonClosed, @NotNull Consumer<? super Throwable> failureHandler, @Nullable TicketTranscript transcript) {
		final Instant now = Instant.now();
		final Guild guild = channel.getGuild();
		final String finalReason = reasonClosed==null ? "-" : (
			reasonClosed.equals("activity") || reasonClosed.equals("time")
				? bot.getLocaleUtil().getLocalized(App.getInstance().getLocaleUtil().getGuildLocale(guild), "logger.ticket.autoclosed")
				: reasonClosed
		);

		try {
			channel.delete().reason(finalReason).queueAfter(4, TimeUnit.SECONDS, _ ->
				{
					try {
						db.tickets.closeTicket(now, channel.getIdLong(), finalReason);
					} catch (SQLException ignored) {}

					long authorId = db.tickets.getUserId(channel.getIdLong());

					bot.JDA.retrieveUserById(authorId).queue(user -> user.openPrivateChannel().queue(pm -> {
						MessageEmbed embed = bot.getLogEmbedUtil().ticketClosedPmEmbed(guild.getLocale(), channel, now, userClosed, finalReason, transcript);
						if (transcript == null || transcript.url() != null) {
							pm.sendMessageEmbeds(embed).queue(null, new ErrorHandler().ignore(ErrorResponse.CANNOT_SEND_TO_USER));
						} else {
							pm.sendMessageEmbeds(embed).setFiles(transcript.transcript().toFileUpload()).queue(null, new ErrorHandler().ignore(ErrorResponse.CANNOT_SEND_TO_USER));
						}
					}));

					bot.getGuildLogger().ticket.onClose(guild, channel, userClosed, authorId, finalReason, transcript);
				},
				failureHandler
			);
		} catch (Throwable t) {
			failureHandler.accept(t);
		}
	}

	/**
	 * Checks if a ticket, whose channel is missing from cache, can be treated as deleted.
	 * Archived threads are not cached by JDA, so thread tickets and tickets
	 * from unavailable guilds are never assumed to be deleted.
	 */
	public boolean isChannelDeleted(long channelId) {
		Long guildId = db.tickets.getGuildId(channelId);
		if (guildId == null || bot.JDA.getGuildById(guildId) == null) return false;
		return !isThreadTicket(channelId);
	}

	/**
	 * Handles an open ticket, whose channel is missing from cache.
	 * Deleted channel - ticket is closed in DB. Archived thread - it is unarchived,
	 * which puts it back into cache, so it is processed normally on the next run.
	 */
	public void handleMissingChannel(long channelId) {
		Long guildId = db.tickets.getGuildId(channelId);
		if (guildId == null || bot.JDA.getGuildById(guildId) == null) return; // Guild not loaded
		if (!isThreadTicket(channelId)) {
			db.tickets.forceCloseTicket(channelId);
			return;
		}
		unarchiveThread(channelId, () -> {}, () -> db.tickets.forceCloseTicket(channelId));
	}

	/**
	 * Closes a ticket, whose thread is archived and missing from cache.
	 * Thread is unarchived first, then closed normally once JDA caches it again.
	 * @param onDeleted Thread no longer exists
	 */
	public void closeArchivedTicket(long channelId, @Nullable User userClosed, @Nullable String reasonClosed, @NotNull Consumer<? super Throwable> failureHandler, @NotNull Runnable onDeleted) {
		unarchiveThread(channelId, () -> closeWhenCached(channelId, userClosed, reasonClosed, failureHandler, 5), onDeleted);
	}

	private void closeWhenCached(long channelId, @Nullable User userClosed, @Nullable String reasonClosed, @NotNull Consumer<? super Throwable> failureHandler, int attemptsLeft) {
		if (bot.JDA.getChannelById(GuildMessageChannel.class, channelId) != null) {
			closeTicket(channelId, userClosed, reasonClosed, failureHandler);
		} else if (attemptsLeft > 0) {
			// Thread is cached when gateway update arrives, which may come after the request's response
			CompletableFuture.delayedExecutor(2, TimeUnit.SECONDS)
				.execute(() -> closeWhenCached(channelId, userClosed, reasonClosed, failureHandler, attemptsLeft-1));
		} else {
			failureHandler.accept(new IllegalStateException("Ticket thread %s is not cached after unarchiving".formatted(channelId)));
		}
	}

	/**
	 * Unarchives ticket's thread, that is missing from cache - JDA does not cache archived threads,
	 * so it is done with a raw request. Deleted thread responds with Unknown Channel.
	 * @param onExists Thread exists - unarchived, or failed for another reason
	 * @param onDeleted Thread no longer exists
	 */
	public void unarchiveThread(long channelId, @NotNull Runnable onExists, @NotNull Runnable onDeleted) {
		Route.CompiledRoute route = Route.Channels.MODIFY_CHANNEL.compile(String.valueOf(channelId));
		new RestActionImpl<Void>(bot.JDA, route, DataObject.empty().put("archived", false))
			.queue(_ -> onExists.run(), failure -> {
				if (ErrorResponse.UNKNOWN_CHANNEL.test(failure)) {
					onDeleted.run();
				} else {
					App.getLogger().warn("Failed to unarchive ticket thread {}", channelId, failure);
					onExists.run();
				}
			});
	}

	private boolean isThreadTicket(long channelId) {
		Integer tagId = db.tickets.getTag(channelId);
		if (tagId == null || tagId == 0) return true; // Role request tickets are threads
		TicketTagManager.Tag tag = db.ticketTags.getTagInfo(tagId);
		return tag == null || tag.getTagType() == 1; // Unknown tag - can't tell, assume thread
	}

	/**
	 * Sends a ping message for a role-request ticket and auto-deletes it if configured to.
	 * Pass {@code null} requesterId or an empty {@code supportRoleIds} to ping only the other side -
	 * used to send the requester-only ping on ticket creation and the reviewers-only ping once they've replied,
	 * when the guild's "delay role ping" setting is enabled.
	 */
	public void sendRoleTicketPing(@NotNull GuildMessageChannel channel, @Nullable Long requesterId, @NotNull List<Long> supportRoleIds) {
		StringBuilder mentions = new StringBuilder();
		if (requesterId != null) mentions.append("<@").append(requesterId).append(">");
		if (!supportRoleIds.isEmpty()) {
			mentions.append("||");
			supportRoleIds.forEach(roleId -> mentions.append(" <@&").append(roleId).append(">"));
			mentions.append("||");
		}
		if (mentions.isEmpty()) return;

		channel.sendMessage(mentions.toString()).queue(msg -> {
			if (db.getTicketSettings(channel.getGuild()).deletePingsEnabled())
				msg.delete().queueAfter(5, TimeUnit.SECONDS, null, new ErrorHandler().ignore(ErrorResponse.UNKNOWN_CHANNEL));
		});
	}

	public void createTicket(@NotNull ButtonInteractionEvent event, @NotNull GuildMessageChannel channel, @NotNull String mentions, @Nullable String message) {
		channel.sendMessage(mentions).queue(msg -> {
			if (db.getTicketSettings(channel.getGuild()).deletePingsEnabled())
				msg.delete().queueAfter(5, TimeUnit.SECONDS, null, new ErrorHandler().ignore(ErrorResponse.UNKNOWN_CHANNEL));
		});
		Guild guild = event.getGuild();
		assert guild != null;

		MessageEmbed embed = new EmbedBuilder().setColor(db.getGuildSettings(guild).getColor())
			.setDescription(message)
			.build();
		Button close = Button.danger("ticket:close", bot.getLocaleUtil().getGuildText(event, "ticket.close")).withEmoji(Emoji.fromUnicode("🔒")).asDisabled();
		Button claim = Button.primary("ticket:claim", bot.getLocaleUtil().getGuildText(event, "ticket.claim"));
		channel.sendMessageEmbeds(embed)
			.setAllowedMentions(Collections.emptyList())
			.setComponents(
				ActionRow.of(close, claim)
			)
			.queue(msg -> msg
				.editMessageComponents(ActionRow.of(close.asEnabled(), claim))
				.queueAfter(15, TimeUnit.SECONDS, null, new ErrorHandler().ignore(ErrorResponse.UNKNOWN_CHANNEL))
			);

		// Send reply
		event.getHook().sendMessageEmbeds(new EmbedBuilder().setColor(Constants.COLOR_SUCCESS)
			.setDescription(bot.getLocaleUtil().getGuildText(event, "bot.ticketing.listener.created", channel.getAsMention()))
			.build()
		).setEphemeral(true).queue();
		// Log
		bot.getGuildLogger().ticket.onCreate(guild, channel, event.getUser());
	}
}
