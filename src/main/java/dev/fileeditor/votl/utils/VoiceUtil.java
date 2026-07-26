package dev.fileeditor.votl.utils;

import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.entities.PermissionOverride;
import net.dv8tion.jda.api.entities.Role;
import net.dv8tion.jda.api.entities.channel.concrete.Category;
import net.dv8tion.jda.api.entities.channel.concrete.VoiceChannel;
import net.dv8tion.jda.api.entities.channel.middleman.AudioChannel;
import net.dv8tion.jda.api.requests.restaction.PermissionOverrideAction;
import org.jetbrains.annotations.NotNull;

/**
 * Shared helpers for custom voice channels.
 */
public class VoiceUtil {

	/** Discord requires channel names to be between 2 and 100 characters. */
	private static final int NAME_MIN_LENGTH = 2;
	private static final int NAME_MAX_LENGTH = 100;

	/**
	 * Whether the channel holds no actual users. Bots are ignored, so a music or recording bot
	 * left behind in a custom channel does not keep it alive forever.
	 */
	public static boolean hasNoUsers(@NotNull AudioChannel channel) {
		return channel.getMembers().stream().allMatch(member -> member.getUser().isBot());
	}

	/**
	 * Clamps a channel name to the length Discord accepts: truncated to {@value #NAME_MAX_LENGTH}
	 * characters, and padded when a name template resolves to something too short to be valid.
	 */
	@NotNull
	public static String formatChannelName(@NotNull String name) {
		String formatted = name.length() > NAME_MAX_LENGTH ? name.substring(0, NAME_MAX_LENGTH) : name;
		if (formatted.length() < NAME_MIN_LENGTH) {
			formatted += "-".repeat(NAME_MIN_LENGTH - formatted.length());
		}
		return formatted;
	}

	/**
	 * Restores {@code permission} for {@code role} to whatever the channel originally inherited
	 * from its category, rather than granting it outright.
	 * <p>
	 * Granting is not the inverse of denying: if the category denies the permission to this role,
	 * then locking and unlocking a channel with {@code grant} would leave it more permissive than
	 * it started, silently widening access.
	 */
	public static void resetInheritedPermission(@NotNull VoiceChannel vc, @NotNull Role role, @NotNull Permission permission) {
		Category category = vc.getParentCategory();
		PermissionOverride inherited = category == null ? null : category.getPermissionOverride(role);
		PermissionOverrideAction action = vc.upsertPermissionOverride(role);

		if (inherited == null) {
			action.clear(permission).queue();
		} else if (inherited.getAllowed().contains(permission)) {
			action.grant(permission).queue();
		} else if (inherited.getDenied().contains(permission)) {
			action.deny(permission).queue();
		} else {
			action.clear(permission).queue();
		}
	}

}