package bot.objects;

import java.util.HashMap;
import java.util.Map;

public enum CmdAccessLevel {
	ALL     (0, "everyone"),
	MOD     (1, "mod"),
	ADMIN   (2, "admin"),
	OWNER   (3, "guild owner"),
	DEV     (4, "bot developer");

	private final Integer level;
	private final String name;

	public static final Map<Integer, CmdAccessLevel> lookup = new HashMap<Integer, CmdAccessLevel>();

	static {
		for (CmdAccessLevel al : CmdAccessLevel.values()) {
			lookup.put(al.getLevel(), al);
		}
	}

	CmdAccessLevel(Integer level, String name) {
		this.level = level;
		this.name = name;
	}

	public Integer getLevel() {
		return level;
	}

	public String getName() {
		return name;
	}

}
