package dev.fileeditor.votl.commands.other;

import java.util.Collections;

import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.JDAInfo;
import net.dv8tion.jda.api.interactions.DiscordLocale;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;

import com.jagrosh.jdautilities.commons.JDAUtilitiesInfo;

import dev.fileeditor.votl.App;
import dev.fileeditor.votl.commands.CommandBase;
import dev.fileeditor.votl.objects.command.SlashCommandEvent;
import dev.fileeditor.votl.objects.constants.CmdCategory;
import dev.fileeditor.votl.objects.constants.Links;

public class AboutCmd extends CommandBase {

	public AboutCmd(App bot) {
		super(bot);
		this.name = "about";
		this.path = "bot.other.about";
		this.options = Collections.singletonList(
			new OptionData(OptionType.BOOLEAN, "show", lu.getText("misc.show_description"))
		);
		this.category = CmdCategory.OTHER;
		this.guildOnly = false;
	}

	@Override
	protected void execute(SlashCommandEvent event) {
		DiscordLocale userLocale = event.getUserLocale();
		EmbedBuilder builder = null;

		if (event.isFromGuild()) {
			builder = bot.getEmbedUtil().getEmbed(event);
		} else {
			builder = bot.getEmbedUtil().getEmbed();
		}

		builder.setAuthor(event.getJDA().getSelfUser().getName(), event.getJDA().getSelfUser().getEffectiveAvatarUrl())
			.setThumbnail(event.getJDA().getSelfUser().getEffectiveAvatarUrl())
			.addField(
				lu.getLocalized(userLocale, "bot.other.about.embed.about_title"),
				lu.getLocalized(userLocale, "bot.other.about.embed.about_value"),
				false
			)
			.addField(
				lu.getLocalized(userLocale, "bot.other.about.embed.commands_title"),
				lu.getLocalized(userLocale, "bot.other.about.embed.commands_value"),
				false
			)
			.addField(
				lu.getLocalized(userLocale, "bot.other.about.embed.bot_info.title"),
				String.join(
					"\n",
					lu.getLocalized(userLocale, "bot.other.about.embed.bot_info.bot_version"),
					lu.getLocalized(userLocale, "bot.other.about.embed.bot_info.library")
						.replace("{jda_version}", JDAInfo.VERSION_MAJOR+"."+JDAInfo.VERSION_MINOR+"."+JDAInfo.VERSION_REVISION+"-"+JDAInfo.VERSION_CLASSIFIER)
						.replace("{jda_github}", JDAInfo.GITHUB)
						.replace("{chewtils_version}", JDAUtilitiesInfo.VERSION_MAJOR+"."+JDAUtilitiesInfo.VERSION_MINOR)
						.replace("{chewtils_github}", Links.CHEWTILS_GITHUB)
				),
				false
			)
			.addField(
				lu.getLocalized(userLocale, "bot.other.about.embed.links.title"),
				String.join(
					"\n",
					lu.getLocalized(userLocale, "bot.other.about.embed.links.discord"),
					lu.getLocalized(userLocale, "bot.other.about.embed.links.github").replace("{github_url}", Links.GITHUB),
					lu.getLocalized(userLocale, "bot.other.about.embed.links.privacy").replace("{privacy}", Links.PRIVACY),
					lu.getLocalized(userLocale, "bot.other.about.embed.links.terms").replace("{terms}", Links.TERMS)
				),
				true
			)
			.addField(
				lu.getLocalized(userLocale, "bot.other.about.embed.links.unionteams_title"),
				String.join(
					"\n",
					lu.getLocalized(userLocale, "bot.other.about.embed.links.unionteams_website").replace("{unionteams}", Links.UNIONTEAMS),
					lu.getLocalized(userLocale, "bot.other.about.embed.links.rotr").replace("{rotr_invite}", Links.ROTR_INVITE),
					lu.getLocalized(userLocale, "bot.other.about.embed.links.ww2").replace("{ww2_invite}", Links.WW2_INVITE)
				),
				true
			);
		
		createReplyEmbed(event, event.isFromGuild() ? !event.optBoolean("show", false) : false, builder.build());
	}

}
