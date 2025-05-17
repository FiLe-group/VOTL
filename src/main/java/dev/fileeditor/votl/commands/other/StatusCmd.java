package dev.fileeditor.votl.commands.other;

import dev.fileeditor.votl.base.command.SlashCommand;
import dev.fileeditor.votl.base.command.SlashCommandEvent;
import dev.fileeditor.votl.objects.constants.CmdCategory;
import dev.fileeditor.votl.objects.constants.Constants;

import dev.fileeditor.votl.utils.file.lang.LocaleUtil;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.entities.MessageEmbed;
import net.dv8tion.jda.api.interactions.DiscordLocale;

public class StatusCmd extends SlashCommand {

	public StatusCmd() {
		this.name = "status";
		this.path = "bot.other.status";
		this.category = CmdCategory.OTHER;
		this.guildOnly = false;
		this.ephemeral = true;
	}

	@Override
	protected void execute(SlashCommandEvent event) {
		DiscordLocale userLocale = event.getUserLocale();
		MessageEmbed embed = new EmbedBuilder().setColor(Constants.COLOR_DEFAULT)
			.setAuthor(event.getJDA().getSelfUser().getName(), event.getJDA().getSelfUser().getEffectiveAvatarUrl())
			.setThumbnail(event.getJDA().getSelfUser().getEffectiveAvatarUrl())
			.addField(
				lu.getLocalized(userLocale, "bot.other.status.embed.stats_title"),
				String.join(
					"\n",
					lu.getLocalized(userLocale, "bot.other.status.embed.stats.guilds")
						.formatted(event.getJDA().getGuilds().size()),
					lu.getLocalized(userLocale, "bot.other.status.embed.stats.shard")
						.formatted(event.getJDA().getShardInfo().getShardId() + 1, event.getJDA().getShardInfo().getShardTotal()),
					memoryUsage(lu, userLocale)
				),
				false
			)
			.addField(lu.getLocalized(userLocale, "bot.other.status.embed.shard_title"),
				String.join(
					"\n",
					lu.getLocalized(userLocale, "bot.other.status.embed.shard.users")
						.formatted(event.getJDA().getUsers().size()),
					lu.getLocalized(userLocale, "bot.other.status.embed.shard.guilds")
						.formatted(event.getJDA().getGuilds().size())
				),
				true
			)
			.addField("",
				String.join(
					"\n",
					lu.getLocalized(userLocale, "bot.other.status.embed.shard.text_channels")
						.formatted(event.getJDA().getTextChannels().size()),
					lu.getLocalized(userLocale, "bot.other.status.embed.shard.voice_channels")
						.formatted(event.getJDA().getVoiceChannels().size())
				),
				true
			)
			.setFooter(lu.getLocalized(userLocale, "bot.other.status.embed.last_restart"))
			.setTimestamp(event.getClient().getStartTime())
			.build();

		editEmbed(event, embed);
	}

	private String memoryUsage(LocaleUtil lu, DiscordLocale locale) {
		return lu.getLocalized(locale, "bot.other.status.embed.stats.memory").formatted(
			(Runtime.getRuntime().totalMemory() - Runtime.getRuntime().freeMemory()) / (1024 * 1024),
			Runtime.getRuntime().totalMemory() / (1024 * 1024)
		);
	}

}
