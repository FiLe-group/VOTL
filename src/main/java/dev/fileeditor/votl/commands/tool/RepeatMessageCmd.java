package dev.fileeditor.votl.commands.tool;

import dev.fileeditor.votl.base.command.SlashCommand;
import dev.fileeditor.votl.base.command.SlashCommandEvent;
import dev.fileeditor.votl.objects.AccessPermission;
import dev.fileeditor.votl.objects.CmdModule;
import dev.fileeditor.votl.objects.constants.CmdCategory;
import dev.fileeditor.votl.objects.constants.Constants;
import dev.fileeditor.votl.objects.constants.Limits;
import dev.fileeditor.votl.utils.database.managers.RepeatMessageManager;
import dev.fileeditor.votl.utils.database.managers.RepeatMessageManager.RepeatMessage;
import dev.fileeditor.votl.utils.exception.FormatterException;
import dev.fileeditor.votl.utils.message.MessageUtil;
import dev.fileeditor.votl.utils.message.TimeUtil;

import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import net.dv8tion.jda.api.events.interaction.command.CommandAutoCompleteInteractionEvent;
import net.dv8tion.jda.api.exceptions.ErrorHandler;
import net.dv8tion.jda.api.interactions.commands.Command;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import net.dv8tion.jda.api.requests.ErrorResponse;
import net.dv8tion.jda.api.utils.TimeFormat;

import java.sql.SQLException;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

public class RepeatMessageCmd extends SlashCommand {

	public static final Duration MIN_INTERVAL = Duration.ofHours(6);
	public static final Duration MAX_INTERVAL = Duration.ofDays(30);

	/** Matches a Discord message link, capturing guild, channel and message IDs. */
	private static final Pattern MESSAGE_LINK = Pattern.compile(
		"https?://(?:ptb\\.|canary\\.)?discord(?:app)?\\.com/channels/(\\d{17,20})/(\\d{17,20})/(\\d{17,20})"
	);
	private static final Pattern MESSAGE_ID = Pattern.compile("\\d{17,20}");

	public RepeatMessageCmd() {
		this.name = "repeat_message";
		this.path = "bot.tool.repeat_message";
		this.children = new SlashCommand[] {
			new Add(), new Remove(), new View()
		};
		this.category = CmdCategory.TOOLS;
		this.module = CmdModule.TOOLS;
		this.requiredPermission = AccessPermission.ADMIN;
	}

	@Override
	protected void execute(SlashCommandEvent event) {}

	/** One entry as rendered by /repeat_message view. */
	private String describe(SlashCommandEvent event, RepeatMessage data) {
		return "`#%d` <#%d>\n> %s | %s %s\n> %s: %s\n> %s".formatted(
			data.getRepeatId(),
			data.getChannelId(),
			TimeUtil.durationToLocalizedString(lu, event.getUserLocale(), Duration.ofSeconds(data.getInterval())),
			lu.getText(event, "bot.tool.repeat_message.view.next"),
			TimeFormat.RELATIVE.atInstant(Instant.ofEpochSecond(data.getNextRun())),
			lu.getText(event, "bot.tool.repeat_message.view.skip_if_last"),
			data.isSkipIfLast() ? Constants.SUCCESS : Constants.FAILURE,
			preview(event, data)
		);
	}

	private String preview(SlashCommandEvent event, RepeatMessage data) {
		if (data.getContent() == null || data.getContent().isBlank())
			return lu.getText(event, "bot.tool.repeat_message.view.embed_only");
		return MessageUtil.limitString(data.getContent().replace("\n", " "), 80);
	}

	private class Add extends SlashCommand {
		public Add() {
			this.name = "add";
			this.path = "bot.tool.repeat_message.add";
			this.options = List.of(
				new OptionData(OptionType.STRING, "message", lu.getText(path+".message.help"), true)
					.setMaxLength(120),
				new OptionData(OptionType.STRING, "interval", lu.getText(path+".interval.help"), true)
					.setMaxLength(12),
				new OptionData(OptionType.BOOLEAN, "skip_if_last", lu.getText(path+".skip_if_last.help"))
			);
		}

		@Override
		protected void execute(SlashCommandEvent event) {
			Guild guild = event.getGuild();
			assert guild != null;
			long guildId = guild.getIdLong();

			if (bot.getDBUtil().repeatMessages.countGuild(guildId) >= Limits.REPEAT_MESSAGES) {
				editErrorLimit(event, "repeat messages", Limits.REPEAT_MESSAGES);
				return;
			}

			// Interval
			final Duration interval;
			try {
				interval = TimeUtil.stringToDuration(event.optString("interval"), false);
			} catch (FormatterException ex) {
				editError(event, ex.getPath());
				return;
			}
			if (interval.compareTo(MIN_INTERVAL) < 0 || interval.compareTo(MAX_INTERVAL) > 0) {
				editError(event, path+".bad_interval");
				return;
			}

			// Message reference - either a full link or a plain ID inside the current channel
			String input = Objects.requireNonNull(event.optString("message")).trim();
			final long channelId;
			final long messageId;
			Matcher linkMatcher = MESSAGE_LINK.matcher(input);
			// find() rather than matches() so a link pasted inside <> or with a trailing query still works
			if (linkMatcher.find()) {
				if (!linkMatcher.group(1).equals(guild.getId())) {
					editError(event, path+".wrong_guild");
					return;
				}
				channelId = Long.parseLong(linkMatcher.group(2));
				messageId = Long.parseLong(linkMatcher.group(3));
			} else if (MESSAGE_ID.matcher(input).matches()) {
				channelId = event.getChannelIdLong();
				messageId = Long.parseLong(input);
			} else {
				editError(event, path+".invalid_link");
				return;
			}

			TextChannel channel = guild.getTextChannelById(channelId);
			if (channel == null) {
				editError(event, "errors.option.channel");
				return;
			}
			if (!channel.canTalk()) {
				editPermError(event, Permission.MESSAGE_SEND, true);
				return;
			}

			channel.retrieveMessageById(messageId).queue(message -> {
				String content = message.getContentRaw();
				String embeds = RepeatMessageManager.encodeEmbeds(message.getEmbeds());
				// Attachments are deliberately not copied - their CDN links are signed and expire
				if (content.isBlank() && embeds == null) {
					editError(event, path+".empty_message");
					return;
				}
				if (embeds != null && !guild.getSelfMember().hasPermission(channel, Permission.MESSAGE_EMBED_LINKS)) {
					editPermError(event, Permission.MESSAGE_EMBED_LINKS, true);
					return;
				}

				Instant nextRun = Instant.now().plus(interval);
				final int repeatId;
				try {
					repeatId = bot.getDBUtil().repeatMessages.add(
						guildId, channel.getIdLong(), messageId, content, embeds,
						interval.toSeconds(), nextRun.getEpochSecond(), event.optBoolean("skip_if_last", false)
					);
				} catch (SQLException ex) {
					editErrorDatabase(event, ex, "Failed to add repeating message");
					return;
				}

				editEmbed(event, bot.getEmbedUtil().getEmbed(Constants.COLOR_SUCCESS)
					.setDescription(lu.getText(event, path+".done", repeatId, channel.getAsMention()))
					.appendDescription("\n> %s | %s %s".formatted(
						TimeUtil.durationToLocalizedString(lu, event.getUserLocale(), interval),
						lu.getText(event, "bot.tool.repeat_message.view.next"),
						TimeFormat.RELATIVE.atInstant(nextRun)
					))
					.build());
			}, new ErrorHandler()
				.handle(ErrorResponse.UNKNOWN_MESSAGE, _ -> editError(event, path+".not_found"))
				.handle(ErrorResponse.MISSING_ACCESS, _ -> editPermError(event, Permission.MESSAGE_HISTORY, true))
			);
		}
	}

	private class Remove extends SlashCommand {
		public Remove() {
			this.name = "remove";
			this.path = "bot.tool.repeat_message.remove";
			this.options = List.of(
				new OptionData(OptionType.INTEGER, "id", lu.getText(path+".id.help"), true, true)
			);
		}

		@Override
		protected void execute(SlashCommandEvent event) {
			assert event.getGuild() != null;
			long guildId = event.getGuild().getIdLong();

			int repeatId = event.optInteger("id", 0);
			if (bot.getDBUtil().repeatMessages.get(guildId, repeatId) == null) {
				editError(event, path+".not_exists");
				return;
			}

			try {
				bot.getDBUtil().repeatMessages.remove(guildId, repeatId);
			} catch (SQLException ex) {
				editErrorDatabase(event, ex, "Failed to remove repeating message");
				return;
			}

			editEmbed(event, bot.getEmbedUtil().getEmbed(Constants.COLOR_SUCCESS)
				.setDescription(lu.getText(event, path+".done", repeatId))
				.build());
		}

		@Override
		public void onAutoComplete(CommandAutoCompleteInteractionEvent event) {
			if (event.getGuild() == null) return;
			String query = event.getFocusedOption().getValue();
			List<Command.Choice> choices = bot.getDBUtil().repeatMessages
				.getGuild(event.getGuild().getIdLong())
				.stream()
				.filter(data -> query.isBlank() || String.valueOf(data.getRepeatId()).startsWith(query))
				.limit(25)
				.map(data -> new Command.Choice(choiceName(data), data.getRepeatId()))
				.collect(Collectors.toList());
			event.replyChoices(choices).queue();
		}

		private String choiceName(RepeatMessage data) {
			String text = data.getContent() == null || data.getContent().isBlank()
				? "[embed]"
				: data.getContent().replace("\n", " ");
			return MessageUtil.limitString("#%d | %s".formatted(data.getRepeatId(), text), 100);
		}
	}

	private class View extends SlashCommand {
		public View() {
			this.name = "view";
			this.path = "bot.tool.repeat_message.view";
		}

		@Override
		protected void execute(SlashCommandEvent event) {
			assert event.getGuild() != null;

			List<RepeatMessage> list = bot.getDBUtil().repeatMessages.getGuild(event.getGuild().getIdLong());
			if (list.isEmpty()) {
				editEmbed(event, bot.getEmbedUtil().getEmbed()
					.setDescription(lu.getText(event, path+".empty"))
					.build());
				return;
			}

			EmbedBuilder embed = bot.getEmbedUtil().getEmbed()
				.setTitle(lu.getText(event, path+".embed_title"))
				.setDescription("");
			list.forEach(data -> embed.appendDescription(describe(event, data)+"\n\n"));

			editEmbed(event, embed.build());
		}
	}

}
