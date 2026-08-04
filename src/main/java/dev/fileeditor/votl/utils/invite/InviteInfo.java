package dev.fileeditor.votl.utils.invite;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Outcome of working out which invite a member joined with.
 * <p>Only {@link Source#INVITE} carries invite details; every other source describes why no single
 * invite could be named.
 *
 * @param uses    Uses <b>after</b> this join, or {@code -1} when not applicable.
 * @param maxUses {@code 0} for unlimited, {@code -1} when not applicable.
 */
public record InviteInfo(
	@NotNull Source source,
	@Nullable String code,
	@Nullable Long inviterId,
	@Nullable Long channelId,
	int uses,
	int maxUses
) {
	public enum Source {
		/** A single invite's counter advanced - it is named. */
		INVITE,
		/** The guild's vanity URL was used. */
		VANITY,
		/** Invites were read fine, but nothing was consumed: widget or server discovery. */
		WIDGET_OR_DISCOVERY,
		/** Several invites advanced at once, they cannot be told apart. */
		AMBIGUOUS,
		/** No snapshot existed yet for this guild, so there was nothing to compare against. */
		NOT_CACHED,
		/** Invites could not be read - missing Manage Server, or the request failed. */
		UNAVAILABLE
	}

	public static InviteInfo invite(@NotNull CachedInvite invite, int usesAfterJoin) {
		return new InviteInfo(Source.INVITE, invite.code(), invite.inviterId(), invite.channelId(),
			usesAfterJoin, invite.maxUses());
	}

	public static InviteInfo vanity(@NotNull String code, int uses) {
		return new InviteInfo(Source.VANITY, code, null, null, uses, 0);
	}

	public static InviteInfo of(@NotNull Source source) {
		return new InviteInfo(source, null, null, null, -1, -1);
	}
}
