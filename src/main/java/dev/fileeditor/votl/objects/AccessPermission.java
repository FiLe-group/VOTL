package dev.fileeditor.votl.objects;

public enum AccessPermission {
	// Grouped command access
	TICKET_SUPPORT,    		// AddUserCmd, RemoveUserCmd, RcloseCmd, CloseCmd
	CMD_ROLES,				// RoleCmd add/remove subcommands

	// Per-command access flags
	CMD_BAN,
	CMD_KICK,
	CMD_MUTE,
	CMD_UNMUTE,
	CMD_REASON,
	CMD_DURATION,
	CMD_CASE,
	CMD_STRIKES,
	CMD_STRIKE,
	CMD_DELETE_STRIKE,
	CMD_GAME_STRIKE,
	CMD_DEL_GAME_STRIKE,
	CMD_MOD_STATS,
	CMD_PURGE,
	CMD_TEMP_ROLE,
	CMD_TICKET_COUNT,
	CMD_BAN_INFO,

	// Sync group actions
	SYNC_ACTIONS,			// execute sync bans/kicks/unbans
	SYNC_MANAGER,			// join/leave/manage server groups
	BLACKLIST_MANAGE,		// manage sync blacklists

	// Special statuses
	AUTO_KICK_EXEMPT,		// exempt from auto-kick and auto-ban triggers only

	// Elevated capability
	MOD_PERMANENT,			// no duration limits on bans/mutes

	// Built-in tier checks (command-level only, never stored in DB)
	ADMIN,			// Discord Administrator permission or higher
	OWNER,			// Guild owner or higher
	DEV;			// Bot developer / bot owner only

	public long toBit() {
		return 1L << this.ordinal();
	}
}
