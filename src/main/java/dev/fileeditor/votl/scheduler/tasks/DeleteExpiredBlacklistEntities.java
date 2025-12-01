package dev.fileeditor.votl.scheduler.tasks;

import dev.fileeditor.votl.App;
import dev.fileeditor.votl.contracts.scheduler.Task;

public class DeleteExpiredBlacklistEntities implements Task {

	@Override
	public void handle(App bot) {
		if (bot.getBlacklist() == null) return;

		synchronized (bot.getBlacklist().getBlacklistEntities()) {
			bot.getBlacklist()
				.getBlacklistEntities()
				.entrySet()
				.removeIf(e -> !e.getValue().isBlacklisted());
		}
	}

}
