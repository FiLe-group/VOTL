package bot.commands.owner;

import com.jagrosh.jdautilities.command.SlashCommand;
import com.jagrosh.jdautilities.command.SlashCommandEvent;
import com.jagrosh.jdautilities.doc.standard.CommandInfo;

import bot.App;
import net.dv8tion.jda.api.OnlineStatus;
import net.dv8tion.jda.api.entities.Activity;

@CommandInfo
(
	name = "Shutdown",
	usage = "/shutdown",
	description = "Safely shuts down the bot.",
	requirements = {"Be the bot owner", "Prepare for the consequences"}
)
public class ShutdownCmd extends SlashCommand {

	private final App bot;

	public ShutdownCmd(App bot) {
		this.name = "shutdown";
		this.help = bot.getMsg("0", "bot.owner.shutdown.description");
		this.guildOnly = false;
		this.ownerCommand = true;
		this.category = new Category("owner");
		this.bot = bot;
	}
	
	@Override
	protected void execute(SlashCommandEvent event) {
		event.reply("Shutting down...").setEphemeral(true).queue();
		event.getJDA().getPresence().setPresence(OnlineStatus.IDLE, Activity.competing("Shutting down..."));
		bot.getLogger().info("Shutting down, by '" + event.getUser().getName() + "'");
		event.getJDA().shutdown();
	}
}
