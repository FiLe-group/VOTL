package dev.fileeditor.votl.servlet.utils;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import dev.fileeditor.oauth2.Scope;
import dev.fileeditor.oauth2.session.Session;
import dev.fileeditor.oauth2.session.SessionController;
import dev.fileeditor.oauth2.session.SessionData;
import org.jetbrains.annotations.NotNull;

import java.time.OffsetDateTime;
import java.util.concurrent.TimeUnit;

public class AuthSessionController implements SessionController<AuthSessionController.AuthSession> {
	// cache
	private static final Cache<String, AuthSession> sessions = Caffeine.newBuilder()
		.expireAfterAccess(2, TimeUnit.HOURS)
		.build();

	@Override
	public AuthSession getSession(@NotNull String identifier) {
		AuthSession session = sessions.getIfPresent(identifier);
		if (session != null && session.expiration.isBefore(OffsetDateTime.now())) {
			endSession(identifier);
			return null;
		}
		return session;
	}

	@NotNull
	@Override
	public AuthSession createSession(@NotNull SessionData data) {
		AuthSession created = new AuthSession(data);
		sessions.put(data.getIdentifier(), created);
		return created;
	}

	@Override
	public void endSession(@NotNull String identifier) {
		sessions.invalidate(identifier);
	}

	public static class AuthSession implements Session {
		private final String accessToken, refreshToken, tokenType;
		private final OffsetDateTime expiration;
		private final Scope[] scopes;

		private AuthSession(SessionData data) {
			this.accessToken = data.getAccessToken();
			this.refreshToken = data.getRefreshToken();
			this.tokenType = data.getTokenType();
			this.expiration = data.getExpiration();
			this.scopes = data.getScopes();
		}

		@NotNull
		@Override
		public String getAccessToken() {
			return accessToken;
		}

		@NotNull
		@Override
		public String getRefreshToken() {
			return refreshToken;
		}

		@NotNull
		@Override
		public Scope[] getScopes() {
			return scopes;
		}

		@NotNull
		@Override
		public String getTokenType() {
			return tokenType;
		}

		@NotNull
		@Override
		public OffsetDateTime getExpiration() {
			return expiration;
		}
	}
}
