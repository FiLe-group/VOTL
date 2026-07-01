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

	/** Build-in sets. */
	public static final AccessResult SERVER_OWNER;
	public static final AccessResult ADMIN_DEFAULT;
	static {
		EnumSet<AccessPermission> perms = EnumSet.allOf(AccessPermission.class);
		perms.remove(AccessPermission.DEV);
		SERVER_OWNER = new AccessResult(perms, AccessLimits.UNLIMITED);

		perms.remove(AccessPermission.OWNER);
		perms.remove(AccessPermission.SYNC_GROUP_MANAGER);
		perms.remove(AccessPermission.BLACKLIST_MANAGE);
		ADMIN_DEFAULT = new AccessResult(perms, AccessLimits.UNLIMITED);
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
