package votl.utils.database.managers;

import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

import votl.objects.CmdModule;
import votl.utils.database.SQLiteDBBase;
import votl.utils.database.DBUtil;

public class ModuleManager extends SQLiteDBBase {

	private final String table = "moduleOff";
	
	public ModuleManager(DBUtil util) {
		super(util);
	}

	public void add(String guildId, CmdModule module) {
		insert(table, List.of("guildId", "module"), List.of(guildId, module.toString()));
	}

	public void remove(String guildId, CmdModule module) {
		delete(table, List.of("guildId", "module"), List.of(guildId, module.toString()));
	}

	public void removeAll(String guildId) {
		delete(table, "guildId", guildId);
	}

	public List<CmdModule> getDisabled(String guildId) {
		List<Object> objs = select(table, "module", "guildId", guildId);
		if (objs.isEmpty()) return Collections.emptyList();
		return objs.stream().map(obj -> CmdModule.valueOf(String.valueOf(obj))).collect(Collectors.toList());
	}

	public boolean isDisabled(String guildId, CmdModule module) {
		if (select(table, "guildId", List.of("guildId", "module"), List.of(guildId, module.toString())).isEmpty()) return false;
		return true;
	}

}
