package dev.fileeditor.votl.servlet.utils;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import dev.fileeditor.oauth2.Scope;
import dev.fileeditor.oauth2.session.Session;
import dev.fileeditor.oauth2.session.SessionController;
import dev.fileeditor.oauth2.session.SessionData;

import java.time.OffsetDateTime;
import java.util.concurrent.TimeUnit;

public class AuthSessionController implements SessionController<AuthSessionController.AuthSession> {
	// cache
	private static final Cache<String, AuthSession> sessions = Caffeine.newBuilder()
		.expireAfterAccess(2, TimeUnit.HOURS)
		.build();

	@Override
	public AuthSession getSession(String identifier) {
		return sessions.getIfPresent(identifier);
	}

	@Override
	public AuthSession createSession(SessionData data) {
		AuthSession created = new AuthSession(data);
		sessions.put(data.getIdentifier(), created);
		return created;
	}

	@Override
	public void endSession(String identifier) {
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

		@Override
		public String getAccessToken() {
			return accessToken;
		}

		@Override
		public String getRefreshToken() {
			return refreshToken;
		}

		@Override
		public Scope[] getScopes() {
			return scopes;
		}

		@Override
		public String getTokenType() {
			return tokenType;
		}

		@Override
		public OffsetDateTime getExpiration() {
			return expiration;
		}
	}
}
