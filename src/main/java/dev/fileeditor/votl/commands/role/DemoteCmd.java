package dev.fileeditor.votl.commands.role;

public class DemoteCmd extends RankStepCmd {

	public DemoteCmd() {
		super("demote", "bot.roles.demote");
	}

	@Override
	protected boolean promote() {
		return false;
	}

}
