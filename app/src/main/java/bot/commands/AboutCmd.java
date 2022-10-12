package bot.commands;

import java.util.Collections;
import java.util.Optional;

import com.jagrosh.jdautilities.command.SlashCommand;
import com.jagrosh.jdautilities.command.SlashCommandEvent;
import com.jagrosh.jdautilities.commons.JDAUtilitiesInfo;
import com.jagrosh.jdautilities.doc.standard.CommandInfo;

import bot.App;
import bot.objects.constants.Links;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.JDAInfo;
import net.dv8tion.jda.api.interactions.InteractionHook;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;

@CommandInfo
(
	name = "About",
	description = "Gets information about the bot.",
	usage = "/about [show?]"
)
public class AboutCmd extends SlashCommand {

	private final App bot;

	public AboutCmd(App bot) {
		this.name = "about";
		this.help = bot.getMsg("bot.other.about.help");
		this.guildOnly = false;
		this.category = new Category("other");
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
		EmbedBuilder builder = null;

		if (event.isFromGuild()) {
			builder = bot.getEmbedUtil().getEmbed(event.getMember());
		} else {
			builder = bot.getEmbedUtil().getEmbed();
		}

		builder.setAuthor(event.getJDA().getSelfUser().getName(), event.getJDA().getSelfUser().getEffectiveAvatarUrl())
			.setThumbnail(event.getJDA().getSelfUser().getEffectiveAvatarUrl())
			.addField(
				bot.getMsg(guildId, "bot.other.about.embed.about_title"),
				bot.getMsg(guildId, "bot.other.about.embed.about_value"),
				false
			)
			.addField(
				bot.getMsg(guildId, "bot.other.about.embed.commands_title"),
				bot.getMsg(guildId, "bot.other.about.embed.commands_value"),
				false
			)
			.addField(
				bot.getMsg(guildId, "bot.other.about.embed.bot_info.title"),
				String.join(
					"\n",
					bot.getMsg(guildId, "bot.other.about.embed.bot_info.bot_version"),
					bot.getMsg(guildId, "bot.other.about.embed.bot_info.library")
						.replace("{jda_version}", JDAInfo.VERSION_MAJOR+"."+JDAInfo.VERSION_MINOR+"."+JDAInfo.VERSION_REVISION+"-"+JDAInfo.VERSION_CLASSIFIER)
						.replace("{jda_github}", JDAInfo.GITHUB)
						.replace("{chewtils_version}", JDAUtilitiesInfo.VERSION_MAJOR+"."+JDAUtilitiesInfo.VERSION_MINOR)
						.replace("{chewtils_github}", Links.CHEWTILS_GITHUB)
				),
				false
			)
			.addField(
				bot.getMsg(guildId, "bot.other.about.embed.links.title"),
				String.join(
					"\n",
					bot.getMsg(guildId, "bot.other.about.embed.links.discord"),
					bot.getMsg(guildId, "bot.other.about.embed.links.github").replace("{github_url}", Links.GITHUB)
				),
				true
			)
			.addField(
				bot.getMsg(guildId, "bot.other.about.embed.links.unionteams_title"),
				String.join(
					"\n",
					bot.getMsg(guildId, "bot.other.about.embed.links.unionteams_website").replace("{unionteams}", Links.UNIONTEAMS),
					bot.getMsg(guildId, "bot.other.about.embed.links.rotr").replace("{rotr_invite}", Links.ROTR_INVITE),
					bot.getMsg(guildId, "bot.other.about.embed.links.ww2").replace("{ww2_invite}", Links.WW2_INVITE)
				),
				true
			);

		hook.editOriginalEmbeds(builder.build()).queue();
	}
}
