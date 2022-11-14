package votl.commands.owner;

import com.jagrosh.jdautilities.doc.standard.CommandInfo;

import votl.App;
import votl.objects.command.SlashCommand;
import votl.objects.command.SlashCommandEvent;
import votl.objects.constants.CmdCategory;

import net.dv8tion.jda.api.OnlineStatus;
import net.dv8tion.jda.api.entities.Activity;

@CommandInfo
(
	name = "Shutdown",
	usage = "/shutdown",
	description = "Safely shuts down the bot.",
	requirements = {"Be the bot's owner", "Prepare for the consequences"}
)
public class ShutdownCmd extends SlashCommand {

	public ShutdownCmd(App bot) {
		this.name = "shutdown";
		this.path = "bot.owner.shutdown";
		this.bot = bot;
		this.category = CmdCategory.OWNER;
		this.guildOnly = false;
		this.ownerCommand = true;
	}
	
	@Override
	protected void execute(SlashCommandEvent event) {
		event.reply("Shutting down...").setEphemeral(true).queue();
		event.getJDA().getPresence().setPresence(OnlineStatus.IDLE, Activity.competing("Shutting down..."));
		bot.getLogger().info("Shutting down, by '" + event.getUser().getName() + "'");
		event.getJDA().shutdown();
	}
}
