package com.github.fileeditor97.votl.commands.voice;

import java.util.Collections;
import java.util.Optional;

import com.jagrosh.jdautilities.doc.standard.CommandInfo;

import com.github.fileeditor97.votl.App;
import com.github.fileeditor97.votl.objects.CmdAccessLevel;
import com.github.fileeditor97.votl.objects.CmdModule;
import com.github.fileeditor97.votl.objects.command.SlashCommand;
import com.github.fileeditor97.votl.objects.command.SlashCommandEvent;
import com.github.fileeditor97.votl.objects.constants.CmdCategory;
import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.interactions.DiscordLocale;
import net.dv8tion.jda.api.interactions.InteractionHook;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;

@CommandInfo(
	name = "SetName",
	description = "Sets default name for custom's voice channels.",
	usage = "/setname <name:String from 1 to 100>",
	requirements = "Have 'Manage server' permission"
)
public class SetNameCmd extends SlashCommand {
	
	public SetNameCmd(App bot) {
		this.bot = bot;
		this.name = "setname";
		this.path = "bot.voice.setname";
		this.options = Collections.singletonList(
			new OptionData(OptionType.STRING, "name", bot.getLocaleUtil().getText("bot.voice.setname.option_description"), true)
		);
		this.botPermissions = new Permission[]{Permission.MANAGE_SERVER};
		this.category = CmdCategory.VOICE;
		this.module = CmdModule.VOICE;
		this.accessLevel = CmdAccessLevel.ADMIN;
		this.mustSetup = true;
	}

	@Override
	protected void execute(SlashCommandEvent event) {

		event.deferReply(true).queue(
			hook -> {
				String filName = event.getOption("name", "Default name", OptionMapping::getAsString).trim();
				sendReply(event, hook, filName);
			}
		);

	}

	private void sendReply(SlashCommandEvent event, InteractionHook hook, String filName) {

		if (filName.isEmpty() || filName.length() > 100) {
			hook.editOriginal(bot.getEmbedUtil().getError(event, "bot.voice.setname.invalid_range")).queue();
			return;
		}

		String guildId = Optional.ofNullable(event.getGuild()).map(g -> g.getId()).orElse("0");
		DiscordLocale userLocale = event.getUserLocale();

		bot.getDBUtil().guildVoiceSetName(guildId, filName);

		hook.editOriginalEmbeds(
			bot.getEmbedUtil().getEmbed(event)
				.setDescription(lu.getLocalized(userLocale, "bot.voice.setname.done").replace("{value}", filName))
				.build()
		).queue();
	}
}
