package dev.fileeditor.votl.scheduler.jobs;

import ch.qos.logback.classic.Logger;
import com.sun.management.OperatingSystemMXBean;
import dev.fileeditor.votl.App;
import dev.fileeditor.votl.contracts.scheduler.Job;
import dev.fileeditor.votl.metrics.GcMonitor;
import dev.fileeditor.votl.metrics.Metrics;
import dev.fileeditor.votl.metrics.MetricsRateLimiter;
import net.dv8tion.jda.api.exceptions.ErrorHandler;
import org.slf4j.LoggerFactory;

import java.lang.management.ManagementFactory;
import java.lang.management.MemoryMXBean;
import java.lang.management.MemoryUsage;
import java.lang.management.ThreadMXBean;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import static dev.fileeditor.votl.metrics.Metrics.TIMESERIES_INTERVAL;
import static dev.fileeditor.votl.metrics.Metrics.pingDataStore;

public class ScheduledMetrics extends Job {

	private static final Logger LOG = (Logger) LoggerFactory.getLogger(ScheduledMetrics.class);

	// CPU and memory
	private static final OperatingSystemMXBean OS_BEAN = (OperatingSystemMXBean) ManagementFactory.getOperatingSystemMXBean();
	private static final MemoryMXBean MEMORY_BEAN = ManagementFactory.getMemoryMXBean();
	private static final ThreadMXBean THREAD_BEAN = ManagementFactory.getThreadMXBean();

	private long lastCpuTime = -1;
	private long lastCpuSample;
	private long lastGcCount = GcMonitor.getCount();
	private long lastGcTime = GcMonitor.getTime();

	// Totals at the last record
	private long lastRateLimited = 0;
	private long lastCommands = 0;
	private long lastInteractions = 0;

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

		recordSystem();
		recordRest();
		recordUsage();
	}

	private void recordSystem() {
		// Process CPU averaged over the interval from CPU time; the first run has nothing to compare, so takes the recent load
		final long cpuTime = OS_BEAN.getProcessCpuTime();
		final long now = System.nanoTime();
		double processCpu;
		if (lastCpuTime >= 0 && cpuTime >= 0 && now > lastCpuSample) {
			processCpu = (double) (cpuTime - lastCpuTime) / (now - lastCpuSample) / OS_BEAN.getAvailableProcessors();
		} else {
			processCpu = OS_BEAN.getProcessCpuLoad();
		}
		lastCpuTime = cpuTime;
		lastCpuSample = now;
		final double systemCpu = OS_BEAN.getCpuLoad();

		final MemoryUsage heap = MEMORY_BEAN.getHeapMemoryUsage();
		final MemoryUsage nonHeap = MEMORY_BEAN.getNonHeapMemoryUsage();

		final long gcCount = GcMonitor.getCount();
		final long gcTime = GcMonitor.getTime();

		Metrics.systemData.addRecord(
			processCpu < 0 ? -1 : Math.min(processCpu, 1) * 100,
			systemCpu < 0 ? -1 : systemCpu * 100,
			heap.getUsed(), heap.getCommitted(), nonHeap.getUsed(),
			THREAD_BEAN.getThreadCount(),
			gcCount - lastGcCount, gcTime - lastGcTime, GcMonitor.takeMaxPause()
		);
		lastGcCount = gcCount;
		lastGcTime = gcTime;
	}

	private void recordRest() {
		final int requests = Long.valueOf(Metrics.restRequests.get()).intValue();
		Metrics.restRequests.reset();

		final long rateLimited = Metrics.restRateLimited.sum();
		Metrics.restData.addRecord(requests, (int) (rateLimited - lastRateLimited),
			MetricsRateLimiter.takeQueuePeak(), MetricsRateLimiter.getQueueSize());
		lastRateLimited = rateLimited;
	}

	private void recordUsage() {
		// Totals are kept since start for /metrics command-stats, so record the difference
		final long commands = Metrics.commandsReceived.sum();
		final long interactions = Metrics.interactionReceived.sum();
		Metrics.usageData.addRecord(commands - lastCommands, interactions - lastInteractions);
		lastCommands = commands;
		lastInteractions = interactions;
	}

}
