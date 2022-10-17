package bot.utils;

import java.util.Objects;

import com.jagrosh.jdautilities.command.SlashCommandEvent;

import bot.App;
import bot.objects.CmdAccessLevel;
import bot.objects.constants.Constants;
import bot.utils.exception.CheckException;
import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import net.dv8tion.jda.api.utils.messages.MessageEditData;
import net.dv8tion.jda.api.entities.User;

public class CheckUtil {

	private final App bot;

	public CheckUtil(App bot) {
		this.bot = bot;
	}

	public boolean isDeveloper(User user) {
		return user.getId().equals(Constants.DEVELOPER_ID);
	}

	public boolean isOwner(SlashCommandEvent event, User user) {
    	if (user.getId().equals(event.getClient().getOwnerId()))
    	    return true;
        if (event.getClient().getCoOwnerIds()==null)
            return false;
        for (String id : event.getClient().getCoOwnerIds())
            if (id.equals(user.getId()))
                return true;
        return false;
    }

	public CmdAccessLevel getAccessLevel(SlashCommandEvent event) {
		return getAccessLevel(event, event.getMember());
	}

	public CmdAccessLevel getAccessLevel(SlashCommandEvent event, Member member) {
		// Is bot developer
		if (isDeveloper(member.getUser()) || isOwner(event, member.getUser()))
			return CmdAccessLevel.DEV;
		// Is guild owner
		if (member.isOwner())
			return CmdAccessLevel.OWNER;
		
		Guild guild = Objects.requireNonNull(event.getGuild());
		String access = bot.getDBUtil().hasAccess(guild.getId(), member.getId());
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

	public CheckUtil hasAccess(SlashCommandEvent event, CmdAccessLevel accessLevel) throws CheckException {
		return hasAccess(event, event.getMember(), accessLevel);
	}

	public CheckUtil hasAccess(SlashCommandEvent event, Member member, CmdAccessLevel accessLevel) throws CheckException {
		if (accessLevel.getLevel() > getAccessLevel(event, member).getLevel())
			throw new CheckException(bot.getEmbedUtil().getError(event, "errors.low_access_level", "Access: "+accessLevel.getName()));
		return this;
	}

	public CheckUtil guildExists(SlashCommandEvent event) throws CheckException {
		String guildId = Objects.requireNonNull(event.getGuild()).getId();
		if (!bot.getDBUtil().isGuild(guildId))
			throw new CheckException(bot.getEmbedUtil().getError(event, "errors.guild_not_setup"));
		return this;
	}

	public CheckUtil moduleEnabled(SlashCommandEvent event, String module) throws CheckException {
		if (module == null)
			return this;
		String guildId = Objects.requireNonNull(event.getGuild()).getId();
		if (bot.getDBUtil().moduleDisabled(guildId, module)) 
			throw new CheckException(bot.getEmbedUtil().getError(event, "modules.module_disabled"));
		return this;
	}

	public CheckUtil hasPermissions(TextChannel tc, Member member, Permission[] permissions) throws CheckException {
		return hasPermissions(tc, member, false, null, permissions);
	}

	public CheckUtil hasPermissions(TextChannel tc, Member member, boolean isSelf, Permission[] permissions) throws CheckException {
		return hasPermissions(tc, member, isSelf, null, permissions);
	}
	
	public CheckUtil hasPermissions(TextChannel tc, Member member, boolean isSelf, TextChannel channel, Permission[] permissions) throws CheckException {
		if (permissions.length == 0)
			return this;
		MessageEditData msg = null;
		if (isSelf) {
			Member self = tc.getGuild().getSelfMember();
			if (channel == null) {
				for (Permission perm : permissions) {
					if (!self.hasPermission(perm)) {
						msg = bot.getEmbedUtil().getPermError(tc, member, perm, true);
						break;
					}
				}
			} else {
				for (Permission perm : permissions) {
					if (!self.hasPermission(channel, perm)) {
						msg = bot.getEmbedUtil().getPermError(tc, member, channel, perm, true);
						break;
					}
				}
			}
		} else {
			if (channel == null) {
				for (Permission perm : permissions) {
					if (!member.hasPermission(perm)) {
						msg = bot.getEmbedUtil().getPermError(tc, member, perm, false);
						break;
					}
				}
			} else {
				for (Permission perm : permissions) {
					if (!member.hasPermission(channel, perm)) {
						msg = bot.getEmbedUtil().getPermError(tc, member, channel, perm, false);
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
