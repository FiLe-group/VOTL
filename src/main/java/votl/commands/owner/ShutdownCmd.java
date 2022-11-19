package votl.commands.owner;

import votl.App;
import votl.commands.CommandBase;
import votl.objects.command.SlashCommandEvent;
import votl.objects.constants.CmdCategory;

import net.dv8tion.jda.api.OnlineStatus;
import net.dv8tion.jda.api.entities.Activity;

import com.jagrosh.jdautilities.doc.standard.CommandInfo;

@CommandInfo
(
	name = "Shutdown",
	usage = "/shutdown",
	description = "Safely shuts down the bot.",
	requirements = {"Be the bot's owner", "Prepare for the consequences"}
)
public class ShutdownCmd extends CommandBase {

	public ShutdownCmd(App bot) {
		super(bot);
		this.name = "shutdown";
		this.path = "bot.owner.shutdown";
		this.category = CmdCategory.OWNER;
		this.guildOnly = false;
		this.ownerCommand = true;
	}
	
	@Override
	protected void execute(SlashCommandEvent event) {
		createReply(event, "Shutting down...");
		event.getJDA().getPresence().setPresence(OnlineStatus.IDLE, Activity.competing("Shutting down..."));
		bot.getLogger().info("Shutting down, by '" + event.getUser().getName() + "'");
		event.getJDA().shutdown();
	}
}
