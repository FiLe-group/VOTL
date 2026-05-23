package dev.fileeditor.votl.utils.database.managers;

import dev.fileeditor.votl.blacklist.Scope;
import dev.fileeditor.votl.utils.database.ConnectionUtil;
import dev.fileeditor.votl.utils.database.LiteBase;
import org.jetbrains.annotations.NotNull;

import java.sql.SQLException;
import java.time.OffsetDateTime;
import java.util.*;

public class BotBlacklistManager extends LiteBase {

	public BotBlacklistManager(ConnectionUtil cu) {
		super(cu, "blacklist");
	}

	public void add(long id, Scope scope, @NotNull OffsetDateTime expiresIn, String reason, boolean dnt) throws SQLException {
		execute("INSERT INTO %s(id, type, expiresIn, reason, dnt) VALUES (%s, %s, %s, %s, %s) ON CONFLICT (id) DO UPDATE SET expiresIn=%4$s, reason=%5$s, dnt=%6$s"
			.formatted(table, id, scope.getId(), expiresIn.toEpochSecond(), quote(reason), dnt?1:0));
	}

	public void remove(long id) throws SQLException {
		execute("DELETE FROM %s WHERE (id = %s)".formatted(table, id));
	}

	public List<Map<String, Object>> load() {
		return select("SELECT * FROM %s WHERE expiresIn>%s".formatted(table, OffsetDateTime.now().toEpochSecond()), Set.of("id", "type", "expiresIn", "reason"));
	}

}
