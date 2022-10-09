package bot.commands;

import java.util.Optional;

import com.jagrosh.jdautilities.command.SlashCommand;
import com.jagrosh.jdautilities.command.SlashCommandEvent;
import com.jagrosh.jdautilities.doc.standard.CommandInfo;

import bot.App;

@CommandInfo
(
	name = "Ping",
	description = "Checks the bot's latency.",
	usage = "/ping"
)
public class PingCmd extends SlashCommand {

	private final App bot;
	
	public PingCmd(App bot) {
		this.name = "ping";
		this.help = bot.getMsg("bot.other.ping.help");
		this.guildOnly = false;
		this.category = new Category("other");
		this.bot = bot;
	}

	@SuppressWarnings("null")
	@Override
	protected void execute(SlashCommandEvent event) {
		String guildId = Optional.ofNullable(event.getGuild()).map(g -> g.getId()).orElse("0");

		event.deferReply(true).queue(hook -> {
			hook.getJDA().getRestPing().queue(time -> {
				hook.editOriginal(
					bot.getMsg(guildId, "bot.other.ping.info_full")
						.replace("{ping}", "-")
						.replace("{rest}", time+"")
						.replace("{websocket}", event.getJDA().getGatewayPing()+"")
				).queue();
			});	
		});
	}
}
