package dev.fileeditor.votl.utils.database.managers;

import static dev.fileeditor.votl.utils.CastUtil.getOrDefault;

import java.util.Map;
import java.util.Set;

import dev.fileeditor.votl.objects.constants.Constants;
import dev.fileeditor.votl.utils.FixedCache;
import dev.fileeditor.votl.utils.database.ConnectionUtil;
import dev.fileeditor.votl.utils.database.LiteBase;

public class VerifySettingsManager extends LiteBase {

	private final Set<String> columns = Set.of("roleId", "panelText", "panelImage");

	// Cache
	private final FixedCache<Long, VerifySettings> cache = new FixedCache<>(Constants.DEFAULT_CACHE_SIZE);
	private final VerifySettings blankSettings = new VerifySettings();

	public VerifySettingsManager(ConnectionUtil cu) {
		super(cu, "verifySettings");
	}

	public VerifySettings getSettings(long guildId) {
		if (cache.contains(guildId))
			return cache.get(guildId);
		VerifySettings settings = applyNonNull(getData(guildId), data -> new VerifySettings(data));
		if (settings == null)
			return blankSettings;
		cache.put(guildId, settings);
		return settings;
	}

	private Map<String, Object> getData(long guildId) {
		return selectOne("SELECT * FROM %s WHERE (guildId=%d)".formatted(table, guildId), columns);
	}

	public void remove(long guildId) {
		invalidateCache(guildId);
		execute("DELETE FROM %s WHERE (guildId=%d)".formatted(table, guildId));
	}

	public void setVerifyRole(long guildId, long roleId) {
		invalidateCache(guildId);
		execute("INSERT INTO %s(guildId, roleId) VALUES (%d, %d) ON CONFLICT(guildId) DO UPDATE SET roleId=%<d".formatted(table, guildId, roleId));
	}

	public void setPanelText(long guildId, String text) {
		invalidateCache(guildId);
		final String textParsed = quote(text.replace("\\n", "<br>"));
		execute("INSERT INTO %s(guildId, panelText) VALUES (%d, %s) ON CONFLICT(guildId) DO UPDATE SET panelText=%<s".formatted(table, guildId, textParsed));
	}

	public void setPanelImage(long guildId, String imageUrl) {
		invalidateCache(guildId);
		execute("INSERT INTO %s(guildId, panelImage) VALUES (%d, %s) ON CONFLICT(guildId) DO UPDATE SET panelImage=%<s".formatted(table, guildId, imageUrl));
	}

	// Blacklist table
	/* public boolean blacklistUser(String guildId, String userId) {
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
	} */

	private void invalidateCache(long guildId) {
		cache.pull(guildId);
	}

	public class VerifySettings {
		private final Long roleId;
		private final String panelText, panelImageUrl;

		public VerifySettings() {
			this.roleId = null;
			this.panelText = null;
			this.panelImageUrl = null;
		}

		public VerifySettings(Map<String, Object> data) {
			this.roleId = getOrDefault(data.get("roleId"), null);
			this.panelText = getOrDefault(data.get("panelText"), null);
			this.panelImageUrl = getOrDefault(data.get("panelImage"), null);
		}

		public Long getRoleId() {
			return roleId;
		}

		public String getPanelText() {
			return panelText;
		}

		public String getPanelImageUrl() {
			return panelImageUrl;
		}
	}
	
}
