package dev.fileeditor.votl.commands.tool;

import dev.fileeditor.votl.base.command.SlashCommand;
import dev.fileeditor.votl.base.command.SlashCommandEvent;
import dev.fileeditor.votl.objects.AccessPermission;
import dev.fileeditor.votl.objects.CmdModule;
import dev.fileeditor.votl.objects.MediaChannelMode;
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
		this.requiredPermission = AccessPermission.ADMIN;
	}

	@Override
	protected void execute(SlashCommandEvent event) {}

	/**
	 * Renders the channel's mode and, for the media modes, which media types
	 * are kept and how many attachments one message may hold.
	 */
	private String describeSettings(SlashCommandEvent event, MediaChannelMode mode, EnumSet<MediaType> allowedMedia, int maxAttachments) {
		StringBuilder builder = new StringBuilder("> ").append(lu.getText(event, mode.getPath()));
		if (mode.allowsMedia()) {
			builder.append("\n> ");
			for (var v : MediaType.values()) {
				builder.append(v.getEmoji())
					.append(allowedMedia.contains(v) ? Constants.SUCCESS : Constants.FAILURE)
					.append(' ');
			}
			builder.append("| MAX ").append(maxAttachments);
		}
		return builder.toString();
	}

	private class AddChannel extends SlashCommand {
		public AddChannel() {
			this.name = "add";
			this.path = "bot.tool.media_channel.add";
			this.options = List.of(
				new OptionData(OptionType.CHANNEL, "channel", lu.getText(path+".channel.help"), true)
					.setChannelTypes(ChannelType.TEXT),
				new OptionData(OptionType.INTEGER, "mode", lu.getText(path+".mode.help"), true)
					.addChoices(MediaChannelMode.asChoices(lu)),
				// Only taken into account by the media modes
				new OptionData(OptionType.INTEGER, "allowed_media", lu.getText(path+".allowed_media.help"))
					.addChoice("Any media", MediaType.allMedia())
					.addChoice("Images only", MediaType.encode(EnumSet.of(MediaType.IMAGE)))
					.addChoice("GIFs only", MediaType.encode(EnumSet.of(MediaType.ANIMATED)))
					.addChoice("Video only", MediaType.encode(EnumSet.of(MediaType.VIDEO)))
					.addChoice("Audio only", MediaType.encode(EnumSet.of(MediaType.AUDIO)))
					.addChoice("Visual content (img+gif+vid)", MediaType.encode(EnumSet.of(MediaType.IMAGE, MediaType.ANIMATED, MediaType.VIDEO))),
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

			var mode = MediaChannelMode.byValue(event.optInteger("mode", 0));
			var allowedMedia = MediaType.decode(event.optInteger("allowed_media", MediaType.allMedia()));
			// Attachments are only ever kept by the media modes
			var maxAttachments = mode.allowsMedia() ? event.optInteger("max_attachments", 10) : 0;

			try {
				bot.getDBUtil().mediaChannels.addChannel(guildId, channel.getIdLong(), allowedMedia, mode, maxAttachments);
			} catch (SQLException e) {
				editErrorDatabase(event, e, "Failed to add media channel");
				return;
			}

			editEmbed(event, bot.getEmbedUtil().getEmbed(Constants.COLOR_SUCCESS)
				.setDescription(lu.getText(event, path+".done", channel.getAsMention()))
				.appendDescription("\n\n"+describeSettings(event, mode, allowedMedia, maxAttachments))
				.build());
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
				.setDescription("");

			channels.forEach((id, settings) -> embed.appendDescription("<#%s>\n%s\n\n".formatted(
				id,
				describeSettings(event, settings.getMode(), settings.getAllowedMedia(), settings.getMaxAttachments())
			)));

			editEmbed(event, embed.build());
		}
	}

}
