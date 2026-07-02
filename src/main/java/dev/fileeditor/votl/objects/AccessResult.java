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
		EnumSet<AccessPermission> ownerPerms = EnumSet.allOf(AccessPermission.class);
		ownerPerms.remove(AccessPermission.DEV);
		SERVER_OWNER = new AccessResult(ownerPerms, AccessLimits.UNLIMITED);

		EnumSet<AccessPermission> adminPerms = EnumSet.copyOf(ownerPerms);
		adminPerms.remove(AccessPermission.OWNER);
		adminPerms.remove(AccessPermission.SYNC_GROUP_MANAGER);
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
