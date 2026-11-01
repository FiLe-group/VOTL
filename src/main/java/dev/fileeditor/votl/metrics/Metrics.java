package dev.fileeditor.votl.metrics;

import ch.qos.logback.classic.Logger;
import dev.fileeditor.votl.metrics.core.Counter;
import dev.fileeditor.votl.metrics.core.Histogram;
import dev.fileeditor.votl.metrics.timeseries.PingData;
import dev.fileeditor.votl.metrics.timeseries.RestData;
import dev.fileeditor.votl.metrics.timeseries.SystemData;
import dev.fileeditor.votl.metrics.timeseries.UsageData;
import io.github.classgraph.ClassGraph;
import io.github.classgraph.ClassInfo;
import io.github.classgraph.ScanResult;
import org.slf4j.LoggerFactory;

import java.lang.reflect.Modifier;

public class Metrics {

	// PING
	public static final int TIMESERIES_INTERVAL = 5; // in Minutes
	public static final PingData pingDataStore = new PingData();

	// CPU and memory
	public static final SystemData systemData = new SystemData();

	// Discord REST
	public static final RestData restData = new RestData();

	// Commands and interactions
	public static final UsageData usageData = new UsageData();

	// GLOBAL

	public static final Counter jdaEvents = Counter.builder()
		.name("votl_jda_events_received_total")
		.help("All events that JDA provided.")
		.build();

	// COMMANDS

	public static final Counter commandsTerminated = Counter.builder()
		.name("votl_commands_terminated_total")
		.help("Total terminated commands.")
		.build();

	public static final Counter commandsReceived = Counter.builder()
		.name("votl_commands_received_total")
		.help("Total received commands.")
		.build();

	public static final Counter commandsExecuted = Counter.builder()
		.name("votl_commands_executed_total")
		.help("Total executed commands by name.")
		.build();

	public static final Histogram executionTime = Histogram.builder()
		.name("votl_command_execution_duration_seconds")
		.help("Command execution time, excluding handling terminated commands.")
		.build();

	public static final Counter commandExceptions = Counter.builder()
		.name("votl_commands_exceptions_total")
		.help("Total uncaught exceptions thrown by command invocation.")
		.build();

	// INTERACTIONS

	public static final Counter interactionReceived = Counter.builder()
		.name("votl_interactions_received_total")
		.help("Total received interactions.")
		.build();

	// DATABASE

	public static final Counter databaseLiteQueries = Counter.builder()
		.name("votl_database_lite_queries")
		.help("Total prepared SQLite statements created for the given type")
		.build();

	// DISCORD REST

	public static final Counter restRequests = Counter.builder()
		.name("votl_rest_requests")
		.help("Discord REST requests sent, including retries")
		.build();

	// Labelled '<METHOD> <route>'

	public static final Counter restRateLimited = Counter.builder()
		.name("votl_rest_rate_limited_total")
		.help("Discord REST 429 responses by route, since start")
		.build();

	// SETUP

	public static final Logger LOG = (Logger) LoggerFactory.getLogger(Metrics.class);

	private static boolean isSetup = false;

	public static void setup() {
		if (isSetup) {
			throw new IllegalStateException("The metrics has already been setup!");
		}

		Metrics.initializeEventMetrics();
		GcMonitor.install();

		isSetup = true;
	}

	private static void initializeEventMetrics() {
		try (ScanResult scanResult = new ClassGraph()
			.enableClassInfo()
			.ignoreClassVisibility()
			.enableClasspath()
			.acceptPackages("net.dv8tion.jda.api.events")
			.scan()) {
			scanResult.getAllClasses().stream()
				.filter(info -> !info.isInterface() && !Modifier.isAbstract(info.getModifiers()))
				.map(ClassInfo::getSimpleName)
				.forEach(jdaEvents::initLabel);
		}
	}
}
