package bot.utils;

import bot.App;
import bot.constants.Constants;
import bot.utils.exception.LacksPermException;
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

	public void hasPermissions(TextChannel tc, Member member, Permission[] permissions) throws LacksPermException {
		hasPermissions(tc, member, false, null, permissions);
	}

	public void hasPermissions(TextChannel tc, Member member, boolean isSelf, Permission[] permissions) throws LacksPermException {
		hasPermissions(tc, member, isSelf, null, permissions);
	}
	
	public void hasPermissions(TextChannel tc, Member member, boolean isSelf, TextChannel channel, Permission[] permissions) throws LacksPermException {
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
			throw new LacksPermException(msg);
		}
	}

}
