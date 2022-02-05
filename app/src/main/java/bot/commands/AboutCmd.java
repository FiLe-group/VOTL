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
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Member;
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

	public AboutCmd(App bot, String description, String[] features) {
		this.name = "about";
		this.aliases = new String[]{"info"};
		this.help = "gets information about the bot";
		//this.guildOnly = false;
		this.category = new Category("other");
		this.botPerms = new Permission[]{Permission.MESSAGE_EMBED_LINKS};
		this.bot = bot;
	}

	@Override
	protected void execute(CommandEvent event) {
		
		MessageEmbed embed = getEmbed(event.getMember());

		if(bot.getMessageUtil().hasArgs("dm", event.getArgs())){
			String mention = event.getMember().getAsMention();
			event.getMember().getUser().openPrivateChannel()
					.flatMap(channel -> channel.sendMessageEmbeds(embed))
					.queue(
							message -> event.getTextChannel().sendMessage(
									bot.getMsg(event.getGuild().getId(), "bot.other.about.dm_success", mention)
							).queue(), 
							error -> event.getTextChannel().sendMessage(
									bot.getMsg(event.getGuild().getId(), "bot.other.about.dm_failure", mention)
							).queue()
					);
			return;
		}

		for (Permission perm : botPerms) {
			if (!event.getSelfMember().hasPermission(event.getTextChannel(), perm)) {
				bot.getEmbedUtil().sendPermError(event.getTextChannel(), event.getMember(), perm, true);
				return;
			}
		}

		event.getTextChannel().sendMessageEmbeds(embed).queue();
	}

	private MessageEmbed getEmbed(Member member) {
		Guild guild = member.getGuild();
		String guildID = guild.getId();
		EmbedBuilder builder = bot.getEmbedUtil().getEmbed(member);

		builder.setAuthor(guild.getJDA().getSelfUser().getName(), guild.getJDA().getSelfUser().getEffectiveAvatarUrl())
			.setThumbnail(guild.getJDA().getSelfUser().getEffectiveAvatarUrl())
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
