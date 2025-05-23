package dev.fileeditor.votl;

import dev.fileeditor.votl.utils.ConsoleColor;
import net.dv8tion.jda.api.JDAInfo;
import org.jetbrains.annotations.Nullable;

import java.util.Optional;

public class AppInfo {
	public static final String VERSION = Optional.ofNullable(AppInfo.class.getPackage().getImplementationVersion())
		.orElse("DEVELOPMENT");

	public static String getVersionInfo(@Nullable Settings settings) {
		return ConsoleColor.format("""
			▄▄▄▄▄▄▄▄▄▄▄▄▄▄▄▄▄▄▄▄▄▄▄▄▄▄▄▄
			██ ███ ██ ▄▄▄ █▄▄ ▄▄██ █████
			███ █ ███ ███ ███ ████ █████
			███▄▀▄███ ▀▀▀ ███ ████ ▀▀ ██
			▀▀▀▀▀▀▀▀▀▀▀▀▀▀▀▀▀▀▀▀▀▀▀▀▀▀▀▀
			
			Version:  %s
			JVM:      %s
			JDA:      %s
			
			"""
			.formatted(
				VERSION,
				System.getProperty("java.version"),
				JDAInfo.VERSION
			)
		);
	}
}
