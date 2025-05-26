package dev.fileeditor.votl.commands.owner;

import dev.fileeditor.votl.base.command.SlashCommand;
import dev.fileeditor.votl.base.command.SlashCommandEvent;
import dev.fileeditor.votl.objects.CmdAccessLevel;
import dev.fileeditor.votl.objects.ExitCodes;
import dev.fileeditor.votl.objects.constants.CmdCategory;

import net.dv8tion.jda.api.OnlineStatus;
import net.dv8tion.jda.api.entities.Activity;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;

public class ShutdownCmd extends SlashCommand {
	public ShutdownCmd() {
		this.name = "shutdown";
		this.path = "bot.owner.shutdown";
		this.category = CmdCategory.OWNER;
		this.accessLevel = CmdAccessLevel.DEV;
		this.guildOnly = false;
		this.options = List.of(
			new OptionData(OptionType.BOOLEAN, "now", lu.getText(path+".now.help")),
			new OptionData(OptionType.INTEGER, "status", lu.getText(path+".status.help"))
				.addChoice("Shutdown (default)", ExitCodes.NORMAL.code)
				.addChoice("Restart", ExitCodes.RESTART.code)
				.addChoice("Update", ExitCodes.UPDATE.code)
		);
	}
	
	@Override
	protected void execute(SlashCommandEvent event) {
		ExitCodes exitCode = ExitCodes.fromInt(event.optInteger("status", 0));
		String text = text(exitCode);
		if (event.optBoolean("now", false)) {
			// Reply
			event.getHook().editOriginal("%s...".formatted(text))
				.submit()
				.whenComplete((v,e) -> bot.shutdown(exitCode));
			// Update presence
			event.getJDA().getPresence().setPresence(OnlineStatus.IDLE, Activity.competing("%s...".formatted(text)));
		} else {
			// Reply
			event.getHook().editOriginal("%s in 5 minutes.".formatted(text))
				.submit()
				.whenComplete((v,e) -> bot.shutdown(exitCode));
			// Update presence
			event.getJDA().getPresence().setPresence(OnlineStatus.IDLE, Activity.competing("Preparing to shutdown"));

			bot.scheduleShutdown(Instant.now().plus(5, ChronoUnit.MINUTES), exitCode);
		}
	}

	private String text(ExitCodes exitCode) {
		return switch (exitCode) {
			case RESTART -> "Restarting";
			case UPDATE -> "Updating";
			default -> "Shutting down";
		};
	}
}
