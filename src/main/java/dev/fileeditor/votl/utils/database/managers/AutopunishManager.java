package dev.fileeditor.votl.utils.database.managers;

import java.util.List;
import java.util.Map;
import java.util.Set;

import dev.fileeditor.votl.objects.PunishAction;
import dev.fileeditor.votl.utils.database.ConnectionUtil;
import dev.fileeditor.votl.utils.database.LiteBase;

import net.dv8tion.jda.internal.utils.tuple.Pair;

public class AutopunishManager extends LiteBase {
	
	public AutopunishManager(ConnectionUtil cu) {
		super(cu, "autopunish");
	}

	public void addAction(long guildId, int atStrikeCount, List<PunishAction> actions, String data) {
		execute("INSERT INTO %s(guildId, strike, actions, data) VALUES (%d, %d, %d, %s)".formatted(table, guildId, atStrikeCount, PunishAction.encodeActions(actions), quote(data)));
	}

	public void removeAction(long guildId, int atStrikeCount) {
		execute("DELETE FROM %s WHERE (guildId=%d AND strike=%d)".formatted(table, guildId, atStrikeCount));
	}

	public void removeGuild(long guildId) {
		execute("DELETE FROM %s WHERE (guildId=%d)".formatted(table, guildId));
	}

	public Pair<Integer, String> getAction(long guildId, int atStrikeCount) {
		Map<String, Object> data = selectOne("SELECT actions, data FROM %s WHERE (guildId=%d AND strike=%d)".formatted(table, guildId, atStrikeCount), Set.of("actions", "data"));
		if (data == null) return null;
		return Pair.of((Integer) data.get("actions"), (String) data.getOrDefault("data", ""));
	}

	public List<Map<String, Object>> getAllActions(long guildId) {
		return select("SELECT * FROM %s WHERE (guildId=%d)".formatted(table, guildId), Set.of("strike", "actions", "data"));
	}
}
