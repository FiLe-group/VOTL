package dev.fileeditor.votl.utils;

import org.jetbrains.annotations.NotNull;

import java.security.SecureRandom;
import java.util.List;

public class RandomUtil {

	private static final SecureRandom random = new SecureRandom();

	private final static String CHARACTERS = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789";

	public static int getInteger(int bound) {
		if (bound <= 0) {
			return 0;
		}

		return random.nextInt(bound);
	}

	@NotNull
	public static String pickRandom(@NotNull String... strings) {
		return strings[random.nextInt(strings.length)];
	}

	@NotNull
	public static Object pickRandom(@NotNull List<?> strings) {
		return strings.get(random.nextInt(strings.size()));
	}

	@NotNull
	public static String randomString(int length) {
		if (length <= 0)
			throw new IllegalArgumentException("Length must be greater than 0");
		StringBuilder builder = new StringBuilder(length);
		for (int i = 0; i < length; i++) {
			builder.append(CHARACTERS.charAt(random.nextInt(CHARACTERS.length())));
		}
		return builder.toString();
	}
}
