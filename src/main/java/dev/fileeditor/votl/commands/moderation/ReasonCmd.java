package dev.fileeditor.votl.commands.moderation;

import java.util.ArrayList;
import java.util.List;
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
import dev.fileeditor.votl.objects.constants.Constants;

public class ReasonCmd extends CommandBase {
	
	public ReasonCmd(App bot) {
		super(bot);
		this.name = "reason";
		this.path = "bot.moderation.reason";
		List<OptionData> options = new ArrayList<>();
		options.add(new OptionData(OptionType.INTEGER, "id", lu.getText(path+".option_id"), true).setMinValue(0));
		options.add(new OptionData(OptionType.STRING, "reason", lu.getText(path+".option_reason"), true).setMaxLength(400));
		this.options = options;
		this.category = CmdCategory.MODERATION;
		this.module = CmdModule.MODERATION;
		this.accessLevel = CmdAccessLevel.MOD;
		this.mustSetup = true;
	}

	@Override
	protected void execute(SlashCommandEvent event) {
		Integer caseId = event.optInteger("id", 0);
		Map<String, Object> banData = bot.getDBUtil().ban.getInfo(caseId);
		if (banData.isEmpty() || !event.getGuild().getId().equals(banData.get("guildId").toString())) {
			createError(event, path+".not_found");
			return;
		}

		String newReason = event.optString("reason");
		bot.getDBUtil().ban.updateReason(caseId, newReason);

		MessageEmbed embed = bot.getEmbedUtil().getEmbed(event)
			.setColor(Constants.COLOR_SUCCESS)
			.setDescription(lu.getText(event, path+".done").replace("{reason}", newReason))
			.build();
		createReplyEmbed(event, embed);

		bot.getLogListener().onChangeReason(event, caseId, banData.get("reason").toString(), newReason);
	}
}
