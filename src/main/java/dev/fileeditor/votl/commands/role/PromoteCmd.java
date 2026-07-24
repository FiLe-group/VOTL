package dev.fileeditor.votl.commands.role;

public class PromoteCmd extends RankStepCmd {

	public PromoteCmd() {
		super("promote", "bot.roles.promote");
	}

	@Override
	protected boolean promote() {
		return true;
	}
}
