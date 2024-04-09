package dev.fileeditor.votl.utils.database.managers;

import java.util.List;

import dev.fileeditor.votl.objects.CmdAccessLevel;
import dev.fileeditor.votl.utils.database.ConnectionUtil;
import dev.fileeditor.votl.utils.database.LiteBase;

public class AccessManager extends LiteBase {

	private final String table_role = "accessRole";
	private final String table_user = "accessUser";
	
	public AccessManager(ConnectionUtil cu) {
		super(cu, null);
	}

	public void addRole(long guildId, long roleId, CmdAccessLevel level) {
		execute("INSERT INTO %s(guildId, roleId, level) VALUES (%s, %s, %d)".formatted(table_role, guildId, roleId, level.getLevel()));
	}

	public void addUser(long guildId, long userId, CmdAccessLevel level) {
		execute("INSERT INTO %s(guildId, userId, level) VALUES (%s, %s, %d)".formatted(table_user, guildId, userId, level.getLevel()));
	}

	public void removeRole(long roleId) {
		execute("DELETE FROM %s WHERE (roleId=%s)".formatted(table_role, roleId));
	}
	
	public void removeUser(long guildId, long userId) {
		execute("DELETE FROM %s WHERE (guildId=%s AND userId=%s)".formatted(table_user, guildId, userId));
	}

	public void removeAll(long guildId) {
		execute("DELETE FROM %1$s WHERE (guildId=%3$s); DELETE FROM %2$s WHERE (guildId=%3$s);".formatted(table_role, table_user, guildId));
	}

	public CmdAccessLevel getRoleLevel(long roleId) {
		Integer data = selectOne("SELECT level FROM %s WHERE (roleId=%s)".formatted(table_role, roleId), "level", Integer.class);
		if (data == null) return CmdAccessLevel.ALL;
		return CmdAccessLevel.byLevel(data);
	}

	public CmdAccessLevel getUserLevel(long guildId, long userId) {
		Integer data = selectOne("SELECT level FROM %s WHERE (guildId=%s AND userId=%s)".formatted(table_user, guildId, userId), "level", Integer.class);
		if (data == null) return null;
		return CmdAccessLevel.byLevel(data);
	}

	public List<Long> getAllRoles(long guildId) {
		return select("SELECT roleId FROM %s WHERE (guildId=%s)".formatted(table_role, guildId), "roleId", Long.class);
	}

	public List<Long> getRoles(long guildId, CmdAccessLevel level) {
		return select("SELECT roleId FROM %s WHERE (guildId=%s AND level=%d)".formatted(table_role, guildId, level.getLevel()), "roleId", Long.class);
	}

	public List<Long> getAllUsers(long guildId) {
		return select("SELECT userId FROM %s WHERE (guildId=%s)".formatted(table_user, guildId), "userId", Long.class);
	}

	public List<Long> getUsers(long guildId, CmdAccessLevel level) {
		return select("SELECT userId FROM %s WHERE (guildId=%s AND level=%d)".formatted(table_user, guildId, level.getLevel()), "userId", Long.class);
	}

	public boolean isRole(long roleId) {
		return selectOne("SELECT roleId FROM %s WHERE (roleId=%s)".formatted(table_role, roleId), "roleId", Long.class) != null;
	}

	public boolean isOperator(long guildId, long userId) {
		return selectOne("SELECT userId FROM %s WHERE (guildId=%s AND userId=%s AND level=%d)"
			.formatted(table_user, guildId, userId, CmdAccessLevel.OPERATOR.getLevel()), "userId", Long.class) != null;
	}

}
