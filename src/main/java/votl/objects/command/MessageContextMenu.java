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
package votl.objects.command;

import javax.annotation.Nonnull;

import votl.utils.exception.CheckException;

import net.dv8tion.jda.api.entities.channel.ChannelType;
import net.dv8tion.jda.api.interactions.commands.DefaultMemberPermissions;
import net.dv8tion.jda.api.interactions.commands.build.CommandData;
import net.dv8tion.jda.api.interactions.commands.build.Commands;
import net.dv8tion.jda.api.utils.messages.MessageCreateData;
import net.dv8tion.jda.api.utils.messages.MessageEditData;

public abstract class MessageContextMenu extends ContextMenu
{
	/**
	 * Runs checks for the {@link MessageContextMenu} with the given {@link MessageContextMenuEvent} that called it.
	 * <br>Will terminate, and possibly respond with a failure message, if any checks fail.
	 *
	 * @param  event
	 *         The MessageContextMenuEvent that triggered this menu
	 */
	public final void run(MessageContextMenuEvent event) {
		// owner check
		if (ownerCommand && !(event.isOwner())) {
			terminate(event, bot.getEmbedUtil().getError(event, "errors.command.not_owner"));
			return;
		}

		// cooldown check, ignoring owner
		if (cooldown>0 && !(event.isOwner())) {
			String key = getCooldownKey(event);
			int remaining = event.getClient().getRemainingCooldown(key);
			if (remaining>0) {
				terminate(event, getCooldownError(event, event.getGuild(), remaining));
				return;
			}
			else event.getClient().applyCooldown(key, cooldown);
		}

		if (event.getChannelType() != ChannelType.PRIVATE) {
			try {
				// check access
				bot.getCheckUtil().hasAccess(event, getAccessLevel())
				// check module enabled
					.moduleEnabled(event, getModule())
				// check user perms
					.hasPermissions(event, getUserPermissions())
				// check bots perms
					.hasPermissions(event, true, getBotPermissions())
				// check setup
					.guildExists(event, getMustSetup());
			} catch (CheckException ex) {
				terminate(event, ex.getCreateData());
				return;
			}
		} else if (guildOnly) {
			terminate(event, bot.getEmbedUtil().getError(event, "errors.command.guild_only"));
			return;
		}

		// run
		try {
			execute(event);
		} catch(Throwable t) {
			if (event.getClient().getListener() != null)
			{
				event.getClient().getListener().onMessageContextMenuException(event, this, t);
				return;
			}
			// otherwise we rethrow
			throw t;
		}

		if (event.getClient().getListener() != null)
			event.getClient().getListener().onCompletedMessageContextMenu(event, this);
	}

	/**
	 * The main body method of a {@link MessageContextMenu}.
	 * <br>This is the "response" for a successful
	 * {@link MessageContextMenu#run(MessageContextMenuEvent)}
	 *
	 * @param  event
	 *         The {@link MessageContextMenuEvent} that triggered this menu.
	 */
	protected abstract void execute(MessageContextMenuEvent event);

	private void terminate(MessageContextMenuEvent event, @Nonnull MessageEditData message) {
		terminate(event, MessageCreateData.fromEditData(message));
	}

	private void terminate(MessageContextMenuEvent event, MessageCreateData message) {
		if (message!=null)
			event.reply(message).setEphemeral(true).queue();
		if (event.getClient().getListener()!=null)
			event.getClient().getListener().onTerminatedMessageContextMenu(event, this);
	}

	@Override
	@SuppressWarnings("null")
	public CommandData buildCommandData() {
		// Make the command data
		CommandData data = Commands.message(getName());
		if (this.userPermissions.length == 0)
			data.setDefaultPermissions(DefaultMemberPermissions.DISABLED);
		else
			data.setDefaultPermissions(DefaultMemberPermissions.enabledFor(this.userPermissions));

		data.setGuildOnly(this.guildOnly);

		return data;
	}
}
