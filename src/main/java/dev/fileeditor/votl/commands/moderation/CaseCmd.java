package dev.fileeditor.votl.commands.moderation;

import java.util.Collections;
import java.util.Map;

import net.dv8tion.jda.api.entities.MessageEmbed;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;

import dev.fileeditor.votl.App;
import dev.fileeditor.votl.commands.CommandBase;
import dev.fileeditor.votl.objects.CmdAccessLevel;
import dev.fileeditor.votl.objects.CmdModule;
import dev.fileeditor.votl.objects.command.SlashCommandEvent;
import dev.fileeditor.votl.objects.constants.CmdCategory;

public class CaseCmd extends CommandBase {

	public CaseCmd(App bot) {
		super(bot);
		this.name = "case";
		this.path = "bot.moderation.case";
		this.options = Collections.singletonList(
			new OptionData(OptionType.INTEGER, "id", lu.getText(path+".option_id"), true)
			.setMinValue(0)
		);
		this.category = CmdCategory.MODERATION;
		this.module = CmdModule.MODERATION;
		this.accessLevel = CmdAccessLevel.MOD;
		this.mustSetup = true;
	}
	
	@Override
	protected void execute(SlashCommandEvent event) {
		Map<String, Object> banData = bot.getDBUtil().ban.getInfo(event.optInteger("id", 0));
		if (banData.isEmpty() || !event.getGuild().getId().equals(banData.get("guildId").toString())) {
			createError(event, path+".not_found");
			return;
		}
		MessageEmbed embed = bot.getLogUtil().getBanEmbed(event.getUserLocale(), banData);

		createReplyEmbed(event, embed);
	}

}
