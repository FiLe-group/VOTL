package dev.fileeditor.votl.commands.tool;

import dev.fileeditor.votl.base.command.SlashCommand;
import dev.fileeditor.votl.base.command.SlashCommandEvent;
import dev.fileeditor.votl.objects.CmdAccessLevel;
import dev.fileeditor.votl.objects.CmdModule;
import dev.fileeditor.votl.objects.MediaType;
import dev.fileeditor.votl.objects.constants.CmdCategory;
import dev.fileeditor.votl.objects.constants.Constants;
import dev.fileeditor.votl.objects.constants.Limits;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.entities.channel.ChannelType;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;

import java.sql.SQLException;
import java.util.EnumSet;
import java.util.List;

public class MediaChannelCmd extends SlashCommand {

	public MediaChannelCmd() {
		this.name = "media_channel";
		this.path = "bot.tool.media_channel";
		this.children = new SlashCommand[] {
			new AddChannel(), new RemoveChannel(), new ListChannels()
		};
		this.category = CmdCategory.TOOLS;
		this.module = CmdModule.TOOLS;
		this.accessLevel = CmdAccessLevel.ADMIN;
	}

	@Override
	protected void execute(SlashCommandEvent event) {}

	private class AddChannel extends SlashCommand {
		public AddChannel() {
			this.name = "add";
			this.path = "bot.tool.media_channel.add";
			this.options = List.of(
				new OptionData(OptionType.CHANNEL, "channel", lu.getText(path+".channel.help"), true)
					.setChannelTypes(ChannelType.TEXT),
				new OptionData(OptionType.INTEGER, "allowed_media", lu.getText(path+".allowed_media.help"), true)
					.addChoice("Any media", MediaType.allMedia())
					.addChoice("Text messages only (any links will be deleted)", 0) // Note: ANY MESSAGES CONTAINING LINKS WILL BE DELETED
					.addChoice("Images only", MediaType.encode(EnumSet.of(MediaType.IMAGE)))
					.addChoice("GIFs only", MediaType.encode(EnumSet.of(MediaType.ANIMATED)))
					.addChoice("Video only", MediaType.encode(EnumSet.of(MediaType.VIDEO)))
					.addChoice("Audio only", MediaType.encode(EnumSet.of(MediaType.AUDIO)))
					.addChoice("Visual content (img+gif+vid)", MediaType.encode(EnumSet.of(MediaType.IMAGE, MediaType.ANIMATED, MediaType.VIDEO))),
				new OptionData(OptionType.BOOLEAN, "allowed_text", lu.getText(path+".allowed_text.help")),
				new OptionData(OptionType.INTEGER, "max_attachments", lu.getText(path+".max_attachments.help"))
					.setRequiredRange(0, 10)
			);
		}

		@Override
		protected void execute(SlashCommandEvent event) {
			assert event.getGuild() != null;
			long guildId = event.getGuild().getIdLong();

			var channel = event.optGuildChannel("channel");
			if (channel == null || event.getGuild().getGuildChannelById(channel.getIdLong()) == null) {
				editError(event, "errors.option.channel");
				return;
			}

			if (bot.getDBUtil().mediaChannels.getChannels(guildId).size() >= Limits.MEDIA_CHANNELS) {
				editErrorLimit(event, "media channels", Limits.MEDIA_CHANNELS);
				return;
			}

			var allowedMedia = MediaType.decode(event.optInteger("allowed_media", MediaType.allMedia()));
			var allowedText = event.optBoolean("allowed_text", true);
			var maxAttachments = event.optInteger("max_attachments", 10);

			if (allowedMedia.isEmpty() && !allowedText) {
				editError(event, path+".bad_combination");
				return;
			}

			// Override max attachments if no media is allowed (THIS INCLUDES ANY LINKS)
			if (allowedMedia.isEmpty()) {
				maxAttachments = 0;
			}

			try {
				bot.getDBUtil().mediaChannels.addChannel(guildId, channel.getIdLong(), allowedMedia, allowedText, maxAttachments);
			} catch (SQLException e) {
				editErrorDatabase(event, e, "Failed to add media channel");
				return;
			}

			var embed = bot.getEmbedUtil().getEmbed(Constants.COLOR_SUCCESS)
				.setDescription(lu.getText(event, path+".done", channel.getAsMention()))
				.appendDescription("\n\n`");
			for (var v : MediaType.values()) {
				embed.appendDescription(v.getEmoji()).appendDescription("|");
			}
			embed.appendDescription("\uD83D\uDCAC|MAX`\n`");

			for (var v : MediaType.values()) {
				embed.appendDescription(allowedMedia.contains(v) ? Constants.SUCCESS : Constants.FAILURE)
					.appendDescription("|");
			}
			embed.appendDescription("%s|%d`".formatted(
				allowedText ? Constants.SUCCESS : Constants.FAILURE,
				maxAttachments
			));

			editEmbed(event, embed.build());
		}
	}

	private class RemoveChannel extends SlashCommand {
		public RemoveChannel() {
			this.name = "remove";
			this.path = "bot.tool.media_channel.remove";
			this.options = List.of(
				new OptionData(OptionType.CHANNEL, "channel", lu.getText(path+".channel.help"), true)
					.setChannelTypes(ChannelType.TEXT)
			);
		}

		@Override
		protected void execute(SlashCommandEvent event) {
			assert event.getGuild() != null;
			long guildId = event.getGuild().getIdLong();

			var channel = event.optChannel("channel");
			if (channel == null || event.getGuild().getGuildChannelById(channel.getIdLong()) == null) {
				editError(event, "errors.option.channel");
				return;
			}

			try {
				bot.getDBUtil().mediaChannels.removeChannel(guildId, channel.getIdLong());
			} catch (SQLException e) {
				editErrorDatabase(event, e, "Failed to remove media channel");
				return;
			}

			editEmbed(event, bot.getEmbedUtil().getEmbed(Constants.COLOR_SUCCESS)
				.setDescription(lu.getText(event, path+".done", channel.getAsMention()))
				.build());
		}
	}

	private class ListChannels extends SlashCommand {
		public ListChannels() {
			this.name = "view";
			this.path = "bot.tool.media_channel.view";
		}

		@Override
		protected void execute(SlashCommandEvent event) {
			assert event.getGuild() != null;
			long guildId = event.getGuild().getIdLong();

			var channels = bot.getDBUtil().mediaChannels.getChannels(guildId);
			if (channels.isEmpty()) {
				editEmbed(event, bot.getEmbedUtil().getEmbed()
					.setDescription(lu.getText(event, path+".empty"))
					.build());
				return;
			}

			EmbedBuilder embed = bot.getEmbedUtil().getEmbed()
				.setTitle(lu.getText(event, path+".embed_title"))
				.setDescription("`");

			for (var v : MediaType.values()) {
				embed.appendDescription(v.getEmoji()).appendDescription("|");
			}
			embed.appendDescription("\uD83D\uDCAC|MAX`\n");

			channels.forEach((id, settings) -> {
				embed.appendDescription("`");
				for (var v : MediaType.values()) {
					embed.appendDescription(settings.getAllowedMedia().contains(v) ? Constants.SUCCESS : Constants.FAILURE)
						.appendDescription("|");
				}
				embed.appendDescription("%s|%d` - <#%s>\n".formatted(
					settings.allowedText() ? Constants.SUCCESS : Constants.FAILURE,
					settings.getMaxAttachments(),
					id
				));
			});

			editEmbed(event, embed.build());
		}
	}

}
