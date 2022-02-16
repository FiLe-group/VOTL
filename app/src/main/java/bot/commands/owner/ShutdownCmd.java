package bot.commands.owner;

import com.jagrosh.jdautilities.command.Command;
import com.jagrosh.jdautilities.command.CommandEvent;
import com.jagrosh.jdautilities.doc.standard.CommandInfo;

import bot.App;
import net.dv8tion.jda.api.OnlineStatus;
import net.dv8tion.jda.api.entities.Activity;

@CommandInfo
(
	name = "Shutdown",
	usage = "{prefix}shutdown",
	description = "Safely shuts down the bot.",
	requirements = {"Be the bot owner"}
)
public class ShutdownCmd extends Command {

	private final App bot;

	public ShutdownCmd(App bot) {
		this.name = "shutdown";
		this.help = "safely shuts down the bot";
		this.guildOnly = false;
		this.ownerCommand = true;
		this.category = new Category("owner");
		this.bot = bot;
	}
	
	@Override
	protected void execute(CommandEvent event) {
		event.reactWarning();
		event.getJDA().getPresence().setStatus(OnlineStatus.IDLE);
		event.getJDA().getPresence().setActivity(Activity.competing("Shutting down..."));
		bot.getLogger().info("Shutting down, by '" + event.getAuthor().getName() + "'");
		event.getJDA().shutdown();
	}
}
