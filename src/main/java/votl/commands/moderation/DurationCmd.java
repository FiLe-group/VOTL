package votl.commands.moderation;

import java.time.Duration;
import java.time.Instant;
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
import votl.utils.exception.FormatterException;

import net.dv8tion.jda.api.entities.MessageEmbed;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;

public class DurationCmd extends CommandBase {
	
	public DurationCmd(App bot) {
		super(bot);
		this.name = "duration";
		this.path = "bot.moderation.duration";
		List<OptionData> options = new ArrayList<>();
		options.add(new OptionData(OptionType.INTEGER, "id", lu.getText(path+".option_id"), true).setMinValue(0));
		options.add(new OptionData(OptionType.STRING, "time", lu.getText(path+".option_time"), true).setMaxLength(20));
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

		final Duration duration;
		try {
			duration = bot.getTimeUtil().stringToDuration(event.optString("time"), false);
		} catch (FormatterException ex) {
			createError(event, ex.getPath());
			return;
		}

		if (bot.getDBUtil().ban.utils.isPermament(banData)) {
			bot.getDBUtil().ban.updateDuration(caseId, duration);
			bot.getDBUtil().ban.setExpirable(caseId);
		} else {
			if (bot.getDBUtil().ban.utils.isExpirable(banData)) {
				bot.getDBUtil().ban.updateDuration(caseId, duration);
			} else {
				createError(event, path+".is_expired");
				return;
			}
		}

		Instant timeStart = Instant.parse(banData.get("timeStart").toString());
		String newTime = duration.isZero() ? lu.getText(event, "bot.moderation.embeds.permanently") : lu.getText(event, "bot.moderation.embeds.temporary")
			.replace("{time}", bot.getTimeUtil().formatTime(timeStart.plus(duration), false));
		MessageEmbed embed = bot.getEmbedUtil().getEmbed(event)
			.setColor(Constants.COLOR_SUCCESS)
			.setDescription(lu.getText(event, path+".done").replace("{duration}", newTime))
			.build();
		createReplyEmbed(event, embed);

		bot.getLogListener().onChangeDuration(event, caseId, timeStart, Duration.parse(banData.get("duration").toString()), newTime);
	}
}
