package dev.fileeditor.votl.scheduler.jobs;

import dev.fileeditor.votl.App;
import dev.fileeditor.votl.contracts.scheduler.Job;
import dev.fileeditor.votl.scheduler.tasks.DrainDbQueue;
import dev.fileeditor.votl.scheduler.tasks.RemoveExpiredCases;

import java.util.concurrent.TimeUnit;

@SuppressWarnings("unused")
public class RegularJobs extends Job {

	private final DrainDbQueue drainDbQueue = new DrainDbQueue();
	private final RemoveExpiredCases removeExpiredCases = new RemoveExpiredCases();

	public RegularJobs(App bot) {
		super(bot, 0, 2, TimeUnit.MINUTES);
	}

	@Override
	public void run() {
		handleTask(
			drainDbQueue,
			removeExpiredCases
		);
	}
}
