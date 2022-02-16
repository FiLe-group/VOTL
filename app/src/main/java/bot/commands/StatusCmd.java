package bot.commands;

import com.jagrosh.jdautilities.command.Command;
import com.jagrosh.jdautilities.command.CommandEvent;
import com.jagrosh.jdautilities.doc.standard.CommandInfo;

import bot.App;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.entities.MessageEmbed;

@CommandInfo(
	name = "status",
	description = "Gets bot's status.",
	usage = "{prefix}status"
)
public class StatusCmd extends Command {
	
	private App bot;

	protected Permission[] botPerms;

	public StatusCmd(App bot) {
		this.name = "status";
		this.help = "gets bot's status";
		this.guildOnly = false;
		this.category = new Category("other");
		this.botPerms = new Permission[]{Permission.MESSAGE_EMBED_LINKS};
		this.bot = bot;
	}

	public void execute(CommandEvent event) {

		MessageEmbed embed = getStatusEmbed(event);

		if (event.getEvent().isFromGuild()) {
			if (bot.getMessageUtil().hasArgs("dm", event.getArgs())) {
				String mention = event.getMember().getAsMention();
				event.getMember().getUser().openPrivateChannel()
					.flatMap(channel -> channel.sendMessageEmbeds(embed))
					.queue(
						message -> event.getChannel().sendMessage(
							bot.getMsg(event.getGuild().getId(), "bot.dm_success", mention)
						).queue(), 
						error -> event.getChannel().sendMessage(
							bot.getMsg(event.getGuild().getId(), "bot.dm_failure", mention)
						).queue()
					);
				return;
			} else {
				if (bot.getCheckUtil().lacksPermissions(event.getTextChannel(), event.getMember(), true, event.getTextChannel(), botPerms)) {
					return;
				}
			}
		}

		event.reply(embed);

	}

	private MessageEmbed getStatusEmbed(CommandEvent event) {
		String guildID = (event.getEvent().isFromGuild() ? event.getGuild().getId() : "0");
		EmbedBuilder builder = bot.getEmbedUtil().getEmbed();

		builder.setAuthor(event.getJDA().getSelfUser().getName(), event.getJDA().getSelfUser().getEffectiveAvatarUrl())
			.setThumbnail(event.getJDA().getSelfUser().getEffectiveAvatarUrl());
		
		builder.addField(
				bot.getMsg(guildID, "bot.other.status.embed.stats_title"),
				String.join(
					"\n",
					bot.getMsg(guildID, "bot.other.status.embed.stats.guilds").replace("{value}", String.valueOf(event.getClient().getTotalGuilds())),
					bot.getMsg(guildID, "bot.other.status.embed.stats.shard")
						.replace("{this}", String.valueOf(event.getJDA().getShardInfo().getShardId() + 1))
						.replace("{all}", String.valueOf(event.getJDA().getShardInfo().getShardTotal()))
				),
				false
			)
			.addField(bot.getMsg(guildID, "bot.other.status.embed.shard_title"),
				String.join(
					"\n",
					bot.getMsg(guildID, "bot.other.status.embed.shard.users").replace("{value}", String.valueOf(event.getJDA().getUsers().size())),
					bot.getMsg(guildID, "bot.other.status.embed.shard.guilds").replace("{value}", String.valueOf(event.getJDA().getGuilds().size()))
				),
				true
			)
			.addField("",
				String.join(
					"\n",
					bot.getMsg(guildID, "bot.other.status.embed.shard.text_channels").replace("{value}", String.valueOf(event.getJDA().getTextChannels().size())),
					bot.getMsg(guildID, "bot.other.status.embed.shard.voice_channels").replace("{value}", String.valueOf(event.getJDA().getVoiceChannels().size()))
				),
				true
			);

		builder.setFooter(bot.getMsg(guildID, "bot.other.status.embed.last_restart"))
			.setTimestamp(event.getClient().getStartTime());

		return builder.build();
	}
}
