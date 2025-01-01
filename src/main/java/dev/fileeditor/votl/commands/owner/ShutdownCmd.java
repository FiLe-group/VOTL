package dev.fileeditor.votl.commands.owner;

import dev.fileeditor.votl.base.command.SlashCommandEvent;
import dev.fileeditor.votl.commands.CommandBase;
import dev.fileeditor.votl.objects.constants.CmdCategory;
import dev.fileeditor.votl.servlet.WebServlet;
import net.dv8tion.jda.api.OnlineStatus;
import net.dv8tion.jda.api.entities.Activity;

public class ShutdownCmd extends CommandBase {

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
		event.reply("Shutting down...").queue();
		// Update presence
		event.getJDA().getPresence().setPresence(OnlineStatus.IDLE, Activity.competing("Shutting down..."));
		// Log
		bot.getAppLogger().info("Shutting down, by '{}'", event.getUser().getName());

		WebServlet.shutdown();
		event.getJDA().shutdown();

		System.exit(0);
	}
}
