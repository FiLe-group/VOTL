package bot.utils;

import bot.App;
import bot.constants.Constants;
import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.TextChannel;
import net.dv8tion.jda.api.entities.User;

public class CheckUtil {

	private final App bot;

	public CheckUtil(App bot) {
		this.bot = bot;
	}

	public boolean isDeveloper(User user) {
		return user.getId().equals(Constants.OWNER_ID);
	}

	public boolean lacksPermissions(TextChannel tc, Member member, Permission[] permissions) {
		return lacksPermissions(tc, member, false, null, permissions);
	}

	public boolean lacksPermissions(TextChannel tc, Member member, boolean isSelf, Permission[] permissions) {
		return lacksPermissions(tc, member, isSelf, null, permissions);
	}
	
	public boolean lacksPermissions(TextChannel tc, Member member, boolean isSelf, TextChannel channel, Permission[] permissions) {
		Guild guild = tc.getGuild();
		if (isSelf) {
			Member self = guild.getSelfMember();
			if (channel == null) {
				for (Permission perm : permissions) {
					if (!self.hasPermission(perm)) {
						bot.getEmbedUtil().sendPermError(tc, member, perm, true);
						return true;
					}
				}
				return false;
			} else {
				for (Permission perm : permissions) {
					if (!self.hasPermission(channel, perm)) {
						bot.getEmbedUtil().sendPermError(tc, member, channel, perm, true);
						return true;
					}
				}
				return false;
			}
		} else {
			if (channel == null) {
				for (Permission perm : permissions) {
					if (!member.hasPermission(perm)) {
						bot.getEmbedUtil().sendPermError(tc, member, perm, false);
						return true;
					}
				}
				return false;
			} else {
				for (Permission perm : permissions) {
					if (!member.hasPermission(channel, perm)) {
						bot.getEmbedUtil().sendPermError(tc, member, channel, perm, false);
						return true;
					}
				}
				return false;
			}
		}
	}

}
