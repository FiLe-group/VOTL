package bot.commands.owner;

import com.jagrosh.jdautilities.command.Command;
import com.jagrosh.jdautilities.command.CommandEvent;
import com.jagrosh.jdautilities.doc.standard.CommandInfo;

import net.dv8tion.jda.api.entities.Activity;

@CommandInfo(
    name = "Shutdown",
    description = "Safely shuts down the bot."
)
public class ShutdownCmd extends Command {

    public ShutdownCmd(Category cat) {
        this.name = "shutdown";
        this.help = "safely shuts down the bot";
        this.guildOnly = false;
        this.ownerCommand = true;
        this.category = cat;
    }
    
    @Override
    protected void execute(CommandEvent event) {
        event.reactWarning();
        event.getJDA().getPresence().setActivity(Activity.competing("Shutting down..."));
        event.getJDA().shutdown();
    }
}
