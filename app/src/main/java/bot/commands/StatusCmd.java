package bot.commands;

import java.util.Collections;
import java.util.Optional;

import com.jagrosh.jdautilities.command.SlashCommand;
import com.jagrosh.jdautilities.command.SlashCommandEvent;
import com.jagrosh.jdautilities.doc.standard.CommandInfo;

import bot.App;
import bot.objects.constants.CmdCategory;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.interactions.InteractionHook;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;

@CommandInfo(
	name = "status",
	description = "Gets bot's status.",
	usage = "/status [show?]"
)
public class StatusCmd extends SlashCommand {
	
	private App bot;

	public StatusCmd(App bot) {
		this.name = "status";
		this.help = bot.getMsg("bot.other.status.help");
		this.guildOnly = false;
		this.category = CmdCategory.OTHER;
		this.options = Collections.singletonList(
			new OptionData(OptionType.BOOLEAN, "show", bot.getMsg("misc.show_description"))
		);
		this.bot = bot;
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
		String guildId = Optional.ofNullable(event.getGuild()).map(g -> g.getId()).orElse("0");
		EmbedBuilder builder = bot.getEmbedUtil().getEmbed();

		builder.setAuthor(event.getJDA().getSelfUser().getName(), event.getJDA().getSelfUser().getEffectiveAvatarUrl())
			.setThumbnail(event.getJDA().getSelfUser().getEffectiveAvatarUrl());
		
		builder.addField(
				bot.getMsg(guildId, "bot.other.status.embed.stats_title"),
				String.join(
					"\n",
					bot.getMsg(guildId, "bot.other.status.embed.stats.guilds").replace("{value}", String.valueOf(event.getClient().getTotalGuilds())),
					bot.getMsg(guildId, "bot.other.status.embed.stats.shard")
						.replace("{this}", String.valueOf(event.getJDA().getShardInfo().getShardId() + 1))
						.replace("{all}", String.valueOf(event.getJDA().getShardInfo().getShardTotal()))
				),
				false
			)
			.addField(bot.getMsg(guildId, "bot.other.status.embed.shard_title"),
				String.join(
					"\n",
					bot.getMsg(guildId, "bot.other.status.embed.shard.users").replace("{value}", String.valueOf(event.getJDA().getUsers().size())),
					bot.getMsg(guildId, "bot.other.status.embed.shard.guilds").replace("{value}", String.valueOf(event.getJDA().getGuilds().size()))
				),
				true
			)
			.addField("",
				String.join(
					"\n",
					bot.getMsg(guildId, "bot.other.status.embed.shard.text_channels").replace("{value}", String.valueOf(event.getJDA().getTextChannels().size())),
					bot.getMsg(guildId, "bot.other.status.embed.shard.voice_channels").replace("{value}", String.valueOf(event.getJDA().getVoiceChannels().size()))
				),
				true
			);

		builder.setFooter(bot.getMsg(guildId, "bot.other.status.embed.last_restart"))
			.setTimestamp(event.getClient().getStartTime());

		hook.editOriginalEmbeds(builder.build()).queue();
	}
}
