package bot.utils;

import com.jagrosh.jdautilities.command.SlashCommandEvent;

import bot.App;
import bot.constants.Constants;
import bot.utils.exception.CheckException;
import net.dv8tion.jda.api.Permission;
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

	public CheckUtil guildExists(SlashCommandEvent event, String guildId) throws CheckException {
		if (!bot.getDBUtil().isGuild(guildId))
			throw new CheckException(bot.getEmbedUtil().getError(event, "errors.guild_not_setup"));
		return this;
	}

	public CheckUtil moduleEnabled(SlashCommandEvent event, String guildId, String module) throws CheckException {
		if (bot.getDBUtil().moduleDisabled(guildId, module)) 
			throw new CheckException(bot.getEmbedUtil().getError(event, "errors.module_disabled"));
		return this;
	}

	public CheckUtil hasPermissions(TextChannel tc, Member member, Permission[] permissions) throws CheckException {
		return hasPermissions(tc, member, false, null, permissions);
	}

	public CheckUtil hasPermissions(TextChannel tc, Member member, boolean isSelf, Permission[] permissions) throws CheckException {
		return hasPermissions(tc, member, isSelf, null, permissions);
	}
	
	public CheckUtil hasPermissions(TextChannel tc, Member member, boolean isSelf, TextChannel channel, Permission[] permissions) throws CheckException {
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
