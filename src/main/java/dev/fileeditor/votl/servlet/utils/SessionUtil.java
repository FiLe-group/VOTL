package dev.fileeditor.votl.servlet.utils;

import dev.fileeditor.votl.utils.RandomUtil;
import io.javalin.http.Context;
import io.javalin.http.Header;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Optional;

public class SessionUtil {
	public static final String SESSION_KEY = "session";

	private static String generateSessionId() {
		return RandomUtil.randomString(40);
	}

	@NotNull
	public static String createSessionId() {
		return generateSessionId();
	}

	@Nullable
	public static String getSessionId(Context ctx) {
		return Optional.ofNullable(ctx.header(Header.AUTHORIZATION))
			.filter(v -> v.startsWith("Bearer "))
			.map(v -> v.substring(6).trim())
			.orElse(null);
	}
}
