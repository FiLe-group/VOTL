package bot.commands;

import com.jagrosh.jdautilities.command.Command;
import com.jagrosh.jdautilities.command.CommandEvent;
import com.jagrosh.jdautilities.commons.JDAUtilitiesInfo;
import com.jagrosh.jdautilities.doc.standard.CommandInfo;

import bot.App;
import bot.constants.Links;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.JDAInfo;
import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.entities.MessageEmbed;

@CommandInfo
(
	name = {"About","Info"},
	description = "Gets information about the bot.",
	usage = "{prefix}about"
)
public class AboutCmd extends Command {

	private final App bot;

	protected Permission[] botPerms;

	public AboutCmd(App bot) {
		this.name = "about";
		this.aliases = new String[]{"info"};
		this.help = "bot.other.about.description";
		this.guildOnly = false;
		this.category = new Category("other");
		this.botPerms = new Permission[]{Permission.MESSAGE_EMBED_LINKS};
		this.bot = bot;
	}

	@Override
	protected void execute(CommandEvent event) {
		
		MessageEmbed embed = getAboutEmbed(event);

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
			} else if (bot.getCheckUtil().lacksPermissions(event.getTextChannel(), event.getMember(), true, event.getTextChannel(), botPerms)) {
				return;
			}
		}

		event.reply(embed);
	}

	private MessageEmbed getAboutEmbed(CommandEvent event) {
		String guildID = "0";
		EmbedBuilder builder = null;
		if (event.getEvent().isFromGuild()) {
			guildID = event.getGuild().getId();
			builder = bot.getEmbedUtil().getEmbed(event.getMember());
		} else {
			builder = bot.getEmbedUtil().getEmbed();
		}

		builder.setAuthor(event.getJDA().getSelfUser().getName(), event.getJDA().getSelfUser().getEffectiveAvatarUrl())
			.setThumbnail(event.getJDA().getSelfUser().getEffectiveAvatarUrl())
			.addField(
				bot.getMsg(guildID, "bot.other.about.embed.about_title"),
				bot.getMsg(guildID, "bot.other.about.embed.about_value"),
				false
			)
			.addField(
				bot.getMsg(guildID, "bot.other.about.embed.commands_title"),
				bot.getMsg(guildID, "bot.other.about.embed.commands_value"),
				false
			)
			.addField(
				bot.getMsg(guildID, "bot.other.about.embed.bot_info.title"),
				String.join(
					"\n",
					bot.getMsg(guildID, "bot.other.about.embed.bot_info.bot_version"),
					bot.getMsg(guildID, "bot.other.about.embed.bot_info.library")
						.replace("{jda_version}", JDAInfo.VERSION_MAJOR+"."+JDAInfo.VERSION_MINOR+"."+JDAInfo.VERSION_REVISION+"-"+JDAInfo.VERSION_CLASSIFIER)
						.replace("{jda_github}", JDAInfo.GITHUB)
						.replace("{chewtils_version}", JDAUtilitiesInfo.VERSION_MAJOR+"."+JDAUtilitiesInfo.VERSION_MINOR)
						.replace("{chewtils_github}", Links.CHEWTILS_GITHUB)
				),
				false
			)
			.addField(
				bot.getMsg(guildID, "bot.other.about.embed.links.title"),
				String.join(
					"\n",
					bot.getMsg(guildID, "bot.other.about.embed.links.discord"),
					bot.getMsg(guildID, "bot.other.about.embed.links.github")
				),
				true
			)
			.addField(
				bot.getMsg(guildID, "bot.other.about.embed.links.unionteams_title"),
				String.join(
					"\n",
					bot.getMsg(guildID, "bot.other.about.embed.links.unionteams_website"),
					bot.getMsg(guildID, "bot.other.about.embed.links.rotr"),
					bot.getMsg(guildID, "bot.other.about.embed.links.ww2")
				),
				true
			);

		return builder.build();
	}
}
