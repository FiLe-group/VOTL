package dev.fileeditor.votl.scheduler.jobs;

import ch.qos.logback.classic.Logger;
import dev.fileeditor.votl.App;
import dev.fileeditor.votl.contracts.scheduler.Job;
import net.dv8tion.jda.api.exceptions.ErrorHandler;
import org.slf4j.LoggerFactory;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import static dev.fileeditor.votl.metrics.Metrics.TIMESERIES_INTERVAL;
import static dev.fileeditor.votl.metrics.Metrics.pingDataStore;

public class ScheduledMetrics extends Job {

	private static final Logger LOG = (Logger) LoggerFactory.getLogger(ScheduledMetrics.class);

	public ScheduledMetrics(App bot) {
		super(bot, 1, TIMESERIES_INTERVAL);
	}

	@Override
	public void run() {
		final long wsPing = bot.JDA.getGatewayPing();
		bot.JDA.getRestPing().timeout(10, TimeUnit.SECONDS).queue(restPing -> {
			// Log
			LOG.debug("WebSocket Ping: {} ms; Rest Ping: {} ms", wsPing, restPing);
			// Save data
			pingDataStore.addRecord(wsPing, restPing);
		}, _ ->
			new ErrorHandler().handle(TimeoutException.class, _ -> {
				LOG.warn("WebSocket Ping: {} ms; Rest Ping: >10000 ms!", wsPing);
				// Save data
				pingDataStore.addRecord(wsPing, 10_000);
			})
		);
	}

}
