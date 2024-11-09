package dev.fileeditor.votl.commands.owner;

import dev.fileeditor.votl.base.command.SlashCommandEvent;
import dev.fileeditor.votl.commands.CommandBase;
import dev.fileeditor.votl.objects.constants.CmdCategory;

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
		event.reply("Shutting down...").queue();
		event.getJDA().getPresence().setPresence(OnlineStatus.IDLE, Activity.competing("Shutting down..."));
		bot.getAppLogger().info("Shutting down, by '{}'", event.getUser().getName());
		event.getJDA().shutdown();
	}
}
