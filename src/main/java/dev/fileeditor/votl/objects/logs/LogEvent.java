package dev.fileeditor.votl.objects.logs;

public enum LogEvent {
	CHANNEL_CREATE("create", LogType.CHANNEL),
	CHANNEL_UPDATE("update", LogType.CHANNEL),
	CHANNEL_DELETE("delete", LogType.CHANNEL),
	CHANNEL_OVERRIDE_CREATE("override_create", LogType.CHANNEL),
	CHANNEL_OVERRIDE_UPDATE("override_update", LogType.CHANNEL),
	CHANNEL_OVERRIDE_DELETE("override_delete", LogType.CHANNEL),
	ROLE_CREATE("create", LogType.ROLE),
	ROLE_UPDATE("update", LogType.ROLE),
	ROLE_DELETE("delete", LogType.ROLE),
	GUILD_UPDATE("update", LogType.GUILD),
	EMOJI_CREATE("emoji_create", LogType.GUILD),
	EMOJI_UPDATE("emoji_update", LogType.GUILD),
	EMOJI_DELETE("emoji_delete", LogType.GUILD),
	STICKER_CREATE("sticker_create", LogType.GUILD),
	STICKER_UPDATE("sticker_update", LogType.GUILD),
	STICKER_DELETE("sticker_delete", LogType.GUILD),
	MEMBER_JOIN("join", LogType.MEMBER),
	MEMBER_LEAVE("leave", LogType.MEMBER),
	MEMBER_ROLE_CHANGE("role_change", LogType.MEMBER),
	MEMBER_NICK_CHANGE("nick_change", LogType.MEMBER),
	KICK("kick.title", LogType.MODERATION),
	BAN("ban.title", LogType.MODERATION),
	UNBAN("unban.title", LogType.MODERATION),
	TIMEOUT("timeout", LogType.MODERATION),
	REMOVE_TIMEOUT("remove_timeout", LogType.MODERATION),
	//INVITE_SENT("sent", LogType.INVITE),
	//VC_JOIN("join", LogType.VOICE),
	//VC_LEAVE("leave", LogType.VOICE),
	//VC_SWITCH("switch", LogType.VOICE),
	VC_CHANGE("change", LogType.VOICE),
	MESSAGE_DELETE("delete", LogType.MESSAGE),
	MESSAGE_BULK_DELETE("bulk_delete", LogType.MESSAGE),
	MESSAGE_UPDATE("update", LogType.MESSAGE),
	LEVEL_UP("level_up", LogType.LEVEL);

	private final String path;
	private final LogType type;

	LogEvent(String path, LogType type) {
		this.path = type.getPath()+"."+path;
		this.type = type;
	}

	public String getPath() {
		return path;
	}

	public LogType getType() {
		return type;
	}

	@Override
	public String toString() {
		return name().toLowerCase();
	}
}
