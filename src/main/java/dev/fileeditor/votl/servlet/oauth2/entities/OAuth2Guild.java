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

import java.util.EnumSet;

import dev.fileeditor.votl.objects.annotation.Nonnull;
import dev.fileeditor.votl.servlet.oauth2.OAuth2Client;

import net.dv8tion.jda.api.Permission;

/**
 * OAuth2 representation of a Discord Server/Guild.
 *
 * <p>Note that this is effectively a wrapper for both the Guild info, as well
 * as the info on the user in the guild represented by the session that got this Guild.
 *
 * @author John Grosh (john.a.grosh@gmail.com)
 * @author Kaidan Gustave
 */
public class OAuth2Guild {
	private final OAuth2Client client;
	private final long id;
	private final String name, icon;
	private final boolean owner;
	private final long permissions;

	public OAuth2Guild(OAuth2Client client, long id, String name, String icon, boolean owner, long permissions) {
		this.client = client;
		this.id = id;
		this.name = name;
		this.icon = icon;
		this.owner = owner;
		this.permissions = permissions;
	}

	/**
	 * The Snowflake id of this entity. This is unique to every entity and will never change.
	 *
	 * @return Never-null String containing the Id.
	 */
	@Nonnull
	public String getId() {
		return Long.toUnsignedString(getIdLong());
	}

	/**
	 * The Snowflake id of this entity. This is unique to every entity and will never change.
	 *
	 * @return Long containing the Id.
	 */
	public long getIdLong() {
		return id;
	}
	
	/**
	 * Gets the underlying {@link com.jagrosh.jdautilities.oauth2.OAuth2Client OAuth2Client}
	 * that created this OAuth2Guild.
	 *
	 * @return The OAuth2Client that created this OAuth2Guild.
	 */
	public OAuth2Client getClient() {
		return client;
	}

	/**
	 * Gets the Guild's name.
	 *
	 * @return The Guild's name.
	 */
	public String getName() {
		return name;
	}

	/**
	 * Gets the Guild's icon ID, or {@code null} if the Guild does not have an icon.
	 *
	 * @return The Guild's icon ID.
	 */
	public String getIconId() {
		return icon;
	}

	/**
	 * Gets the Guild's icon URL, or {@code null} if the Guild does not have an icon.
	 *
	 * @return The Guild's icon URL.
	 */
	public String getIconUrl() {
		return icon == null ? null : "https://cdn.discordapp.com/icons/" + id + "/" + icon + ".png";
	}

	/**
	 * Gets the Session User's raw permission value for the Guild.
	 *
	 * @return The Session User's raw permission value for the Guild.
	 */
	public long getPermissionsRaw() {
		return permissions;
	}

	/**
	 * Gets the Session User's {@link net.dv8tion.jda.api.Permission Permissions} for the Guild.
	 *
	 * @return The Session User's Permissions for the Guild.
	 */
	public EnumSet<Permission> getPermissions() {
		return Permission.getPermissions(permissions);
	}

	/**
	 * Whether or not the Session User is the owner of the Guild.
	 *
	 * @return {@code true} if the Session User is the owner of
	 *         the Guild, {@code false} otherwise.
	 */
	public boolean isOwner() {
		return owner;
	}

	/**
	 * Whether or not the Session User has all of the specified
	 * {@link net.dv8tion.jda.api.Permission Permissions} in the Guild.
	 *
	 * @param  perms
	 *         The Permissions to check for.
	 *
	 * @return {@code true} if and only if the Session User has all of the
	 *         specified Permissions, {@code false} otherwise.
	 */
	public boolean hasPermission(Permission... perms) {
		if(isOwner())
			return true;

		long adminPermRaw = Permission.ADMINISTRATOR.getRawValue();
		long permissions = getPermissionsRaw();

		if ((permissions & adminPermRaw) == adminPermRaw)
			return true;

		long checkPermsRaw = Permission.getRaw(perms);

		return (permissions & checkPermsRaw) == checkPermsRaw;
	}

}