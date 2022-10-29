package bot.commands.owner;

import com.jagrosh.jdautilities.doc.standard.CommandInfo;

import bot.App;
import bot.objects.command.SlashCommand;
import bot.objects.command.SlashCommandEvent;
import bot.objects.constants.CmdCategory;
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
		this.helpPath = "bot.owner.shutdown.help";
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
