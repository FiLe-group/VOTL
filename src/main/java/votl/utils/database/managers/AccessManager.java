package votl.utils.database.managers;

import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

import votl.utils.database.SQLiteDBBase;
import votl.utils.database.DBUtil;

public class AccessManager extends SQLiteDBBase {

	private final String table = "modAccess";
	
	public AccessManager(DBUtil util) {
		super(util);
	}

	public void add(String guildId, String userId, boolean admin) {
		insert(table, List.of("guildId", "userId", "admin"), List.of(guildId, userId, (admin ? 1 : 0)));
	}

	public void remove(String guildId, String userId) {
		delete(table, List.of("guildId", "userId"), List.of(guildId, userId));
	}

	public void removeAll(String guildId) {
		delete(table, "guildId", guildId);
	}

	public void update(String guildId, String userId, boolean admin) {
		update(table, "admin", (admin ? 1 : 0), List.of("guildId", "userId"), List.of(guildId, userId));
	}

	public List<String> getAll(String guildId) {
		List<Object> objs = select(table, "userId", "guildId", guildId);
		if (objs.isEmpty()) return Collections.emptyList();
		return objs.stream().map(obj -> String.valueOf(obj)).collect(Collectors.toList());
	}

	public List<String> getMods(String guildId) {
		List<Object> objs = select(table, "userId", List.of("guildId", "admin"), List.of(guildId, 0));
		if (objs.isEmpty()) return Collections.emptyList();
		return objs.stream().map(obj -> String.valueOf(obj)).collect(Collectors.toList());
	}

	public List<String> getAdmins(String guildId) {
		List<Object> objs = select(table, "userId", List.of("guildId", "admin"), List.of(guildId, 1));
		if (objs.isEmpty()) return Collections.emptyList();
		return objs.stream().map(obj -> String.valueOf(obj)).collect(Collectors.toList());
	}

	public String hasAccess(String guildId, String userId) {
		List<Object> objs = select(table, "admin", List.of("guildId", "userId"), List.of(guildId, userId));
		if (objs.isEmpty() || objs.get(0) == null) return null;
		
		if (objs.get(0).equals(1)) return "admin";
		return "mod";
	}
}
