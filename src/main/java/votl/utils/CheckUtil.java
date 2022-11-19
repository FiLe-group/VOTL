package votl.utils;

import java.util.Objects;

import votl.App;
import votl.objects.CmdAccessLevel;
import votl.objects.CmdModule;
import votl.objects.command.CommandClient;
import votl.objects.command.CommandEvent;
import votl.objects.command.MessageContextMenuEvent;
import votl.objects.command.SlashCommandEvent;
import votl.objects.constants.Constants;
import votl.utils.exception.CheckException;

import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import net.dv8tion.jda.api.utils.messages.MessageCreateData;

public class CheckUtil {

	private final App bot;

	public CheckUtil(App bot) {
		this.bot = bot;
	}

	public boolean isDeveloper(User user) {
		return user.getId().equals(Constants.DEVELOPER_ID);
	}

	public boolean isOwner(CommandClient client, User user) {
    	if (user.getId().equals(client.getOwnerId()))
    	    return true;
        if (client.getCoOwnerIds()==null)
            return false;
        for (String id : client.getCoOwnerIds())
            if (id.equals(user.getId()))
                return true;
        return false;
    }

	public CmdAccessLevel getAccessLevel(CommandClient client, Member member) {
		// Is bot developer
		if (isDeveloper(member.getUser()) || isOwner(client, member.getUser()))
			return CmdAccessLevel.DEV;
		// Is guild owner
		if (member.isOwner())
			return CmdAccessLevel.OWNER;
		
		Guild guild = Objects.requireNonNull(member.getGuild());
		String access = bot.getDBUtil().access.hasAccess(guild.getId(), member.getId());
		// Has either mod or admin access
		if (access != null) {
			// Has admin access
			if (access.equals("admin"))
				return CmdAccessLevel.ADMIN;
			return CmdAccessLevel.MOD;
		}
		// Default
		return CmdAccessLevel.ALL;
	}

	public Boolean hasHigherAccess(CommandClient client, Member who, Member than) {
		return getAccessLevel(client, who).getLevel() > getAccessLevel(client, than).getLevel();
	}

	public <T> CheckUtil hasAccess(T genericEvent, CmdAccessLevel accessLevel) throws CheckException {
		if (genericEvent instanceof SlashCommandEvent) {
			SlashCommandEvent event = (SlashCommandEvent) genericEvent;
			return hasAccess(event, event.getClient(), event.getMember(), accessLevel);
		}
		if (genericEvent instanceof CommandEvent) {
			CommandEvent event = (CommandEvent) genericEvent;
			return hasAccess(event, event.getClient(), event.getMember(), accessLevel);
		}
		if (genericEvent instanceof MessageContextMenuEvent) {
			MessageContextMenuEvent event = (MessageContextMenuEvent) genericEvent;
			return hasAccess(event, event.getClient(), event.getMember(), accessLevel);
		}
		throw new IllegalArgumentException("Argument passed is not supported event. Received: "+genericEvent.getClass());
	}

	private <T> CheckUtil hasAccess(T event, CommandClient client, Member member, CmdAccessLevel accessLevel) throws CheckException {
		if (accessLevel.getLevel() > getAccessLevel(client, member).getLevel())
			throw new CheckException(bot.getEmbedUtil().getError(event, "errors.low_access_level", "Access: "+accessLevel.getName()));
		return this;
	}

	public <T> CheckUtil guildExists(T genericEvent, boolean mustSetup) throws CheckException {
		if (genericEvent instanceof SlashCommandEvent) {
			SlashCommandEvent event = (SlashCommandEvent) genericEvent;
			return guildExists(event, event.getGuild(), mustSetup);
		}
		if (genericEvent instanceof CommandEvent) {
			CommandEvent event = (CommandEvent) genericEvent;
			return guildExists(event, event.getGuild(), mustSetup);
		}
		if (genericEvent instanceof MessageContextMenuEvent) {
			MessageContextMenuEvent event = (MessageContextMenuEvent) genericEvent;
			return guildExists(event, event.getGuild(), mustSetup);
		}
		throw new IllegalArgumentException("Argument passed is not supported event. Received: "+genericEvent.getClass());
	}

	private <T> CheckUtil guildExists(T event, Guild guild, boolean mustSetup) throws CheckException {
		if (!mustSetup)
			return this;
		if (!bot.getDBUtil().guild.exists(guild.getId()))
			throw new CheckException(bot.getEmbedUtil().getError(event, "errors.guild_not_setup"));
		return this;
	}

	public <T> CheckUtil moduleEnabled(T genericEvent, CmdModule module) throws CheckException {
		if (genericEvent instanceof SlashCommandEvent) {
			SlashCommandEvent event = (SlashCommandEvent) genericEvent;
			return moduleEnabled(event, event.getGuild(), module);
		}
		if (genericEvent instanceof CommandEvent) {
			CommandEvent event = (CommandEvent) genericEvent;
			return moduleEnabled(event, event.getGuild(), module);
		}
		if (genericEvent instanceof MessageContextMenuEvent) {
			MessageContextMenuEvent event = (MessageContextMenuEvent) genericEvent;
			return moduleEnabled(event, event.getGuild(), module);
		}
		throw new IllegalArgumentException("Argument passed is not supported event. Received: "+genericEvent.getClass());
	}

	private <T> CheckUtil moduleEnabled(T event, Guild guild, CmdModule module) throws CheckException {
		if (module == null)
			return this;
		if (bot.getDBUtil().module.isDisabled(guild.getId(), module)) 
			throw new CheckException(bot.getEmbedUtil().getError(event, "modules.module_disabled"));
		return this;
	}

	public <T> CheckUtil hasPermissions(T genericEvent, Permission[] permissions) throws CheckException {
		return hasPermissions(genericEvent, false, null, permissions);
	}

	public <T> CheckUtil hasPermissions(T genericEvent, boolean isSelf, Permission[] permissions) throws CheckException {
		return hasPermissions(genericEvent, isSelf, null, permissions);
	}

	public <T> CheckUtil hasPermissions(T genericEvent, boolean isSelf, TextChannel channel, Permission[] permissions) throws CheckException {
		if (genericEvent instanceof SlashCommandEvent) {
			SlashCommandEvent event = (SlashCommandEvent) genericEvent;
			return hasPermissions(event, event.getGuild(), event.getMember(), isSelf, channel, permissions);
		}
		if (genericEvent instanceof CommandEvent) {
			CommandEvent event = (CommandEvent) genericEvent;
			return hasPermissions(event, event.getGuild(), event.getMember(), isSelf, channel, permissions);
		}
		if (genericEvent instanceof MessageContextMenuEvent) {
			MessageContextMenuEvent event = (MessageContextMenuEvent) genericEvent;
			return hasPermissions(event, event.getGuild(), event.getMember(), isSelf, channel, permissions);
		}
		throw new IllegalArgumentException("Argument passed is not supported event. Received: "+genericEvent.getClass());
	}

	private <T> CheckUtil hasPermissions(T event, Guild guild, Member member, boolean isSelf, TextChannel channel, Permission[] permissions) throws CheckException {
		if (permissions == null || permissions.length == 0)
			return this;
		if (guild == null || member == null)
			return this;

		MessageCreateData msg = null;
		if (isSelf) {
			Member self = guild.getSelfMember();
			if (channel == null) {
				for (Permission perm : permissions) {
					if (!self.hasPermission(perm)) {
						msg = bot.getEmbedUtil().createPermError(event, member, perm, true);
						break;
					}
				}
			} else {
				for (Permission perm : permissions) {
					if (!self.hasPermission(channel, perm)) {
						msg = bot.getEmbedUtil().createPermError(event, member, channel, perm, true);
						break;
					}
				}
			}
		} else {
			if (channel == null) {
				for (Permission perm : permissions) {
					if (!member.hasPermission(perm)) {
						msg = bot.getEmbedUtil().createPermError(event, member, perm, false);
						break;
					}
				}
			} else {
				for (Permission perm : permissions) {
					if (!member.hasPermission(channel, perm)) {
						msg = bot.getEmbedUtil().createPermError(event, member, channel, perm, false);
						break;
					}
				}
			}
		}
		if (msg != null) {
			throw new CheckException(msg);
		}
		return this;
	}

}
