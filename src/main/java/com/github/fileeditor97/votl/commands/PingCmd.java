package com.github.fileeditor97.votl.commands;

import com.jagrosh.jdautilities.doc.standard.CommandInfo;

import com.github.fileeditor97.votl.App;
import com.github.fileeditor97.votl.objects.command.SlashCommand;
import com.github.fileeditor97.votl.objects.command.SlashCommandEvent;
import com.github.fileeditor97.votl.objects.constants.CmdCategory;

@CommandInfo
(
	name = "Ping",
	description = "Checks the bot's latency.",
	usage = "/ping",
	requirements = "none"
)
public class PingCmd extends SlashCommand {
	
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
			Long st = System.currentTimeMillis();
			hook.getJDA().getRestPing().queue(time -> {
				hook.editOriginal(
					bot.getLocaleUtil().getLocalized(event.getUserLocale(), "bot.other.ping.info_full")
						.replace("{ping}", String.valueOf(System.currentTimeMillis() - st))
						.replace("{websocket}", event.getJDA().getGatewayPing()+"")
						.replace("{rest}", time+"")
				).queue();
			});	
		});
	}
}
