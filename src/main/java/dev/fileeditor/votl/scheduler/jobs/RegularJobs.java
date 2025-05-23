package dev.fileeditor.votl.scheduler.jobs;

import dev.fileeditor.votl.App;
import dev.fileeditor.votl.contracts.scheduler.Job;
import dev.fileeditor.votl.scheduler.tasks.DeleteExpiredBlacklistEntities;
import dev.fileeditor.votl.scheduler.tasks.DrainDbQueue;
import dev.fileeditor.votl.scheduler.tasks.RemoveExpiredCases;

import java.util.concurrent.TimeUnit;

public class RegularJobs extends Job {

	private final DrainDbQueue drainDbQueue = new DrainDbQueue();
	private final RemoveExpiredCases removeExpiredCases = new RemoveExpiredCases();
	private final DeleteExpiredBlacklistEntities deleteExpiredBlacklistEntities = new DeleteExpiredBlacklistEntities();

	public RegularJobs(App bot) {
		super(bot, 0, 1, TimeUnit.MINUTES);
	}

	@Override
	public void run() {
		handleTask(
			drainDbQueue,
			removeExpiredCases,
			deleteExpiredBlacklistEntities
		);
	}
}
