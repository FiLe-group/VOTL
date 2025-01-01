package dev.fileeditor.votl.metrics;

import dev.fileeditor.votl.metrics.core.Counter;
import dev.fileeditor.votl.metrics.core.Histogram;
import io.github.classgraph.ClassGraph;
import io.github.classgraph.ClassInfo;
import io.github.classgraph.ScanResult;

import java.lang.reflect.Modifier;

public class Metrics {
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

	public static final Histogram executionTime = Histogram.builder() // commands execution time, excluding terminated ones
		.name("votl_command_execution_duration_seconds")
		.help("Command execution time, excluding handling terminated commands.")
		.build();

	public static final Counter commandExceptions = Counter.builder()
		.name("votl_commands_exceptions_total")
		.help("Total uncaught exceptions thrown by command invocation.")
		.build();

	// DATABASE

	public static final Counter databaseLiteQueries = Counter.builder()
		.name("votl_database_queries")
		.help("Total prepared statements created for the given type")
		.build();


	// SETUP

	private static boolean isSetup = false;

	public static void setup() {
		if (isSetup) {
			throw new IllegalStateException("The metrics has already been setup!");
		}

		Metrics.initializeEventMetrics();

		isSetup = true;
	}

	private static void initializeEventMetrics() {
		try (ScanResult scanResult = new ClassGraph()
			.enableAllInfo()
			.acceptPackages("net.dv8tion.jda.api.events")
			.scan()) {
			scanResult.getAllClasses().stream()
				.filter(info -> !info.isInterface() && !Modifier.isAbstract(info.getModifiers()))
				.map(ClassInfo::getSimpleName)
				.forEach(jdaEvents::initLabel);
		}
	}

}