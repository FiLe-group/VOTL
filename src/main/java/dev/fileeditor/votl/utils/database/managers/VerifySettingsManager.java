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
		VerifySettings settings = applyNonNull(getData(guildId), VerifySettings::new);
		if (settings == null)
			settings = blankSettings;
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
		execute("INSERT INTO %s(guildId, panelImage) VALUES (%d, %s) ON CONFLICT(guildId) DO UPDATE SET panelImage=%<s".formatted(table, guildId, quote(imageUrl)));
	}

	private void invalidateCache(long guildId) {
		cache.pull(guildId);
	}

	public static class VerifySettings {
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
