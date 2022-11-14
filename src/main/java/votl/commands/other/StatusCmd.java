package votl.commands.other;

import java.util.Collections;

import votl.App;
import votl.commands.CommandBase;
import votl.objects.command.SlashCommandEvent;
import votl.objects.constants.CmdCategory;

import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.interactions.DiscordLocale;
import net.dv8tion.jda.api.interactions.InteractionHook;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;

import com.jagrosh.jdautilities.doc.standard.CommandInfo;

@CommandInfo(
	name = "status",
	description = "Gets bot's status.",
	usage = "/status [show?]"
)
public class StatusCmd extends CommandBase {

	public StatusCmd(App bot) {
		super(bot);
		this.name = "status";
		this.path = "bot.other.status";
		this.options = Collections.singletonList(
			new OptionData(OptionType.BOOLEAN, "show", lu.getText("misc.show_description"))
		);
		this.category = CmdCategory.OTHER;
		this.guildOnly = false;
	}

	@Override
	protected void execute(SlashCommandEvent event) {	

		event.deferReply(event.isFromGuild() ? !event.getOption("show", false, OptionMapping::getAsBoolean) : false).queue(
			hook -> {
				sendReply(event, hook);
			}
		);

	}

	@SuppressWarnings("null")
	private void sendReply(SlashCommandEvent event, InteractionHook hook) {
		DiscordLocale userLocale = event.getUserLocale();
		EmbedBuilder builder = bot.getEmbedUtil().getEmbed();

		builder.setAuthor(event.getJDA().getSelfUser().getName(), event.getJDA().getSelfUser().getEffectiveAvatarUrl())
			.setThumbnail(event.getJDA().getSelfUser().getEffectiveAvatarUrl());
		
		builder.addField(
				lu.getLocalized(userLocale, "bot.other.status.embed.stats_title"),
				String.join(
					"\n",
					lu.getLocalized(userLocale, "bot.other.status.embed.stats.guilds").replace("{value}", String.valueOf(event.getClient().getTotalGuilds())),
					lu.getLocalized(userLocale, "bot.other.status.embed.stats.shard")
						.replace("{this}", String.valueOf(event.getJDA().getShardInfo().getShardId() + 1))
						.replace("{all}", String.valueOf(event.getJDA().getShardInfo().getShardTotal()))
				),
				false
			)
			.addField(lu.getLocalized(userLocale, "bot.other.status.embed.shard_title"),
				String.join(
					"\n",
					lu.getLocalized(userLocale, "bot.other.status.embed.shard.users").replace("{value}", String.valueOf(event.getJDA().getUsers().size())),
					lu.getLocalized(userLocale, "bot.other.status.embed.shard.guilds").replace("{value}", String.valueOf(event.getJDA().getGuilds().size()))
				),
				true
			)
			.addField("",
				String.join(
					"\n",
					lu.getLocalized(userLocale, "bot.other.status.embed.shard.text_channels").replace("{value}", String.valueOf(event.getJDA().getTextChannels().size())),
					lu.getLocalized(userLocale, "bot.other.status.embed.shard.voice_channels").replace("{value}", String.valueOf(event.getJDA().getVoiceChannels().size()))
				),
				true
			);

		builder.setFooter(lu.getLocalized(userLocale, "bot.other.status.embed.last_restart"))
			.setTimestamp(event.getClient().getStartTime());

		hook.editOriginalEmbeds(builder.build()).queue();
	}
}
