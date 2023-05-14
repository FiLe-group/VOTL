package votl.utils.database.managers;

import java.util.List;

import votl.utils.database.DBUtil;
import votl.utils.database.LiteDBBase;

public class VerifyManager extends LiteDBBase {

	public VerifyManager(DBUtil util) {
		super(util);
	}

	public void add(String guildId) {
		insert("verify", "guildId", guildId);
	}

	public void remove(String guildId) {
		delete("verify", "guildId", guildId);
	}

	public boolean exists(String guildId) {
		if (select("verify", "guildId", "guildId", guildId).isEmpty()) return false;
		return true;
	}

	public void setVerifyRole(String guildId, String roleId) {
		update("verify", "roleId", roleId, "guildId", guildId);
	}

	public String getVerifyRole(String guildId) {
		List<Object> objs = select("verify", "roleId", "guildId", guildId);
		if (objs.isEmpty() || objs.get(0) == null) return null;
		return String.valueOf(objs.get(0));
	}

	public void setPanelText(String guildId, String text) {
		update("verify", "panelText", text, "guildId", guildId);
	}

	public String getPanelText(String guildId) {
		List<Object> objs = select("verify", "panelText", "guildId", guildId);
		if (objs.isEmpty() || objs.get(0) == null) return "No text";
		return escapeCode(String.valueOf(objs.get(0)));
	}

	public void setInstructionText(String guildId, String text) {
		update("verify", "instructionText", text, "guildId", guildId);
	}

	public String getInstructionText(String guildId) {
		List<Object> objs = select("verify", "instructionText", "guildId", guildId);
		if (objs.isEmpty() || objs.get(0) == null) return "No text";
		return escapeCode(String.valueOf(objs.get(0)));
	}

	public void setInstructionField(String guildId, String text) {
		update("verify", "instructionField", text, "guildId", guildId);
	}

	public String getInstructionField(String guildId) {
		List<Object> objs = select("verify", "instructionField", "guildId", guildId);
		if (objs.isEmpty() || objs.get(0) == null) return "No text";
		return escapeCode(String.valueOf(objs.get(0)));
	}

	public void setVerificationLink(String guildId, String link) {
		update("verify", "verificationLink", link, "guildId", guildId);
	}

	public String getVerificationLink(String guildId) {
		List<Object> objs = select("verify", "verificationLink", "guildId", guildId);
		if (objs.isEmpty() || objs.get(0) == null) return "http://example.com/";
		return String.valueOf(objs.get(0));
	}

	private String escapeCode(String text) {
		if (text.startsWith("```")) text = text.substring(3, text.length()-3);
		else if (text.startsWith("`")) text = text.substring(1, text.length()-1);

		return text.replaceAll("<br>", "\n");
	}
	
}
