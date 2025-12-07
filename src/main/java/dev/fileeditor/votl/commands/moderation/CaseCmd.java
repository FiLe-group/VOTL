package dev.fileeditor.votl.commands.moderation;

import java.util.List;

import dev.fileeditor.votl.base.command.SlashCommand;
import dev.fileeditor.votl.base.command.SlashCommandEvent;
import dev.fileeditor.votl.objects.CmdAccessLevel;
import dev.fileeditor.votl.objects.CmdModule;
import dev.fileeditor.votl.objects.constants.CmdCategory;
import dev.fileeditor.votl.utils.database.managers.CaseManager.CaseData;

import net.dv8tion.jda.api.entities.MessageEmbed;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;

public class CaseCmd extends SlashCommand {

	public CaseCmd() {
		this.name = "case";
		this.path = "bot.moderation.case";
		this.options = List.of(
			new OptionData(OptionType.INTEGER, "id", lu.getText(path+".id.help"), true)
				.setMinValue(1)
		);
		this.category = CmdCategory.MODERATION;
		this.module = CmdModule.MODERATION;
		this.accessLevel = CmdAccessLevel.MOD;
	}
	
	@Override
	protected void execute(SlashCommandEvent event) {
		assert event.getGuild() != null;
		CaseData caseData = bot.getDBUtil().cases.getInfo(event.getGuild().getIdLong(), event.optInteger("id"));
		if (caseData == null) {
			editError(event, path+".not_found");
			return;
		}
		MessageEmbed embed = bot.getLogEmbedUtil().getCaseEmbed(event.getUserLocale(), caseData);

		editEmbed(event, embed);
	}

}
