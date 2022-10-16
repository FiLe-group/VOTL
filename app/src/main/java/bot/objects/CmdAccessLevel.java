package bot.objects;

import javax.annotation.Nonnull;

public enum CmdAccessLevel {
	ALL     (0, "everyone"),
	MOD     (1, "mod"),
	ADMIN   (2, "admin"),
	OWNER   (3, "guild owner"),
	DEV     (4, "bot developer");

	private final Integer level;
	private final String name;

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

	public CmdAccessLevel fromLevel(@Nonnull Integer input) {
		CmdAccessLevel value = ALL;
		try {
			value = values()[input];
		} catch (IndexOutOfBoundsException ex) {
			
		}
		return value;
	}	
}
