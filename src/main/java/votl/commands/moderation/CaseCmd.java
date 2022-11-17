package votl.commands.moderation;

import votl.App;
import votl.commands.CommandBase;
import votl.objects.command.SlashCommandEvent;

public class CaseCmd extends CommandBase {

	public CaseCmd(App bot) {
		super(bot);
		this.name = "case";
		this.path = "bot.moderation.case";
	}
	
	@Override
	protected void execute(SlashCommandEvent event) {

	}
}
