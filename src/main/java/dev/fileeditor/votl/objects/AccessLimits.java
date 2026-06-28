package dev.fileeditor.votl.objects;

import org.jetbrains.annotations.Nullable;

import java.time.Duration;

public record AccessLimits(
	@Nullable Duration maxBanDuration,
	@Nullable Duration maxMuteDuration
) {
	public static final AccessLimits UNLIMITED = new AccessLimits(null, null);

	/** Merges two limits, taking the most permissive (highest) finite value. null means "no constraint from this group" and is ignored unless both sides are null. */
	public AccessLimits merge(AccessLimits other) {
		return new AccessLimits(
			maxOf(this.maxBanDuration, other.maxBanDuration),
			maxOf(this.maxMuteDuration, other.maxMuteDuration)
		);
	}

	private static @Nullable Duration maxOf(@Nullable Duration a, @Nullable Duration b) {
		if (a == null) return b;
		if (b == null) return a;
		return a.compareTo(b) >= 0 ? a : b;
	}
}
