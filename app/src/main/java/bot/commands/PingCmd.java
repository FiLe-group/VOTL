package bot.commands;

import com.jagrosh.jdautilities.command.SlashCommandEvent;
import com.jagrosh.jdautilities.doc.standard.CommandInfo;

import bot.App;
import bot.objects.CommandBase;
import bot.objects.constants.CmdCategory;

@CommandInfo
(
	name = "Ping",
	description = "Checks the bot's latency.",
	usage = "/ping",
	requirements = "none"
)
public class PingCmd extends CommandBase {
	
	public PingCmd(App bot) {
		this.name = "ping";
		this.helpPath = "bot.other.ping.help";
		this.bot = bot;
		this.category = CmdCategory.OTHER;
		this.guildOnly = false;
	}

	@SuppressWarnings("null")
	@Override
	protected void execute(SlashCommandEvent event) {
		event.deferReply(true).queue(hook -> {
			hook.getJDA().getRestPing().queue(time -> {
				hook.editOriginal(
					bot.getLocalized(event.getUserLocale(), "bot.other.ping.info_full")
						.replace("{ping}", "-")
						.replace("{rest}", time+"")
						.replace("{websocket}", event.getJDA().getGatewayPing()+"")
				).queue();
			});	
		});
	}
}
