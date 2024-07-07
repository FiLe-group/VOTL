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
package dev.fileeditor.votl.servlet.oauth2.entities;

import dev.fileeditor.votl.servlet.oauth2.OAuth2Client;
import dev.fileeditor.votl.servlet.oauth2.Session;

import net.dv8tion.jda.api.sharding.ShardManager;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.entities.User;

/**
 * OAuth2 representation of a Discord User.
 * <br>More specifically, this is the User that the session is currently managing when retrieved using
 * {@link com.jagrosh.jdautilities.oauth2.OAuth2Client#getUser(Session) OAuth2Client#getUser}.
 *
 * @author John Grosh (john.a.grosh@gmail.com)
 * @author Kaidan Gustave
 */
public class OAuth2User {
	private final OAuth2Client client;
	private final Session session;
	private final long id;
	private final String name, discriminator, avatar, email;
	private final boolean verified, mfaEnabled;

	public OAuth2User(OAuth2Client client, Session session, long id, String name, String discriminator,
					  String avatar, String email, boolean verified, boolean mfaEnabled) {
		this.client = client;
		this.session = session;
		this.id = id;
		this.name = name;
		this.discriminator = discriminator;
		this.avatar = avatar;
		this.email = email;
		this.verified = verified;
		this.mfaEnabled = mfaEnabled;
	}
	
	/**
	 * Gets the underlying {@link com.jagrosh.jdautilities.oauth2.OAuth2Client OAuth2Client}
	 * that created this OAuth2User.
	 *
	 * @return The OAuth2Client that created this OAuth2User.
	 */
	public OAuth2Client getClient() {
		return client;
	}


	/**
	 * Gets the originating {@link com.jagrosh.jdautilities.oauth2.Session}
	 * that is responsible for this OAuth2User.
	 *
	 * @return The Session responsible for this OAuth2User.
	 */
	public Session getSession() {
		return session;
	}

	/**
	 * Gets the user's Snowflake ID as a String.
	 *
	 * @return The user's Snowflake ID as a String.
	 */
	public String getId() {
		return Long.toUnsignedString(id);
	}

	/**
	 * Gets the user's Snowflake ID as a {@code long}.
	 *
	 * @return The user's Snowflake ID as a {@code long}.
	 */
	public long getIdLong() {
		return id;
	}

	/**
	 * Gets the user's account name.
	 *
	 * @return The user's account name.
	 */
	public String getName() {
		return name;
	}

	/**
	 * Gets the user's email address that is associated with their Discord account.
	 *
	 * @return The user's email.
	 *
	 */
	public String getEmail() {
		return email;
	}

	/**
	 * Returns {@code true} if the user's Discord account has been verified via email.
	 *
	 * <p>This is required to send messages in guilds where certain moderation levels are used.
	 *
	 * @return {@code true} if the user has verified their account, {@code false} otherwise.
	 */
	public boolean isVerified() {
		return verified;
	}

	/**
	 * Returns {@code true} if this user has multi-factor authentication enabled.
	 *
	 * <p>Some guilds require mfa for administrative actions.
	 *
	 * @return {@code true} if the user has mfa enabled, {@code false} otherwise.
	 */
	public boolean isMfaEnabled() {
		return mfaEnabled;
	}

	/**
	 * Gets the user's discriminator.
	 *
	 * @return The user's discriminator.
	 */
	public String getDiscriminator() {
		return discriminator;
	}

	/**
	 * Gets the user's avatar ID, or {@code null} if they have not set one.
	 *
	 * @return The user's avatar ID, or {@code null} if they have not set one.
	 */
	public String getAvatarId() {
		return avatar;
	}

	/**
	 * Gets the user's avatar URL, or {@code null} if they have not set one.
	 *
	 * @return The user's avatar URL, or {@code null} if they have not set one.
	 */
	public String getAvatarUrl() {
		return getAvatarId() == null ? null : "https://cdn.discordapp.com/avatars/" + getId() + "/" + getAvatarId()
			+ (getAvatarId().startsWith("a_") ? ".gif" : ".png");
	}

	/**
	 * Gets the user's avatar URL.
	 *
	 * @return The user's avatar URL.
	 */
	public String getDefaultAvatarId() {
		return DEFAULT_AVATARS[Integer.parseInt(getDiscriminator()) % DEFAULT_AVATARS.length];
	}

	/**
	 * Gets the user's default avatar ID.
	 *
	 * @return The user's default avatar ID.
	 */
	public String getDefaultAvatarUrl() {
		return "https://discord.com/assets/" + getDefaultAvatarId() + ".png";
	}

	/**
	 * Gets the user's avatar URL, or their {@link #getDefaultAvatarUrl() default avatar URL}
	 * if they do not have a custom avatar set on their account.
	 *
	 * @return The user's effective avatar URL.
	 */
	public String getEffectiveAvatarUrl() {
		return getAvatarUrl() == null ? getDefaultAvatarUrl() : getAvatarUrl();
	}

	/**
	 * Gets the user as a discord formatted mention:
	 * <br>{@code <@SNOWFLAKE_ID> }
	 *
	 * @return A discord formatted mention of this user.
	 */
	public String getAsMention() {
		return "<@" + id + '>';
	}

	/**
	 * Gets the corresponding {@link net.dv8tion.jda.api.entities.User JDA User}
	 * from the provided instance of {@link net.dv8tion.jda.api.JDA JDA}.
	 *
	 * <p>Note that there is no guarantee that this will not return {@code null}
	 * as the instance of JDA may not have access to the User.
	 *
	 * <p>For sharded bots, use {@link OAuth2User#getJDAUser(ShardManager)}.
	 *
	 * @param  jda
	 *         The instance of JDA to get from.
	 *
	 * @return A JDA User, possibly {@code null}.
	 */
	public User getJDAUser(JDA jda) {
		return jda.getUserById(id);
	}

	/**
	 * Gets the corresponding {@link net.dv8tion.jda.api.entities.User JDA User}
	 * from the provided {@link net.dv8tion.jda.api.sharding.ShardManager ShardManager}.
	 *
	 * <p>Note that there is no guarantee that this will not return {@code null}
	 * as the ShardManager may not have access to the User.
	 *
	 * <p>For un-sharded bots, use {@link OAuth2User#getJDAUser(JDA)}.
	 *
	 * @param  shardManager
	 *         The ShardManager to get from.
	 *
	 * @return A JDA User, possibly {@code null}.
	 */
	public User getJDAUser(ShardManager shardManager) {
		return shardManager.getUserById(id);
	}

	@Override
	public boolean equals(Object o) {
		if (!(o instanceof OAuth2User))
			return false;
		OAuth2User oUser = (OAuth2User) o;
		return this == oUser || this.id == oUser.id;
	}

	@Override
	public int hashCode() {
		return Long.hashCode(id);
	}

	@Override
	public String toString() {
		return "U:" + getName() + '(' + id + ')';
	}

	private static final String[] DEFAULT_AVATARS = new String[] {
		"6debd47ed13483642cf09e832ed0bc1b",
		"322c936a8c8be1b803cd94861bdfa868",
		"dd4dbc0016779df1378e7812eabaa04d",
		"0e291f67c9274a1abdddeb3fd919cbaa",
		"1cbd08c76f8af6dddce02c5138971129"
	};
}