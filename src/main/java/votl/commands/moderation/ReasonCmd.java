package votl.commands.moderation;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import votl.App;
import votl.commands.CommandBase;
import votl.objects.CmdAccessLevel;
import votl.objects.CmdModule;
import votl.objects.command.SlashCommandEvent;
import votl.objects.constants.CmdCategory;
import votl.objects.constants.Constants;

import net.dv8tion.jda.api.entities.MessageEmbed;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;

import com.jagrosh.jdautilities.doc.standard.CommandInfo;

@CommandInfo
(
	name = "reason",
	description = "change case reason",
	usage = "/reason <caseId> <newReason>"
)
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
