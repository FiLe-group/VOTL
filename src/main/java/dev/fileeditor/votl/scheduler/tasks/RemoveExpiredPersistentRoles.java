package dev.fileeditor.votl.scheduler.tasks;

import dev.fileeditor.votl.App;
import dev.fileeditor.votl.contracts.scheduler.Task;

public class RemoveExpiredPersistentRoles implements Task {

	@Override
	public void handle(App bot) {
		bot.getDBUtil().persistent.removeExpired();
	}

}
