package dev.fileeditor.votl;

import dev.fileeditor.votl.utils.ConsoleColor;
import net.dv8tion.jda.api.JDAInfo;

import java.util.Optional;

public class AppInfo {
	public static final String VERSION = Optional.ofNullable(AppInfo.class.getPackage().getImplementationVersion())
		.orElse("DEVELOPMENT");

	public static String getVersionInfo() {
		return ConsoleColor.format("""
			  _   __  ____   _____  __
			 | | / / / __ \\ /_  _/ / /
			 | |/ / / /_/ /  / /  / /__
			 |___/  \\____/  /_/  /____/
			
			Version:  %s
			JVM:      %s
			JDA:      %s
			
			Copyright (c) 2026 Kristofer "FileEditor97"
			Contact:  votl@fileeditor.dev
			"""
			.formatted(
				VERSION,
				System.getProperty("java.version"),
				JDAInfo.VERSION
			)
		);
	}
}
