package dev.fileeditor.votl.servlet.oauth2;

import io.javalin.http.util.CookieStore;

public class Session {
	/**
     * The session's access token.
     */
    private final String accessToken;

	/**
     * The session's token type.
     */
    private final String tokenType;

	public Session(String accessToken, String tokenType) {
		this.accessToken = accessToken;
		this.tokenType = tokenType;
	}

    public Session(CookieStore cs) {
		this.accessToken = cs.get("access_token");
		this.tokenType = cs.get("token_type");
	}

	/**
     * Gets the session access token.
     *
     * @return The session access token.
     */
    public String getAccessToken()
    {
        return accessToken;
    }

	/**
     * Gets the session token type.
     *
     * @return The session token type.
     */
    public String getTokenType()
    {
        return tokenType;
    }

	@Override
    public String toString()
    {
        return String.format("SessionData(access-token: %s, type: %s)",
            getAccessToken(), getTokenType());
    }
}
