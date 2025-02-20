package dev.fileeditor.votl.servlet.utils;

import dev.fileeditor.votl.utils.RandomUtil;
import io.javalin.http.Context;
import io.javalin.http.Cookie;
import io.javalin.http.SameSite;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class SessionUtil {
	private final static String SESSION_ID_KEY = "SESSION_ID";

	private static String generateSessionId() {
		return RandomUtil.randomString(30);
	}

	@NotNull
	public static String createSessionId(Context ctx) {
		final String sessionId = generateSessionId();

		var cookie = new Cookie(SESSION_ID_KEY, sessionId);
		cookie.setHttpOnly(true);
		cookie.setSecure(true);
		cookie.setMaxAge(30*60); // 30 minutes
		cookie.setSameSite(SameSite.LAX);

		ctx.cookie(cookie);
		return sessionId;
	}

	@Nullable
	public static String getSessionId(Context ctx) {
		return ctx.cookie(SESSION_ID_KEY);
	}

	public static boolean hasValidSession(Context ctx) {
		return getSessionId(ctx) != null;
	}

	public static void invalidateSession(Context ctx) {
		ctx.removeCookie(SESSION_ID_KEY);
	}
}
