package dev.fileeditor.votl.utils.database.managers;

import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

import dev.fileeditor.votl.objects.constants.Constants;
import dev.fileeditor.votl.utils.database.DBUtil;
import dev.fileeditor.votl.utils.database.SQLiteDBBase;

public class VerifyManager extends SQLiteDBBase {

	private final String tableVerify = "verify";
	private final String tableBlacklist = "blacklist";

	public VerifyManager(DBUtil util) {
		super(util);
	}

	// Verify table
	public void add(String guildId) {
		insert(tableVerify, "guildId", guildId);
	}

	public void remove(String guildId) {
		delete(tableVerify, "guildId", guildId);
	}

	public boolean exists(String guildId) {
		if (select(tableVerify, "guildId", "guildId", guildId).isEmpty()) return false;
		return true;
	}

	public void setVerifyRole(String guildId, String roleId) {
		update(tableVerify, "roleId", roleId, "guildId", guildId);
	}

	public String getVerifyRole(String guildId) {
		List<Object> objs = select(tableVerify, "roleId", "guildId", guildId);
		if (objs.isEmpty() || objs.get(0) == null) return null;
		return String.valueOf(objs.get(0));
	}

	public void setPanelText(String guildId, String text) {
		update(tableVerify, "panelText", text, "guildId", guildId);
	}

	public String getPanelText(String guildId) {
		List<Object> objs = select(tableVerify, "panelText", "guildId", guildId);
		if (objs.isEmpty() || objs.get(0) == null) return "No text";
		return escapeCode(String.valueOf(objs.get(0)));
	}

	public void setColor(String guildId, Integer color) {
		update(tableVerify, "panelColor", color, "guildId", guildId);
	}

	public Integer getColor(String guildId) {
		List<Object> objs = select(tableVerify, "panelColor", "guildId", guildId);
		if (objs.isEmpty() || objs.get(0) == null) return Constants.COLOR_DEFAULT;
		return Integer.decode(String.valueOf(objs.get(0)));
	}

	private String escapeCode(String text) {
		if (text.startsWith("```")) text = text.substring(3, text.length()-3);
		else if (text.startsWith("`")) text = text.substring(1, text.length()-1);

		return text.replaceAll("<br>", "\n");
	}

	// Blacklist table
	public boolean blacklistUser(String guildId, String userId) {
		if (isBlacklisted(guildId, userId))
			return false;
		else
			insert(tableBlacklist, List.of("guildId", "userId"), List.of(guildId, userId));
		return true;
	}

	public boolean isBlacklisted(String guildId, String userId) {
		if (select(tableBlacklist, "userId", List.of("guildId", "userId"), List.of(guildId, userId)).isEmpty()) return false;
		return true;
	}

	public List<String> getBlacklist(String guildId) {
		List<Object> objs = select(tableBlacklist, "userId", "guildId", guildId);
		if (objs.isEmpty()) return Collections.emptyList();
		return objs.stream().map(obj -> String.valueOf(obj)).collect(Collectors.toList());
	}

	public void removeUser(String guildId, String userId) {
		delete(tableBlacklist, List.of("guildId", "userId"), List.of(guildId, userId));
	}

	public void clearGuild(String guildId) {
		delete(tableBlacklist, "guildId", guildId);
	}
	
}
