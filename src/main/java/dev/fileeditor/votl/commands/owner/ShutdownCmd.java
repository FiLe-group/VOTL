package dev.fileeditor.votl.commands.owner;

import dev.fileeditor.votl.base.command.SlashCommand;
import dev.fileeditor.votl.base.command.SlashCommandEvent;
import dev.fileeditor.votl.objects.constants.CmdCategory;

import dev.fileeditor.votl.scheduler.ScheduleHandler;
import net.dv8tion.jda.api.OnlineStatus;
import net.dv8tion.jda.api.entities.Activity;

public class ShutdownCmd extends SlashCommand {
	public ShutdownCmd() {
		this.name = "shutdown";
		this.path = "bot.owner.shutdown";
		this.category = CmdCategory.OWNER;
		this.ownerCommand = true;
		this.guildOnly = false;
	}
	
	@Override
	protected void execute(SlashCommandEvent event) {
		// Reply
		editMsg(event, "Shutting down...");
		// Update presence
		event.getJDA().getPresence().setPresence(OnlineStatus.IDLE, Activity.competing("Shutting down..."));
		// Log
		bot.getAppLogger().info("Shutting down, by '{}'", event.getUser().getName());
		// Shutdown
		bot.shutdownUtils();

		try {
			Thread.sleep(2000L);
		} catch (InterruptedException e) {
			System.out.println("Interrupted while shutting down: "+e.getMessage());
		}

		event.getJDA().shutdown();
		ScheduleHandler.getScheduler().shutdownNow();

		System.exit(0);
	}
}
