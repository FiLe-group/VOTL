package bot.commands;

import java.time.temporal.ChronoUnit;

import com.jagrosh.jdautilities.command.Command;
import com.jagrosh.jdautilities.command.CommandEvent;
import com.jagrosh.jdautilities.doc.standard.CommandInfo;

@CommandInfo
(
    name = {"Ping", "Pong"},
    description = "Checks the bot's latency."
)
public class PingCmd extends Command {
    
    public PingCmd(Category cat) {
        this.name = "ping";
        this.aliases = new String[]{"pong"};
        this.category = cat;
        this.help = "проверить задержку бота";
        this.guildOnly = false;
    }

    @Override
    protected void execute(CommandEvent event) {
        event.reply("Ping: ...", m -> {
            long ping = event.getMessage().getTimeCreated().until(m.getTimeCreated(), ChronoUnit.MILLIS);
            m.editMessage("Ping: " + ping + "ms | Websocket: " + event.getJDA().getGatewayPing() + "ms").queue();
        });
    }
}
