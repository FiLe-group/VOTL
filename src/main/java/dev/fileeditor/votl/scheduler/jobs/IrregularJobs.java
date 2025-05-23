package dev.fileeditor.votl.scheduler.jobs;

import dev.fileeditor.votl.App;
import dev.fileeditor.votl.contracts.scheduler.Job;
import dev.fileeditor.votl.scheduler.tasks.*;

import java.util.concurrent.TimeUnit;

public class IrregularJobs extends Job {

	private final MarkTickets markTickets = new MarkTickets();
	private final CloseMarkedTickets closeMarkedTickets = new CloseMarkedTickets();
	private final CloseEmptyTickets closeEmptyTickets = new CloseEmptyTickets();
	private final RemoveTempRoles removeTempRoles = new RemoveTempRoles();
	private final RemoveExpiredStrikes removeExpiredStrikes = new RemoveExpiredStrikes();
	private final RemoveExpiredPersistentRoles removeExpiredPersistentRoles = new RemoveExpiredPersistentRoles();
	private final GenerateReport generateReport = new GenerateReport();

	public IrregularJobs(App bot) {
		super(bot, 1, 10, TimeUnit.MINUTES);
	}

	@Override
	public void run() {
		handleTask(
			markTickets,
			closeMarkedTickets,
			closeEmptyTickets,
			removeTempRoles,
			removeExpiredStrikes,
			removeExpiredPersistentRoles,
			generateReport
		);
	}
}
