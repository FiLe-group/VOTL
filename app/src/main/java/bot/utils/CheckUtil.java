package bot.utils;

import javax.annotation.Nullable;

import bot.App;
import bot.constants.Constants;
import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.utils.messages.MessageCreateData;

public class CheckUtil {

	private final App bot;

	public CheckUtil(App bot) {
		this.bot = bot;
	}

	public boolean isDeveloper(User user) {
		return user.getId().equals(Constants.DEVELOPER_ID);
	}

	@Nullable
	public MessageCreateData lacksPermissions(TextChannel tc, Member member, Permission[] permissions) {
		return lacksPermissions(tc, member, false, null, permissions);
	}

	@Nullable
	public MessageCreateData lacksPermissions(TextChannel tc, Member member, boolean isSelf, Permission[] permissions) {
		return lacksPermissions(tc, member, isSelf, null, permissions);
	}
	
	@Nullable
	public MessageCreateData lacksPermissions(TextChannel tc, Member member, boolean isSelf, TextChannel channel, Permission[] permissions) {
		Guild guild = tc.getGuild();
		if (isSelf) {
			Member self = guild.getSelfMember();
			if (channel == null) {
				for (Permission perm : permissions) {
					if (!self.hasPermission(perm)) {
						return bot.getEmbedUtil().getPermError(tc, member, perm, true);
					}
				}
				return null;
			} else {
				for (Permission perm : permissions) {
					if (!self.hasPermission(channel, perm)) {
						return bot.getEmbedUtil().getPermError(tc, member, channel, perm, true);
					}
				}
				return null;
			}
		} else {
			if (channel == null) {
				for (Permission perm : permissions) {
					if (!member.hasPermission(perm)) {
						return bot.getEmbedUtil().getPermError(tc, member, perm, false);
					}
				}
				return null;
			} else {
				for (Permission perm : permissions) {
					if (!member.hasPermission(channel, perm)) {
						return bot.getEmbedUtil().getPermError(tc, member, channel, perm, false);
					}
				}
				return null;
			}
		}
	}

}
