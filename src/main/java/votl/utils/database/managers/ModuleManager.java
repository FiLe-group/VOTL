package votl.utils.database.managers;

import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

import votl.objects.CmdModule;
import votl.utils.database.DBBase;
import votl.utils.database.DBUtil;

public class ModuleManager extends DBBase {
	
	public ModuleManager(DBUtil util) {
		super(util);
	}

	public void add(String guildId, CmdModule module) {
		insert("moduleOff", List.of("guildId", "module"), List.of(guildId, module.toString()));
	}

	public void remove(String guildId, CmdModule module) {
		delete("moduleOff", List.of("guildId", "module"), List.of(guildId, module.toString()));
	}

	public List<CmdModule> getDisabled(String guildId) {
		List<Object> objs = select("moduleOff", "module", "guildId", guildId);
		if (objs.isEmpty()) {
			return Collections.emptyList();
		}
		return objs.stream().map(obj -> CmdModule.valueOf(String.valueOf(obj))).collect(Collectors.toList());
	}

	public boolean isDisabled(String guildId, CmdModule module) {
		if (select("moduleOff", "guildId", List.of("guildId", "module"), List.of(guildId, module.toString())).isEmpty()) {
			return false;
		}
		return true;
	}

}
