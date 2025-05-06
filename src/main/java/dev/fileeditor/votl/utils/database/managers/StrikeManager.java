package dev.fileeditor.votl.utils.database.managers;

import java.sql.SQLException;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Set;

import dev.fileeditor.votl.utils.database.ConnectionUtil;
import dev.fileeditor.votl.utils.database.LiteBase;

import net.dv8tion.jda.internal.utils.tuple.Pair;

public class StrikeManager extends LiteBase {

	public StrikeManager(ConnectionUtil cu) {
		super(cu, "strikeExpire");
	}

	public void addStrikes(long guildId, long userId, LocalDateTime expiresAt, int count, String caseInfo) throws SQLException {
		execute("INSERT INTO %s(guildId, userId, expiresAt, count, data, lastAddition) VALUES (%d, %d, %d, %d, %s, %s) ON CONFLICT(guildId, userId) DO UPDATE SET count=count+%5$d, data=data || ';' || %6$s, lastAddition=%7$s"
			.formatted(table, guildId, userId, expiresAt.toEpochSecond(ZoneOffset.UTC), count, quote(caseInfo), LocalDateTime.now().toEpochSecond(ZoneOffset.UTC)));
	}

	public Integer getStrikeCount(long guildId, long userId) {
		return selectOne("SELECT count FROM %s WHERE (guildId=%d AND userId=%d)".formatted(table, guildId, userId), "count", Integer.class);
	}

	public List<Map<String, Object>> getExpired() {
		return select("SELECT * FROM %s WHERE (expiresAt<%d)".formatted(table, LocalDateTime.now().toEpochSecond(ZoneOffset.UTC)), Set.of("guildId", "userId", "count", "data"));
	}

	public Pair<Integer, String> getData(long guildId, long userId) {
		Map<String, Object> data = selectOne("SELECT count, data FROM %s WHERE (guildId=%d AND userId=%d)".formatted(table, guildId, userId), Set.of("count", "data"));
		if (data == null || data.isEmpty()) return null;
		return Pair.of((Integer) data.get("count"), String.valueOf(data.getOrDefault("data", "")));
	}

	public Pair<Integer, Integer> getDataCountAndDate(long guildId, long userId) {
		Map<String, Object> data = selectOne("SELECT count, expiresAt FROM %s WHERE (guildId=%d AND userId=%d)".formatted(table, guildId, userId), Set.of("count", "expiresAt"));
		if (data == null || data.isEmpty()) return null;
		return Pair.of((Integer) data.get("count"), (Integer) data.get("expiresAt"));
	}

	public void removeStrike(long guildId, long userId, LocalDateTime expiresAt, int amount, String newData) throws SQLException {
		execute("UPDATE %s SET expiresAt=%d, count=count-%d, data=%s WHERE (guildId=%d AND userId=%d)".formatted(table, expiresAt.toEpochSecond(ZoneOffset.UTC), amount, quote(newData), guildId, userId));
	}

	public void removeGuildUser(long guildId, long userId) throws SQLException {
		execute("DELETE FROM %s WHERE (guildId=%d AND userId=%d)".formatted(table, guildId, userId));
	}

	public void removeGuild(long guildId) throws SQLException {
		execute("DELETE FROM %s WHERE (guildId=%d)".formatted(table, guildId));
	}
	
	public LocalDateTime getLastAddition(long guildId, long userId) {
		Long data = selectOne("SELECT lastAddition FROM %s WHERE (guildId=%d AND userId=%d)".formatted(table, guildId, userId), "lastAddition", Long.class);
		return data==null ? null : LocalDateTime.ofEpochSecond(data, 0, ZoneOffset.UTC);
	}
}
