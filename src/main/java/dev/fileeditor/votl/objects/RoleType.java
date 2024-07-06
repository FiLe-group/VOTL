package dev.fileeditor.votl.objects;

import java.util.HashMap;
import java.util.Map;

public enum RoleType {
	CUSTOM(0, "role_type.custom"),
	TOGGLE(1, "role_type.toggle"),
	ASSIGN(2, "role_type.assign");

	private final Integer type;
	private final String path;
	
	private static final Map<Integer, RoleType> BY_TYPE;

	static {
		BY_TYPE = new HashMap<>();
		for (RoleType rt : RoleType.values()) {
			BY_TYPE.put(rt.getType(), rt);
		}
	}

	RoleType(Integer type, String path) {
		this.type = type;
		this.path = path;
	}

	public Integer getType() {
		return type;
	}

	public String getPath() {
		return path;
	}

	@Override
	public String toString() {
		return this.name().toLowerCase();
	}
	
	public static RoleType byType(int type) {
		return BY_TYPE.get(type);
	}
}
