package dev.fileeditor.votl.commands.strike;

import java.util.List;

import dev.fileeditor.votl.App;
import dev.fileeditor.votl.base.command.CooldownScope;
import dev.fileeditor.votl.base.command.SlashCommandEvent;
import dev.fileeditor.votl.commands.CommandBase;
import dev.fileeditor.votl.objects.CmdAccessLevel;
import dev.fileeditor.votl.objects.CmdModule;
import dev.fileeditor.votl.objects.constants.CmdCategory;
import dev.fileeditor.votl.utils.database.managers.CaseManager.CaseData;
import dev.fileeditor.votl.utils.message.MessageUtil;

import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import net.dv8tion.jda.api.utils.TimeFormat;
import net.dv8tion.jda.internal.utils.tuple.Pair;

public class StrikesCmd extends CommandBase {
	
	public StrikesCmd(App bot) {
		super(bot);
		this.name = "strikes";
		this.path = "bot.moderation.strikes";
		this.options = List.of(
			new OptionData(OptionType.USER, "user", lu.getText(path+".user.help"))
		);
		this.category = CmdCategory.MODERATION;
		this.module = CmdModule.STRIKES;
		this.accessLevel = CmdAccessLevel.MOD;
		this.cooldown = 5;
		this.cooldownScope = CooldownScope.GUILD;
	}

	@Override
	protected void execute(SlashCommandEvent event) {
		event.deferReply(true).queue();
		
		User tu;
		if (event.hasOption("user")) {
			tu = event.optUser("user", event.getUser());
			if (!tu.equals(event.getUser()) && !bot.getCheckUtil().hasAccess(event.getMember(), CmdAccessLevel.MOD)) {
				editError(event, path+".no_perms");
				return;
			}
		} else {
			tu = event.getUser();
		}

		Pair<Integer, String> strikeData = bot.getDBUtil().strikes.getData(event.getGuild().getIdLong(), tu.getIdLong());
		if (strikeData == null) {
			editHookEmbed(event, bot.getEmbedUtil().getEmbed().setDescription(lu.getText(event, path+".no_active")).build());
			return;
		}
		String[] strikesInfoArray = strikeData.getRight().split(";");
		if (strikesInfoArray[0].isEmpty()) {
			editError(event, "errors.error", "Strikes data is empty.");
			return;
		}

		StringBuffer buffer = new StringBuffer();
		for (String c : strikesInfoArray) {
			String[] args = c.split("-");
			Integer caseId = Integer.valueOf(args[0]);
			Integer strikeAmount = Integer.valueOf(args[1]);
			CaseData caseData = bot.getDBUtil().cases.getInfo(caseId);
			buffer.append("`%4d` %s | %s - %s\nBy: %s\n".formatted(
				caseId,
				getSquares(strikeAmount, caseData.getType().getValue()-20),
				MessageUtil.limitString(caseData.getReason(), 50),
				TimeFormat.DATE_SHORT.format(caseData.getTimeStart()),
				caseData.getModTag()
			));
		};
		
		editHookEmbed(event, bot.getEmbedUtil().getEmbed()
			.setTitle(lu.getText(event, path+".title").formatted(strikeData.getLeft(), tu.getName(), tu.getId()))
			.setDescription(buffer.toString())
			.build()
		);
	}

	private String getSquares(int active, int max) {
		return "🟥".repeat(active) + "🔲".repeat(max-active);
	}

}
