package dev.fileeditor.votl.utils.database.managers;

import java.sql.SQLException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import dev.fileeditor.votl.utils.CastUtil;
import dev.fileeditor.votl.utils.database.ConnectionUtil;
import dev.fileeditor.votl.utils.database.LiteBase;

public class AutoModSyncManager extends LiteBase {

	private final String rules = "autoModSync";
	private final String targets = "autoModSyncTargets";

	public AutoModSyncManager(ConnectionUtil cu) {
		super(cu, null);
	}

	// autoModSync table
	public void addSyncRule(int groupId, long ruleId, String ruleName) throws SQLException {
		execute("INSERT INTO %s(groupId, ruleId, ruleName) VALUES (%d, %d, %s)"
			.formatted(rules, groupId, ruleId, quote(ruleName)));
	}

	public void removeSyncRule(int groupId, long ruleId) throws SQLException {
		execute("DELETE FROM %s WHERE (groupId=%d AND ruleId=%d)".formatted(rules, groupId, ruleId));
	}

	public void updateRuleName(int groupId, long ruleId, String ruleName) throws SQLException {
		execute("UPDATE %s SET ruleName=%s WHERE (groupId=%d AND ruleId=%d)".formatted(rules, quote(ruleName), groupId, ruleId));
	}

	public boolean isSynced(int groupId, long ruleId) {
		return selectOne("SELECT ruleId FROM %s WHERE (groupId=%d AND ruleId=%d)".formatted(rules, groupId, ruleId), "ruleId", Long.class) != null;
	}

	public List<Long> getSyncedRules(int groupId) {
		return select("SELECT ruleId FROM %s WHERE (groupId=%d)".formatted(rules, groupId), "ruleId", Long.class);
	}

	public String getRuleName(int groupId, long ruleId) {
		return selectOne("SELECT ruleName FROM %s WHERE (groupId=%d AND ruleId=%d)".formatted(rules, groupId, ruleId), "ruleName", String.class);
	}

	public int countSyncedRules(int groupId) {
		return count("SELECT COUNT(*) FROM %s WHERE (groupId=%d)".formatted(rules, groupId));
	}

	// autoModSyncTargets table
	public void setTarget(int groupId, long ruleId, long guildId, Long targetRuleId) throws SQLException {
		String value = targetRuleId == null ? "NULL" : String.valueOf(targetRuleId);
		execute("INSERT INTO %s(groupId, ruleId, guildId, targetRuleId) VALUES (%d, %d, %d, %s) ON CONFLICT(groupId, ruleId, guildId) DO UPDATE SET targetRuleId=%s"
			.formatted(targets, groupId, ruleId, guildId, value, value));
	}

	public void removeTarget(int groupId, long ruleId, long guildId) throws SQLException {
		execute("DELETE FROM %s WHERE (groupId=%d AND ruleId=%d AND guildId=%d)".formatted(targets, groupId, ruleId, guildId));
	}

	public void removeTargetsForRule(int groupId, long ruleId) throws SQLException {
		execute("DELETE FROM %s WHERE (groupId=%d AND ruleId=%d)".formatted(targets, groupId, ruleId));
	}

	public void removeGuildTargets(long guildId) throws SQLException {
		execute("DELETE FROM %s WHERE (guildId=%d)".formatted(targets, guildId));
	}

	public Long getTargetRuleId(int groupId, long ruleId, long guildId) {
		return selectOne("SELECT targetRuleId FROM %s WHERE (groupId=%d AND ruleId=%d AND guildId=%d)"
			.formatted(targets, groupId, ruleId, guildId), "targetRuleId", Long.class);
	}

	// guildId -> targetRuleId (null if not yet created for that guild)
	public Map<Long, Long> getTargets(int groupId, long ruleId) {
		Map<Long, Long> result = new HashMap<>();
		for (Map<String, Object> row : select("SELECT guildId, targetRuleId FROM %s WHERE (groupId=%d AND ruleId=%d)"
			.formatted(targets, groupId, ruleId), Set.of("guildId", "targetRuleId"))) {
			result.put(CastUtil.castLong(row.get("guildId")), CastUtil.castLong(row.get("targetRuleId")));
		}
		return result;
	}

}
