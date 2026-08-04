package dev.fileeditor.votl.utils.invite;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * One entry of a guild's invite snapshot, kept by {@link InviteTracker}.
 * <p>The inviter is copied in rather than looked up later on purpose - Discord drops an invite the
 * moment its last use is spent, so by the time the join is processed the invite may no longer exist.
 *
 * @param uses    Uses at the time of the snapshot.
 * @param maxUses {@code 0} for unlimited.
 */
public record CachedInvite(
	@NotNull String code,
	int uses,
	int maxUses,
	@Nullable Long inviterId,
	@Nullable Long channelId
) {
	/** Whether one more use would have consumed this invite. */
	public boolean isAboutToBeExhausted() {
		return maxUses > 0 && uses == maxUses - 1;
	}
}
