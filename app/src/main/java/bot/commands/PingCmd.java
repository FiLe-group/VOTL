package bot.commands;

import java.time.temporal.ChronoUnit;

import com.jagrosh.jdautilities.command.Command;
import com.jagrosh.jdautilities.command.CommandEvent;
import com.jagrosh.jdautilities.doc.standard.CommandInfo;

import bot.App;

@CommandInfo
(
	name = {"Ping", "Pong"},
	description = "Checks the bot's latency.",
	usage = "{prefix}ping"
)
public class PingCmd extends Command {

	private final App bot;
	
	public PingCmd(App bot) {
		this.name = "ping";
		this.aliases = new String[]{"pong"};
		this.help = "checks the bot's latency";
		this.guildOnly = false;
		this.category = new Category("other");
		this.bot = bot;
	}

	@Override
	protected void execute(CommandEvent event) {
		String guildID = (event.getEvent().isFromGuild() ? event.getGuild().getId() : "0");

		event.reply(bot.getMsg(guildID, "bot.other.ping.loading"), m -> {
			long ping = event.getMessage().getTimeCreated().until(m.getTimeCreated(), ChronoUnit.MILLIS);
			m.editMessage(
				bot.getMsg(guildID, "bot.other.ping.info")
					.replace("{ping}", ping+"")
					.replace("{websocket}", event.getJDA().getGatewayPing()+"")
			).queue();
		});
	}
}
