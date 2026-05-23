package dev.fileeditor.votl.objects;

import java.util.EnumSet;

public enum CmdModule {
	WEBHOOK("modules.webhook", 1),
	MODERATION("modules.moderation", 2),
	STRIKES("modules.strikes", 3),
	VERIFICATION("modules.verification", 4),
	TICKETING("modules.ticketing", 5),
	VOICE("modules.voice", 6),
	REPORT("modules.report", 7),
	ROLES("modules.roles", 8),
	GAMES("modules.games", 9),
	LEVELS("modules.levels", 10),
	TOOLS("modules.tools", 11);
	
	private final String path;
	private final int offset;
	
	CmdModule(String path, int value) {
		this.path = path;
		this.offset = 2^(value-1);
	}

	public String getPath() {
		return path;
	}

	public int getOffset() {
		return offset;
	}

	public static EnumSet<CmdModule> decodeModules(int data) {
		EnumSet<CmdModule> modules = EnumSet.noneOf(CmdModule.class);
		for (CmdModule v : values()) {
			if ((data & v.offset) == v.offset) modules.add(v);
		}
		return modules;
	}

	@SuppressWarnings("unused")
	public static int encodeModules(EnumSet<CmdModule> values) {
		return values.stream().mapToInt(CmdModule::getOffset).sum();
	}
}
