package com.github.fileeditor97.votl.commands;

import java.util.Collections;

import com.jagrosh.jdautilities.commons.JDAUtilitiesInfo;
import com.jagrosh.jdautilities.doc.standard.CommandInfo;

import com.github.fileeditor97.votl.App;
import com.github.fileeditor97.votl.objects.command.SlashCommand;
import com.github.fileeditor97.votl.objects.command.SlashCommandEvent;
import com.github.fileeditor97.votl.objects.constants.CmdCategory;
import com.github.fileeditor97.votl.objects.constants.Links;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.JDAInfo;
import net.dv8tion.jda.api.interactions.DiscordLocale;
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

	public AboutCmd(App bot) {
		this.name = "about";
		this.path = "bot.other.about";
		this.options = Collections.singletonList(
			new OptionData(OptionType.BOOLEAN, "show", bot.getLocaleUtil().getText("misc.show_description"))
		);
		this.bot = bot;
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
					lu.getLocalized(userLocale, "bot.other.about.embed.links.github").replace("{github_url}", Links.GITHUB)
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

		hook.editOriginalEmbeds(builder.build()).queue();
	}
}
