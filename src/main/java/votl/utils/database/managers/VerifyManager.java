package votl.utils.database.managers;

import java.util.List;

import votl.utils.database.DBUtil;
import votl.utils.database.SqlDBBase;

public class VerifyManager extends SqlDBBase {

	private String table;

	public VerifyManager(DBUtil util) {
		super(util);
		this.table = util.sqldb + ".users";
	}

	public String getDiscordId(String steam64) {
		List<String> data = select(table, "discord_id", "steam_id", steam64);
		if (data.isEmpty() || data.get(0) == null) return null;
        return data.get(0);
	}

	public String getSteamName(String steam64) {
		List<String> data = select(table, "name", "steam_id", steam64);
		if (data.isEmpty() || data.get(0) == null) return null;
        return data.get(0);
	}

	public String getSteam64(String discordId) {
		List<String> data = select(table, "steam_id", "discord_id", discordId);
		if (data.isEmpty() || data.get(0) == null) return null;
        return data.get(0);
	}

	public boolean existsDiscord(String discordId) {
		if (select(table, "steam_id", "discord_id", discordId).isEmpty()) {
			return false;
		}
		return true;
	}

	public boolean existsSteam(String steam64) {
		if (select(table, "discord_id", "steam_id", steam64).isEmpty()) {
			return false;
		}
		return true;
	}
	
}
