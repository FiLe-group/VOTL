package bot.commands;

import com.jagrosh.jdautilities.command.SlashCommand;
import com.jagrosh.jdautilities.command.SlashCommandEvent;
import com.jagrosh.jdautilities.doc.standard.CommandInfo;

import bot.App;
import bot.objects.constants.CmdCategory;

@CommandInfo
(
	name = "Ping",
	description = "Checks the bot's latency.",
	usage = "/ping"
)
public class PingCmd extends SlashCommand {

	private final App bot;
	private final String helpPath = "bot.other.ping.help";
	
	public PingCmd(App bot) {
		this.name = "ping";
		this.help = bot.getMsg(helpPath);
		this.descriptionLocalization = bot.getFullLocaleMap(helpPath);
		this.guildOnly = false;
		this.category = CmdCategory.OTHER;
		this.bot = bot;
	}

	@SuppressWarnings("null")
	@Override
	protected void execute(SlashCommandEvent event) {
		event.deferReply(true).queue(hook -> {
			hook.getJDA().getRestPing().queue(time -> {
				hook.editOriginal(
					bot.getLocale(event.getUserLocale(), "bot.other.ping.info_full")
						.replace("{ping}", "-")
						.replace("{rest}", time+"")
						.replace("{websocket}", event.getJDA().getGatewayPing()+"")
				).queue();
			});	
		});
	}
}
