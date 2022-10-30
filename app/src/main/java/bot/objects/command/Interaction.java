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
package bot.objects.command;

import javax.annotation.Nonnull;

import bot.App;
import bot.objects.CmdAccessLevel;
import bot.utils.message.LocaleUtil;
import net.dv8tion.jda.api.Permission;

/**
 * A class that represents an interaction with a user.
 *
 * This is all information used for all forms of interactions. Namely, permissions and cooldowns.
 *
 * Any content here is safely functionality equivalent regardless of the source of the interaction.
 */
public abstract class Interaction
{
	/**
	 * {@code true} if the command may only be used in a {@link net.dv8tion.jda.api.entities.Guild Guild},
	 * {@code false} if it may be used in both a Guild and a DM.
	 * <br>Default {@code true}.
	 */
	protected boolean guildOnly = true;

	/**
	 * Any {@link Permission Permissions} a Member must have to use this interaction.
	 * <br>These are only checked in a {@link net.dv8tion.jda.api.entities.Guild server} environment.
	 * <br>To disable the command for everyone (for interactions), set this to {@code null}.
	 * <br>Keep in mind, commands may still show up if the channel permissions are updated in settings.
	 * Otherwise, commands will automatically be hidden unless a user has these perms.
	 * However, permissions are always checked, just in case. A user must have these permissions regardless.
	 */
	@Nonnull
	protected Permission[] userPermissions = new Permission[0];

	/**
	 * Any {@link Permission Permissions} the bot must have to use a command.
	 * <br>These are only checked in a {@link net.dv8tion.jda.api.entities.Guild server} environment.
	 */
	@Nonnull
	protected Permission[] botPermissions = new Permission[0];

	/**
	 * {@code true} if the interaction may only be used by a User with an ID matching the
	 * Owners or any of the CoOwners.<br>
	 * If enabled for a Slash Command, only owners (owner + up to 9 co-owners) will be added to the SlashCommand.
	 * All other permissions will be ignored.
	 * <br>Default {@code false}.
	 */
	protected boolean ownerCommand = false;

	/**
	 * An {@code int} number of seconds users must wait before using this command again.
	 */
	protected int cooldown = 0;

	/**
	 * The {@link CooldownScope CooldownScope}
	 * of the command. This defines how far the scope the cooldowns have.
	 * <br>Default {@link CooldownScope#USER CooldownScope.USER}.
	 */
	protected CooldownScope cooldownScope = CooldownScope.USER;

	/**
	 * The permission message used when the bot does not have the required permission.
	 * Requires 3 "%s", first is user mention, second is the permission needed, third is type, e.g. server.
	 */
	protected String botMissingPermMessage = "%s I need the %s permission in this %s!";

	/**
	 * The permission message used when the user does not have the required permission.
	 * Requires 3 "%s", first is user mention, second is the permission needed, third is type, e.g. server.
	 */
	protected String userMissingPermMessage = "%s You must have the %s permission in this %s to use that!";

	/**
	 * Gets the {@link Interaction#cooldown cooldown} for the Interaction.
	 *
	 * @return The cooldown for the Interaction
	 */
	public int getCooldown()
	{
		return cooldown;
	}

	/**
	 * Gets the {@link Interaction#cooldown cooldown} for the Interaction.
	 *
	 * @return The cooldown for the Interaction
	 */
	public CooldownScope getCooldownScope()
	{
		return cooldownScope;
	}

	/**
	 * Gets the {@link Interaction#userPermissions userPermissions} for the Interaction.
	 *
	 * @return The userPermissions for the Interaction
	 */
	@Nonnull
	public Permission[] getUserPermissions()
	{
		return userPermissions;
	}

	/**
	 * Gets the {@link Interaction#botPermissions botPermissions} for the Interaction.
	 *
	 * @return The botPermissions for the Interaction
	 */
	@Nonnull
	public Permission[] getBotPermissions()
	{
		return botPermissions;
	}

	/**
	 * Checks whether this is an owner only Interaction, meaning only the owner and co-owners can use it.
	 *
	 * @return {@code true} if the command is an owner interaction, otherwise {@code false} if it is not
	 */
	public boolean isOwnerCommand()
	{
		return ownerCommand;
	}

	/**
	 * Path to the command's help String. Must by set, otherwise 
	 */
	@Nonnull
	protected String helpPath = "misc.unknown";
	
	/**
	 * Gets the {@link bot.commands.OtherCmdBase#helpPath OtherCmdBase.helpPath} for the Command.
	 *
	 * @return The path for command's help String.
	 */
	@Nonnull
	public String getHelpPath()
	{
		return helpPath;
	}

	protected App bot = null;

	protected LocaleUtil lu = null;
	
	protected boolean mustSetup = false;

	protected String module = null;

	protected CmdAccessLevel accessLevel = CmdAccessLevel.ALL;

	public App getApp() {
		return bot;
	}

	public LocaleUtil getLocaleUtil() {
		return lu;
	}

	public CmdAccessLevel getAccessLevel() {
		return accessLevel;
	}

	public String getModule() {
		return module;
	}

	public boolean getMustSetup() {
		return mustSetup;
	}

	
}
