package dev.fileeditor.votl.commands.ticketing;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.List;

import dev.fileeditor.votl.base.command.SlashCommand;
import dev.fileeditor.votl.base.command.SlashCommandEvent;
import dev.fileeditor.votl.objects.CmdAccessLevel;
import dev.fileeditor.votl.objects.CmdModule;
import dev.fileeditor.votl.objects.constants.CmdCategory;

import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;

public class TicketCountCmd extends SlashCommand {
	public TicketCountCmd() {
		this.name = "tcount";
		this.path = "bot.ticketing.tcount";
		this.options = List.of(new OptionData(OptionType.USER, "user", lu.getText(path+".user.help"), true),
			new OptionData(OptionType.STRING, "start_date", lu.getText(path+".start_date.help"))
				.setRequiredLength(10, 10),
			new OptionData(OptionType.STRING, "end_date", lu.getText(path+".end_date.help"))
				.setRequiredLength(10, 10)
		);
		this.module = CmdModule.TICKETING;
		this.category = CmdCategory.TICKETING;
		this.accessLevel = CmdAccessLevel.MOD;
	}

	@Override
	protected void execute(SlashCommandEvent event) {
		assert event.getGuild() != null;
		String afterDate = event.optString("start_date");
		String beforeDate = event.optString("end_date");
		Instant afterTime = null;
		Instant beforeTime = null;

		DateTimeFormatter inputFormatter = DateTimeFormatter.ofPattern("dd/MM/yyyy");
		try {
			if (afterDate != null) afterTime = LocalDate.parse(afterDate, inputFormatter).atStartOfDay(ZoneOffset.UTC).toInstant();
			if (beforeDate != null) beforeTime = LocalDate.parse(beforeDate, inputFormatter).atStartOfDay(ZoneOffset.UTC).toInstant();
		} catch (Exception ex) {
			editError(event, path+".failed_parse", ex.getMessage());
			return;
		}

		if (beforeTime == null) beforeTime = Instant.now();
		if (afterTime == null) afterTime = Instant.now().minus(7, ChronoUnit.DAYS);
		if (beforeTime.isBefore(afterTime)) {
			editError(event, path+".wrong_date");
			return;
		}

		User user = event.optUser("user");
		assert user != null;
		int countRoles = bot.getDBUtil().tickets.countTicketsByMod(
			event.getGuild().getIdLong(), user.getIdLong(), afterTime.getEpochSecond(), beforeTime.getEpochSecond(), true
		);
		int countOther = bot.getDBUtil().tickets.countTicketsByMod(
			event.getGuild().getIdLong(), user.getIdLong(), afterTime.getEpochSecond(), beforeTime.getEpochSecond(), false
		);

		DateTimeFormatter formatter = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm").withZone(ZoneOffset.UTC);
		editEmbed(event, bot.getEmbedUtil().getEmbed()
			.setTitle("`"+formatter.format(afterTime)+"` - `"+formatter.format(beforeTime)+"`")
			.setDescription(lu.getGuildText(event, path+".done",
				user.getAsMention(), user.getId(), countRoles, countOther
			))
			.build());
	}
}
