package dev.fileeditor.votl.utils;

import java.time.Duration;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;

import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.entities.*;
import net.dv8tion.jda.api.entities.channel.middleman.GuildChannel;
import net.dv8tion.jda.api.interactions.callbacks.IReplyCallback;
import net.dv8tion.jda.api.utils.messages.MessageCreateData;

import dev.fileeditor.votl.App;
import dev.fileeditor.votl.objects.AccessPermission;
import dev.fileeditor.votl.objects.AccessResult;
import dev.fileeditor.votl.objects.CmdModule;
import dev.fileeditor.votl.objects.constants.Constants;
import dev.fileeditor.votl.utils.exception.CheckException;
import dev.fileeditor.votl.utils.message.TimeUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

@SuppressWarnings("UnusedReturnValue")
public class CheckUtil {

	private final App bot;
	private final long ownerId;

	public CheckUtil(App bot, long ownerId) {
		this.bot = bot;
		this.ownerId = ownerId;
	}

	public boolean isDeveloper(@NotNull UserSnowflake user) {
		return user.getIdLong() == Constants.DEVELOPER_ID;
	}

	public boolean isBotOwner(@NotNull UserSnowflake user) {
		return user.getIdLong() == ownerId;
	}

	/** Resolves the combined AccessResult for a member across all their custom groups. */
	@NotNull
	public AccessResult resolve(@NotNull Member member) {
		if (isDeveloper(member) || isBotOwner(member)) return AccessResult.FULL;
		if (member.isOwner()) return AccessResult.FULL;
		if (member.hasPermission(Permission.ADMINISTRATOR)) return AccessResult.ADMIN_DEFAULT;

		List<Long> roleIds = member.getRoles().stream().map(ISnowflake::getIdLong).toList();
		return bot.getDBUtil().accessGroups.resolveForMember(
			member.getGuild().getIdLong(), member.getIdLong(), roleIds
		);
	}

	/**
	 * Enforces ban duration limits for the executing member.
	 * @param requested null = permanent ban
	 */
	public void enforceBanLimit(@NotNull IReplyCallback event, @NotNull Member member,
	                            @Nullable Duration requested) throws CheckException {
		if (isDeveloper(member) || isBotOwner(member) || member.isOwner()) return;
		AccessResult access = resolve(member);
		if (access.has(AccessPermission.MOD_PERMANENT)) return;
		Duration max = access.limits().maxBanDuration();
		if (max == null) return;
		if (requested == null)
			throw new CheckException(bot.getEmbedUtil().getError(event, "errors.interaction.ban_no_permanent"));
		if (requested.compareTo(max) > 0)
			throw new CheckException(bot.getEmbedUtil().getError(event,
				"errors.interaction.ban_exceeds_limit", TimeUtil.durationToString(max)));
	}

	/**
	 * Enforces mute duration limits for the executing member.
	 * Mutes cannot be permanent (Discord limitation), so requested is always non-null.
	 */
	public void enforceMuteLimit(@NotNull IReplyCallback event, @NotNull Member member,
	                             @NotNull Duration requested) throws CheckException {
		if (isDeveloper(member) || isBotOwner(member) || member.isOwner()) return;
		AccessResult access = resolve(member);
		if (access.has(AccessPermission.MOD_PERMANENT)) return;
		Duration max = access.limits().maxMuteDuration();
		if (max == null) return;
		if (requested.compareTo(max) > 0)
			throw new CheckException(bot.getEmbedUtil().getError(event,
				"errors.interaction.mute_exceeds_limit", TimeUtil.durationToString(max)));
	}

	private int builtinTier(@NotNull Member member) {
		if (isDeveloper(member) || isBotOwner(member)) return 3;
		if (member.isOwner()) return 2;
		if (member.hasPermission(Permission.ADMINISTRATOR)) return 1;
		return 0;
	}

	public boolean hasHigherAccess(@NotNull Member who, @NotNull Member than) {
		return builtinTier(who) > builtinTier(than);
	}

	public boolean hasAccess(@NotNull Member member, @NotNull AccessPermission required) {
		return switch (required) {
			case DEV   -> isDeveloper(member) || isBotOwner(member);
			case OWNER -> member.isOwner()    || isDeveloper(member) || isBotOwner(member);
			case ADMIN -> member.hasPermission(Permission.ADMINISTRATOR) || member.isOwner() || isDeveloper(member) || isBotOwner(member);
			default    -> resolve(member).has(required);
		};
	}

	@NotNull
	public CheckUtil moduleEnabled(@NotNull IReplyCallback replyCallback, @NotNull Guild guild, @Nullable CmdModule module) throws CheckException {
		if (module == null)
			return this;
		if (bot.getDBUtil().getGuildSettings(guild).isDisabled(module)) 
			throw new CheckException(bot.getEmbedUtil().getError(replyCallback, "modules.module_disabled"));
		return this;
	}

	@NotNull
	public CheckUtil hasPermissions(@NotNull IReplyCallback replyCallback, @NotNull Permission[] permissions) throws CheckException {
		return hasPermissions(replyCallback, permissions, null, null, true);
	}

	@NotNull
	public CheckUtil hasPermissions(@NotNull IReplyCallback replyCallback, @NotNull Permission[] permissions, @NotNull Member member) throws CheckException {
		return hasPermissions(replyCallback, permissions, null, member, false);
	}

	@NotNull
	public CheckUtil hasPermissions(@NotNull IReplyCallback replyCallback, @NotNull Permission[] permissions, @NotNull GuildChannel channel) throws CheckException {
		return hasPermissions(replyCallback, permissions, channel, null, true);
	}

	@NotNull
	public CheckUtil hasPermissions(@NotNull IReplyCallback replyCallback, @NotNull Permission[] permissions, @NotNull GuildChannel channel, @NotNull Member member) throws CheckException {
		return hasPermissions(replyCallback, permissions, channel, member, false);
	}

	@NotNull
	public CheckUtil hasPermissions(@NotNull IReplyCallback replyCallback, @Nullable Permission[] permissions, @Nullable GuildChannel channel, @Nullable Member member, boolean isSelf) throws CheckException {
		if (permissions == null || permissions.length == 0)
			return this;
		if (!isSelf && member == null)
			throw new IllegalArgumentException("You must specify a member if not self.");

		final Guild guild = replyCallback.getGuild();
		if (guild == null)
			return this;

		MessageCreateData msg = null;
		if (isSelf) {
			Member self = guild.getSelfMember();
			if (channel == null) {
				for (Permission perm : permissions) {
					if (!self.hasPermission(perm)) {
						msg = bot.getEmbedUtil().createPermError(replyCallback, perm, true);
						break;
					}
				}
			} else {
				for (Permission perm : permissions) {
					if (!self.hasPermission(channel, perm)) {
						msg = bot.getEmbedUtil().createPermError(replyCallback, channel, perm, true);
						break;
					}
				}
			}
		} else {
			if (channel == null) {
				for (Permission perm : permissions) {
					if (!member.hasPermission(perm)) {
						msg = bot.getEmbedUtil().createPermError(replyCallback, perm, false);
						break;
					}
				}
			} else {
				for (Permission perm : permissions) {
					if (!member.hasPermission(channel, perm)) {
						msg = bot.getEmbedUtil().createPermError(replyCallback, channel, perm, false);
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

	private final Set<Permission> adminPerms = Set.of(Permission.ADMINISTRATOR, Permission.MANAGE_CHANNEL, Permission.MANAGE_ROLES, Permission.MANAGE_SERVER, Permission.BAN_MEMBERS);

	@Nullable
	public String denyRole(@NotNull Role role, @NotNull Guild guild, @NotNull Member member, boolean checkPerms) {
		if (role.isPublicRole()) return "`@everyone` is public";
		else if (role.isManaged()) return "Bot's role";
		else if (!member.canInteract(role)) return "You can't interact with this role";
		else if (!guild.getSelfMember().canInteract(role)) return "Bot can't interact with this role";
		else if (checkPerms) {
			EnumSet<Permission> rolePerms = EnumSet.copyOf(role.getPermissions());
			rolePerms.retainAll(adminPerms);
			if (!rolePerms.isEmpty()) return "This role has Administrator/Manager permissions";
		}
		return null;
	}

}
