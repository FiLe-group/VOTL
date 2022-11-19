package votl.utils.database.managers;

import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

import votl.utils.database.DBBase;
import votl.utils.database.DBUtil;

public class WebhookManager extends DBBase {
	
	public WebhookManager(DBUtil util) {
		super(util);
	}

	public void add(String webhookId, String guildId, String token) {
		insert("webhook", List.of("webhookId", "guildId", "token"), List.of(webhookId, guildId, token));
	}

	public void remove(String webhookId) {
		delete("webhook", "webhookId", webhookId);
	}

	public boolean exists(String webhookId) {
		if (select("webhook", "webhookId", "webhookId", webhookId).isEmpty()) {
			return false;
		}
		return true;
	}

	public String getToken(String webhookId) {
		List<Object> objs = select("webhook", "token", "webhookId", webhookId);
		if (objs.isEmpty() || objs.get(0) == null) {
			return null;
		}
		return String.valueOf(objs.get(0));
	}

	public List<String> getIds(String guildId) {
		List<Object> objs = select("webhook", "webhookId", "guildId", guildId);
		if (objs.isEmpty()) {
			return Collections.emptyList();
		}
		return objs.stream().map(obj -> String.valueOf(obj)).collect(Collectors.toList());
	}

}
