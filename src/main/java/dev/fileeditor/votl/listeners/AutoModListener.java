package dev.fileeditor.votl.listeners;

import dev.fileeditor.votl.App;
import dev.fileeditor.votl.utils.database.DBUtil;

import net.dv8tion.jda.api.entities.automod.AutoModRule;
import net.dv8tion.jda.api.events.automod.AutoModRuleDeleteEvent;
import net.dv8tion.jda.api.events.automod.AutoModRuleUpdateEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;

import org.jetbrains.annotations.NotNull;

/**
 * Reacts only to AutoMod rule changes made in a group's owner guild for rules registered for sync.
 * Member guilds' own edits/deletions of their synced copy are intentionally ignored - group sync
 * only pushes from the owner down, never the other way.
 */
public class AutoModListener extends ListenerAdapter {

	private final App bot;
	private final DBUtil db;

	public AutoModListener(App bot) {
		this.bot = bot;
		this.db = bot.getDBUtil();
	}

	@Override
	public void onAutoModRuleUpdate(@NotNull AutoModRuleUpdateEvent event) {
		AutoModRule rule = event.getRule();
		long guildId = rule.getGuild().getIdLong();

		for (int groupId : db.group.getOwnedGroups(guildId)) {
			if (db.automodSync.isSynced(groupId, rule.getIdLong())) {
				bot.getAutoModSyncHelper().pushSync(groupId, rule);
			}
		}
	}

	@Override
	public void onAutoModRuleDelete(@NotNull AutoModRuleDeleteEvent event) {
		AutoModRule rule = event.getRule();
		long guildId = rule.getGuild().getIdLong();
		long ruleId = rule.getIdLong();
		String ruleName = rule.getName();

		for (int groupId : db.group.getOwnedGroups(guildId)) {
			if (db.automodSync.isSynced(groupId, ruleId)) {
				bot.getAutoModSyncHelper().pushDelete(groupId, ruleId, ruleName, true);
			}
		}
	}

}
