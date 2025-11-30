package dev.fileeditor.votl;

import ch.qos.logback.classic.ClassicConstants;
import dev.fileeditor.votl.objects.ExitCodes;
import dev.fileeditor.votl.utils.ConsoleColor;
import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.DefaultParser;
import org.apache.commons.cli.Options;
import org.apache.commons.cli.ParseException;
import org.apache.commons.cli.help.*;

import java.io.IOException;

public class Main {
	void main(String[] args) throws IOException {
		Options options = new Options()
			.addOption("h", "help", false, "Displays this help menu.")
			.addOption("v", "version", false, "Displays the current version of the application.")
			.addOption("sc", "shard-count", true, "Sets the amount of shards the bot should start up.")
			.addOption("s", "shards", true, "Sets the shard IDs that should be started up, the shard IDs should be formatted by the lowest shard ID to start up, and the highest shard ID to start up, separated by a dash.\nExample: \"--shards=4-9\" would start up shard 4, 5, 6, 7, 8, and 9.")
			.addOption("nocolor", "no-colors", false, "Disables colors for commands in the terminal.")
			.addOption("d", "debug", false, "Enables debugging mode, this will log extra information to the terminal.");

		DefaultParser parser = new DefaultParser();
		HelpFormatter formatter = HelpFormatter.builder().get();

		try {
			CommandLine cmd = parser.parse(options, args);

			Settings settings = new Settings(cmd, args);
			ConsoleColor.setSettings(settings);
			if (!settings.useColors()) {
				System.setProperty(ClassicConstants.CONFIG_FILE_PROPERTY, "logback_nocolor.xml" + (
					settings.useDebugging() ? "_debug" : ""
				) + ".xml");
			} else if (settings.useDebugging()) {
				System.setProperty(ClassicConstants.CONFIG_FILE_PROPERTY, "logback_debug.xml");
			}

			if (cmd.hasOption("help")) {
				formatter.printHelp("java --jar VOTL.jar", "Help menu", options, null, true);
				System.exit(ExitCodes.NORMAL.v);
			} else if (cmd.hasOption("version")) {
				System.out.println(AppInfo.getVersionInfo());
				System.exit(ExitCodes.NORMAL.v);
			}

			App.instance = new App(settings);
		} catch (ParseException e) {
			System.err.println(e.getMessage());
			formatter.printHelp("java --jar VOTL.jar", null, options, null, true);

			System.exit(ExitCodes.NORMAL.v);
		}
	}
}

