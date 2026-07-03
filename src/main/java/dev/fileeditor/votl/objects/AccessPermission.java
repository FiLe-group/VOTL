package dev.fileeditor.votl.objects;

public enum AccessPermission {
	// Grouped command access
	TICKET_SUPPORT(0),    		// AddUserCmd, RemoveUserCmd, RcloseCmd, CloseCmd
	CMD_ROLES(1),				// RoleCmd add/remove subcommands

	// Per-command access flags
	CMD_BAN(2),
	CMD_UNBAN(3),
	CMD_KICK(4),
	CMD_MUTE(5),
	CMD_UNMUTE(6),
	CMD_REASON(7),
	CMD_DURATION(8),
	CMD_CASE(9),
	CMD_STRIKE(10),
	CMD_DELETE_STRIKE(11),
	CMD_GAME_STRIKE(12),
	CMD_DEL_GAME_STRIKE(13),
	CMD_MOD_STATS(14),
	CMD_PURGE(15),
	CMD_TEMP_ROLE(16),
	CMD_BAN_INFO(17),
	CMD_CUSTOM_ROLE(18),

	// Sync group actions
	SYNC_GROUP_MANAGER(19),		// join/leave/manage server groups
	BLACKLIST_MANAGE(20),		// manage sync blacklists, execute sync actions

	// Special statuses
	AUTO_KICK_EXEMPT(21),		// exempt from auto-kick and auto-ban triggers only

	// Elevated capability
	LIMIT_OVERRIDE(22),			// no duration limits on bans/mutes

	// Built-in tier checks (command-level only, never stored in DB)
	ADMIN(23, true),			// Discord Administrator permission or higher
	OWNER(24, true),			// Guild owner or higher
	DEV(25, true);				// Bot developer / bot owner only

	public final int value;
	public final boolean hidden;

	AccessPermission(int value) {
		this(value, false);
	}

	AccessPermission(int value, boolean hidden) {
		if (value < 0 || value > 63) throw new IllegalArgumentException("value must be less that 64 and not negative");
		this.value = value;
		this.hidden = hidden;
	}

	public long toBit() {
		return 1L << this.value;
	}
}
