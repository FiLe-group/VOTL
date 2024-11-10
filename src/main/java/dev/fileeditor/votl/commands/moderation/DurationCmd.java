package dev.fileeditor.votl.commands.moderation;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

import dev.fileeditor.votl.base.command.SlashCommandEvent;
import dev.fileeditor.votl.commands.CommandBase;
import dev.fileeditor.votl.objects.CaseType;
import dev.fileeditor.votl.objects.CmdAccessLevel;
import dev.fileeditor.votl.objects.CmdModule;
import dev.fileeditor.votl.objects.constants.CmdCategory;
import dev.fileeditor.votl.objects.constants.Constants;
import dev.fileeditor.votl.utils.database.managers.CaseManager.CaseData;
import dev.fileeditor.votl.utils.exception.FormatterException;
import dev.fileeditor.votl.utils.message.TimeUtil;

import net.dv8tion.jda.api.entities.MessageEmbed;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;

public class DurationCmd extends CommandBase {
	
	public DurationCmd() {
		this.name = "duration";
		this.path = "bot.moderation.duration";
		this.options = List.of(
			new OptionData(OptionType.INTEGER, "id", lu.getText(path+".id.help"), true).setMinValue(1),
			new OptionData(OptionType.STRING, "time", lu.getText(path+".time.help"), true).setMaxLength(20)
		);
		this.category = CmdCategory.MODERATION;
		this.module = CmdModule.MODERATION;
		this.accessLevel = CmdAccessLevel.MOD;
	}

	@Override
	protected void execute(SlashCommandEvent event) {
		event.deferReply().queue();
		CaseData caseData = bot.getDBUtil().cases.getInfo(event.getGuild().getIdLong(), event.optInteger("id"));
		if (caseData == null || event.getGuild().getIdLong() != caseData.getGuildId()) {
			editError(event, path+".not_found");
			return;
		}

		Duration newDuration;
		try {
			newDuration = TimeUtil.stringToDuration(event.optString("time"), false);
		} catch (FormatterException ex) {
			editError(event, ex.getPath());
			return;
		}

		if (!( caseData.isActive() && (caseData.getType().equals(CaseType.MUTE) || caseData.getType().equals(CaseType.BAN)) )) {
			editError(event, path+".is_expired");
			return;
		}

		if (caseData.getType().equals(CaseType.MUTE)) {
			if (newDuration.isZero()) {
				editErrorOther(event, "Duration must be larger than 1 minute.");
				return;
			}
			event.getGuild().retrieveMemberById(caseData.getTargetId()).queue(target -> {
				if (caseData.getTimeStart().plus(newDuration).isAfter(Instant.now())) {
					// time out member for new time
					target.timeoutUntil(caseData.getTimeStart().plus(newDuration)).reason("Duration change by "+event.getUser().getName()).queue();
				} else {
					// time will be expired, remove time out
					target.removeTimeout().reason("Expired").queue();
					bot.getDBUtil().cases.setInactive(caseData.getRowId());
				}
			});
		}
		if (bot.getDBUtil().cases.updateDuration(caseData.getRowId(), newDuration)) {
			editErrorDatabase(event, "update duration");
			return;
		}
		
		String newTime = TimeUtil.formatDuration(lu, event.getUserLocale(), caseData.getTimeStart(), newDuration);
		MessageEmbed embed = bot.getEmbedUtil().getEmbed(Constants.COLOR_SUCCESS)
			.setDescription(lu.getText(event, path+".done").formatted(caseData.getLocalId(), newTime))
			.build();
		editEmbed(event, embed);

		bot.getLogger().mod.onChangeDuration(event.getGuild(), caseData, event.getMember(), newTime);
	}
}
