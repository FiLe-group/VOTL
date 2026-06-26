package dev.fileeditor.votl.objects;

import java.util.EnumSet;
import java.util.Set;

public record AccessResult(Set<AccessPermission> permissions, AccessLimits limits) {

	public static final AccessResult FULL = new AccessResult(
		EnumSet.allOf(AccessPermission.class), AccessLimits.UNLIMITED
	);

	public static final AccessResult EMPTY = new AccessResult(
		EnumSet.noneOf(AccessPermission.class), AccessLimits.UNLIMITED
	);

	/** ADMIN built-in: all flags except sync-related. */
	public static final AccessResult ADMIN_DEFAULT;
	static {
		EnumSet<AccessPermission> adminPerms = EnumSet.allOf(AccessPermission.class);
		adminPerms.remove(AccessPermission.SYNC_ACTIONS);
		adminPerms.remove(AccessPermission.SYNC_MANAGER);
		adminPerms.remove(AccessPermission.BLACKLIST_MANAGE);
		ADMIN_DEFAULT = new AccessResult(adminPerms, AccessLimits.UNLIMITED);
	}

	public boolean has(AccessPermission p) {
		return permissions.contains(p);
	}

	public AccessResult merge(AccessResult other) {
		EnumSet<AccessPermission> merged = permissions.isEmpty()
			? EnumSet.noneOf(AccessPermission.class)
			: EnumSet.copyOf(permissions);
		merged.addAll(other.permissions);
		return new AccessResult(merged, limits.merge(other.limits));
	}
}
