package dev.fileeditor.votl.utils.database.managers;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import dev.fileeditor.votl.objects.RoleType;
import dev.fileeditor.votl.utils.database.ConnectionUtil;
import dev.fileeditor.votl.utils.database.LiteBase;

public class RoleManager extends LiteBase {
	
	public RoleManager(ConnectionUtil cu) {
		super(cu, "roles");
	}

	public void add(long guildId, long roleId, String description, Integer row, RoleType roleType, boolean timed) {
		execute("INSERT INTO %s(guildId, roleId, description, type, row, timed) VALUES (%s, %s, %s, %s, %s, %s)"
			.formatted(table, guildId, roleId, quote(description), roleType.getType(), Optional.ofNullable(row).orElse(0), timed ? 1 : 0));
	}

	public void remove(long roleId) {
		execute("DELETE FROM %s WHERE (roleId=%s)".formatted(table, roleId));
	}

	public void removeAll(long guildId) {
		execute("DELETE FROM %s WHERE (guildId=%s)".formatted(table, guildId));
	}

	public List<Map<String, Object>> getRolesByType(long guildId, RoleType type) {
		return select("SELECT * FROM %s WHERE (guildId=%s AND type=%d)".formatted(table, guildId, type.getType()), Set.of("roleId", "description"));
	}

	public List<Map<String, Object>> getAssignable(long guildId) {
		return select("SELECT * FROM %s WHERE (guildId=%s AND type>%d)".formatted(table, guildId, RoleType.ASSIGN.getType()), Set.of("roleId", "description", "row"));
	}

	public List<Map<String, Object>> getAssignableByRow(long guildId, int row) {
		return select("SELECT * FROM %s WHERE (guildId=%s AND type=%d AND row=%d)".formatted(table, guildId, RoleType.ASSIGN.getType(), row),
			Set.of("roleId", "description", "timed")
		);
	}

	public List<Map<String, Object>> getToggleable(long guildId) {
		return select("SELECT * FROM %s WHERE (guildId=%s AND type=%d)".formatted(table, guildId, RoleType.TOGGLE.getType()), Set.of("roleId", "description"));
	}

	public List<Map<String, Object>> getCustom(long guildId) {
		return select("SELECT * FROM %s WHERE (guildId=%s AND type=%d)".formatted(table, guildId, RoleType.CUSTOM.getType()), Set.of("roleId", "description"));
	}

	public int getRowSize(long guildId, int row) {
		return count("SELECT COUNT(*) FROM %s WHERE (guildId=%s AND row=%d)".formatted(table, guildId, row));
	}

	public int countRoles(long guildId, RoleType type) {
		return count("SELECT COUNT(*) FROM %s WHERE (guildId=%s AND type=%d)".formatted(table, guildId, type.getType()));
	}

	public RoleType getType(long roleId) {
		Integer data = selectOne("SELECT type FROM %s WHERE (roleId=%s)".formatted(table, roleId), "type", Integer.class);
		if (data == null) return null;
		return RoleType.byType(data);
	}

	public String getDescription(long roleId) {
		return selectOne("SELECT description FROM %s WHERE (roleId=%s)".formatted(table, roleId), "description", String.class);
	}

	public void setDescription(long roleId, String description) {
		execute("UPDATE %s SET description=%s WHERE (roleId=%s)".formatted(table, quote(description), roleId));
	}

	public void setRow(long roleId, Integer row) {
		execute("UPDATE %s SET row=%d WHERE (roleId=%s)".formatted(table, Optional.ofNullable(row).orElse(0), roleId));
	}

	public void setTimed(long roleId, boolean timed) {
		execute("UPDATE %s SET timed=%s WHERE (roleId=%s)".formatted(table, timed ? 1 : 0, roleId));
	}

	public boolean isToggleable(long roleId) {
		RoleType type = getType(roleId);
		return type != null && type.equals(RoleType.TOGGLE);
	}

	public boolean existsRole(long roleId) {
		return selectOne("SELECT roleId FROM %s WHERE (roleId=%s)".formatted(table, roleId), "roleId", Long.class) != null;
	}
}
