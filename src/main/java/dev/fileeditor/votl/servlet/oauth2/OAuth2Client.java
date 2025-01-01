/*
 * Copyright 2016-2018 John Grosh (jagrosh) & Kaidan Gustave (TheMonitorLizard)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package dev.fileeditor.votl.servlet.oauth2;

import java.io.IOException;
import java.io.InputStream;
import java.util.LinkedList;
import java.util.List;

import dev.fileeditor.votl.servlet.oauth2.entities.OAuth2Guild;
import dev.fileeditor.votl.servlet.oauth2.entities.OAuth2User;
import dev.fileeditor.votl.servlet.oauth2.requests.OAuth2Action;
import dev.fileeditor.votl.servlet.oauth2.requests.OAuth2Requester;
import dev.fileeditor.votl.servlet.oauth2.requests.OAuth2URL;

import org.json.JSONArray;
import org.json.JSONObject;
import org.json.JSONTokener;
import org.slf4j.LoggerFactory;

import net.dv8tion.jda.api.exceptions.HttpException;
import net.dv8tion.jda.api.requests.Method;
import net.dv8tion.jda.internal.utils.Checks;
import net.dv8tion.jda.internal.utils.IOUtil;

import ch.qos.logback.classic.Logger;
import okhttp3.Headers;
import okhttp3.OkHttpClient;
import okhttp3.Response;

/**
 * The central controller for OAuth2 state and session management using the Discord API.
 *
 * @author John Grosh (john.a.grosh@gmail.com)
 * @author Kaidan Gustave
 */
public class OAuth2Client {

	public static final Logger log = (Logger) LoggerFactory.getLogger(OAuth2Client.class);

	private final OAuth2Requester requester;

	public OAuth2Client() {
		this.requester = new OAuth2Requester(new OkHttpClient.Builder().build());
	}
	
	/**
	 * The REST version targeted by JDA-Utilities OAuth2.
	 */
	public static int DISCORD_REST_VERSION = 10;

	/**
	 * Requests a {@link dev.fileeditor.votl.servlet.oauth2.entities.OAuth2User OAuth2User}
	 * from the {@link dev.fileeditor.votl.servlet.oauth2.Session Session}.
	 *
	 * <p>All Sessions should handle an individual Discord User, and as such this method retrieves
	 * data on that User when the session is provided.
	 *
	 * @param  session
	 *         The Session to get a OAuth2User for.
	 *
	 * @return A {@link dev.fileeditor.votl.servlet.oauth2.requests.OAuth2Action OAuth2Action} for
	 *         the OAuth2User to be retrieved.
	 */
	public OAuth2Action<OAuth2User> getUser(Session session) {
		Checks.notNull(session, "Session");
		return new OAuth2Action<>(this, Method.GET, OAuth2URL.CURRENT_USER.compile()) {

			@Override
			protected Headers getHeaders() {
				return Headers.of("Authorization", generateAuthorizationHeader(session));
			}

			@Override
			protected OAuth2User handle(Response response) throws IOException {
				if(!response.isSuccessful())
					throw failure(response);
				JSONObject body = new JSONObject(new JSONTokener(IOUtil.getBody(response)));
				return new OAuth2User(OAuth2Client.this, session, body.getLong("id"),
					body.getString("username"), body.getString("discriminator"),
					body.optString("avatar", null), body.optString("email", null),
					body.optBoolean("verified", false), body.getBoolean("mfa_enabled"));
			}
		};
	}

	/**
	 * Requests a list of {@link dev.fileeditor.votl.servlet.oauth2.entities.OAuth2Guild OAuth2Guilds}
	 * from the {@link dev.fileeditor.votl.servlet.oauth2.Session Session}.
	 *
	 * <p>All Sessions should handle an individual Discord User, and as such this method retrieves
	 * data on all the various Discord Guilds that user is a part of when the session is provided.
	 *
	 * <p>Note that this can only be performed for Sessions who have the necessary 'guilds' scope.
	 *
	 * @param  session
	 *         The Session to get OAuth2Guilds for.
	 *
	 * @return A {@link dev.fileeditor.votl.servlet.oauth2.requests.OAuth2Action OAuth2Action} for
	 *         the OAuth2Guilds to be retrieved.
	 */
	public OAuth2Action<List<OAuth2Guild>> getGuilds(Session session) {
		Checks.notNull(session, "session");
		return new OAuth2Action<>(this, Method.GET, OAuth2URL.CURRENT_USER_GUILDS.compile()) {
			@Override
			protected Headers getHeaders() {
				return Headers.of("Authorization", generateAuthorizationHeader(session));
			}

			@Override
			protected List<OAuth2Guild> handle(Response response) throws IOException {
				if(!response.isSuccessful())
					throw failure(response);

				JSONArray body = new JSONArray(new JSONTokener(IOUtil.getBody(response)));
				List<OAuth2Guild> list = new LinkedList<>();
				JSONObject obj;
				for(int i = 0; i < body.length(); i++) {
					obj = body.getJSONObject(i);
					list.add(new OAuth2Guild(OAuth2Client.this, obj.getLong("id"),
						obj.getString("name"), obj.optString("icon", null), obj.getBoolean("owner"),
						obj.getLong("permissions")));
				}
				return list;
			}
		};
	}

	/**
	 * Gets the internal OAuth2Requester used by this OAuth2Client.
	 *
	 * @return The internal OAuth2Requester used by this OAuth2Client.
	 */
	public OAuth2Requester getRequester() {
		return requester;
	}

	protected static HttpException failure(Response response) throws IOException {
		final InputStream stream = IOUtil.getBody(response);
		final String responseBody = new String(IOUtil.readFully(stream));
		return new HttpException("Request returned failure " + response.code() + ": " + responseBody);
	}

	// Generates an authorization header 'X Y', where 'X' is the session's
	// token-type and 'Y' is the session's access token.
	private String generateAuthorizationHeader(Session session) {
		return String.format("%s %s", session.getTokenType(), session.getAccessToken());
	}
}
