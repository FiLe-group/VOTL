package votl.commands;

import votl.App;
import votl.objects.command.SlashCommand;

public abstract class CommandBase extends SlashCommand {
	
	public CommandBase(App bot) {
		this.bot = bot;
		this.lu = bot.getLocaleUtil();
	}
}
