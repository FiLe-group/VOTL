package dev.fileeditor.votl.utils;

import java.util.*;
import java.util.concurrent.CompletableFuture;

import dev.fileeditor.votl.App;
import dev.fileeditor.votl.utils.database.DBUtil;
import dev.fileeditor.votl.utils.logs.GuildLogger;

import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.automod.AutoModEventType;
import net.dv8tion.jda.api.entities.automod.AutoModResponse;
import net.dv8tion.jda.api.entities.automod.AutoModRule;
import net.dv8tion.jda.api.entities.automod.AutoModTriggerType;
import net.dv8tion.jda.api.entities.automod.build.AutoModRuleData;
import net.dv8tion.jda.api.entities.automod.build.CustomKeywordTriggerConfig;
import net.dv8tion.jda.api.entities.automod.build.MentionSpamTriggerConfig;
import net.dv8tion.jda.api.entities.automod.build.PresetKeywordTriggerConfig;
import net.dv8tion.jda.api.entities.automod.build.TriggerConfig;
import net.dv8tion.jda.api.exceptions.ErrorResponseException;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.slf4j.LoggerFactory;

import ch.qos.logback.classic.Logger;

/**
 * Pushes a source guild's AutoMod rule to every member guild of a group.
 * Only trigger config, actions (minus channel-specific alert messages), enabled state and name are synced -
 * exempt roles/channels are guild-specific and intentionally left untouched on the member's copy.
 */
public class AutoModSyncHelper {

	private final Logger log = (Logger) LoggerFactory.getLogger(AutoModSyncHelper.class);

	private final App bot;
	private final DBUtil db;
	private final GuildLogger logger;

	public AutoModSyncHelper(App bot) {
		this.bot = bot;
		this.db = bot.getDBUtil();
		this.logger = bot.getGuildLogger();
	}

	public String syncedName(int groupId, String sourceName) {
		String name = "Sync #%s: %s".formatted(groupId, sourceName);
		return name.length() > AutoModRule.MAX_RULE_NAME_LENGTH ? name.substring(0, AutoModRule.MAX_RULE_NAME_LENGTH) : name;
	}

	@Nullable
	private TriggerConfig buildTriggerConfig(@NotNull AutoModRule rule) {
		AutoModTriggerType type = rule.getTriggerType();
		return switch (type) {
			case KEYWORD, MEMBER_PROFILE_KEYWORD -> {
				CustomKeywordTriggerConfig cfg = TriggerConfig.keywordFilter(rule.getFilteredKeywords())
					.addPatterns(rule.getFilteredRegex());
				if (!rule.getAllowlist().isEmpty()) cfg.setAllowList(rule.getAllowlist());
				yield cfg;
			}
			case KEYWORD_PRESET -> {
				PresetKeywordTriggerConfig cfg = TriggerConfig.presetKeywordFilter(rule.getFilteredPresets());
				if (!rule.getAllowlist().isEmpty()) cfg.setAllowList(rule.getAllowlist());
				yield cfg;
			}
			case MENTION_SPAM -> {
				MentionSpamTriggerConfig cfg = TriggerConfig.mentionSpam(rule.getMentionLimit());
				cfg.setMentionRaidProtectionEnabled(rule.isMentionRaidProtectionEnabled());
				yield cfg;
			}
			case SPAM -> TriggerConfig.antiSpam();
			default -> null; // Unknown/unsupported trigger type - can't be rebuilt for another guild
		};
	}

	@NotNull
	private Collection<AutoModResponse> buildActions(@NotNull AutoModRule rule) {
		List<AutoModResponse> actions = new ArrayList<>();
		for (AutoModResponse response : rule.getActions()) {
			switch (response.getType()) {
				case BLOCK_MESSAGE -> actions.add(
					response.getCustomMessage() == null || response.getCustomMessage().isBlank()
						? AutoModResponse.blockMessage()
						: AutoModResponse.blockMessage(response.getCustomMessage())
				);
				case TIMEOUT -> actions.add(AutoModResponse.timeoutMember(Objects.requireNonNull(response.getTimeoutDuration())));
				case BLOCK_MEMBER_INTERACTION -> actions.add(AutoModResponse.blockMemberInteraction());
				// SEND_ALERT_MESSAGE references a channel that doesn't exist in the target guild - dropped
				default -> {}
			}
		}
		return actions;
	}

	@Nullable
	private AutoModRuleData buildRuleData(int groupId, @NotNull AutoModRule rule) {
		TriggerConfig trigger = buildTriggerConfig(rule);
		if (trigger == null) return null;

		String name = syncedName(groupId, rule.getName());
		AutoModRuleData data = rule.getEventType() == AutoModEventType.MEMBER_UPDATE
			? AutoModRuleData.onMemberProfile(name, trigger)
			: AutoModRuleData.onMessage(name, trigger);
		return data.setEnabled(rule.isEnabled()).setResponses(buildActions(rule));
	}

	// Pushes the current state of sourceRule to a single member guild, creating the copy if it doesn't exist yet
	// or recreating it if the previously-known copy was deleted manually.
	private CompletableFuture<Boolean> pushToGuild(int groupId, long ruleId, @NotNull AutoModRule sourceRule, long guildId) {
		Guild guild = bot.JDA.getGuildById(guildId);
		if (guild == null) return CompletableFuture.completedFuture(false);

		AutoModRuleData data = buildRuleData(groupId, sourceRule);
		if (data == null) {
			log.warn("Unsupported AutoMod trigger type {} for rule {}, skipping sync to guild {}", sourceRule.getTriggerType(), ruleId, guildId);
			return CompletableFuture.completedFuture(false);
		}

		Long targetId = db.automodSync.getTargetRuleId(groupId, ruleId, guildId);
		CompletableFuture<Boolean> future = new CompletableFuture<>();
		if (targetId == null) {
			createRule(groupId, ruleId, guild, data, future);
		} else {
			guild.modifyAutoModRuleById(targetId)
				.setName(syncedName(groupId, sourceRule.getName()))
				.setEnabled(sourceRule.isEnabled())
				.setResponses(buildActions(sourceRule))
				.setTriggerConfig(Objects.requireNonNull(buildTriggerConfig(sourceRule)))
				.queue(
					_ -> future.complete(true),
					failure -> {
						// Member's copy was deleted manually - recreate it rather than reacting live to that deletion
						if (isNotFound(failure)) {
							createRule(groupId, ruleId, guild, data, future);
						} else {
							log.warn("Failed to push AutoMod sync to guild {}: {}", guildId, failure.getMessage());
							future.complete(false);
						}
					}
				);
		}
		return future;
	}

	private void createRule(int groupId, long ruleId, @NotNull Guild guild, @NotNull AutoModRuleData data, CompletableFuture<Boolean> future) {
		guild.createAutoModRule(data).queue(
			created -> {
				ignoreExc(() -> db.automodSync.setTarget(groupId, ruleId, guild.getIdLong(), created.getIdLong()));
				future.complete(true);
			},
			failure -> {
				log.warn("Failed to create synced AutoMod rule in guild {}: {}", guild.getIdLong(), failure.getMessage());
				future.complete(false);
			}
		);
	}

	private boolean isNotFound(Throwable failure) {
		return failure instanceof ErrorResponseException ex && ex.getResponse().code == 404;
	}

	// Pushes the given rule to every member guild of the group. Called on initial sync setup and whenever the owner updates the rule.
	public void pushSync(int groupId, @NotNull AutoModRule sourceRule) {
		final long ruleId = sourceRule.getIdLong();
		final String ruleName = sourceRule.getName();
		ignoreExc(() -> db.automodSync.updateRuleName(groupId, ruleId, ruleName));

		List<Long> guildIds = db.group.getGroupMembers(groupId);
		if (guildIds.isEmpty()) return;

		List<CompletableFuture<Boolean>> futures = guildIds.stream()
			.map(guildId -> pushToGuild(groupId, ruleId, sourceRule, guildId))
			.toList();

		CompletableFuture.allOf(futures.toArray(new CompletableFuture[0]))
			.thenRun(() -> {
				int success = (int) futures.stream().filter(f -> Boolean.TRUE.equals(f.join())).count();
				logger.group.onAutomodRuleSynced(sourceRule.getGuild(), groupId, ruleId, ruleName, success, guildIds.size());
			});
	}

	// Applies a single already-synced rule to one guild - used when a guild joins a group that already has synced rules.
	public void pushToNewMember(int groupId, long guildId) {
		Long ownerId = db.group.getOwner(groupId);
		if (ownerId == null) return;
		Guild owner = bot.JDA.getGuildById(ownerId);
		if (owner == null) return;

		for (long ruleId : db.automodSync.getSyncedRules(groupId)) {
			owner.retrieveAutoModRuleById(ruleId).queue(
				rule -> pushToGuild(groupId, ruleId, rule, guildId),
				failure -> log.warn("Failed to retrieve AutoMod rule {} while applying group #{} to new member {}: {}", ruleId, groupId, guildId, failure.getMessage())
			);
		}
	}

	// Deletes the synced copy of every rule in the group from a single guild - used when it leaves/is removed from the group.
	public void removeFromGuild(int groupId, long guildId) {
		Guild guild = bot.JDA.getGuildById(guildId);
		for (long ruleId : db.automodSync.getSyncedRules(groupId)) {
			Long targetId = db.automodSync.getTargetRuleId(groupId, ruleId, guildId);
			if (targetId != null && guild != null) {
				guild.deleteAutoModRuleById(targetId).queue(_ -> {}, _ -> {});
			}
			ignoreExc(() -> db.automodSync.removeTarget(groupId, ruleId, guildId));
		}
	}

	// Deletes the rule from every member guild and un-registers it entirely. Called on manual removal and when the owner deletes the source rule.
	public void pushDelete(int groupId, long ruleId, @NotNull String ruleName, boolean logToOwner) {
		Map<Long, Long> targets = db.automodSync.getTargets(groupId, ruleId);

		List<CompletableFuture<Void>> futures = new ArrayList<>();
		for (Map.Entry<Long, Long> entry : targets.entrySet()) {
			long guildId = entry.getKey();
			Long targetId = entry.getValue();
			Guild guild = bot.JDA.getGuildById(guildId);
			if (targetId == null || guild == null) continue;

			futures.add(guild.deleteAutoModRuleById(targetId).submit()
				.exceptionally(_ -> null));
		}

		CompletableFuture.allOf(futures.toArray(new CompletableFuture[0]))
			.whenComplete((_, _) -> {
				ignoreExc(() -> db.automodSync.removeTargetsForRule(groupId, ruleId));
				ignoreExc(() -> db.automodSync.removeSyncRule(groupId, ruleId));

				if (logToOwner) {
					Long ownerId = db.group.getOwner(groupId);
					Guild owner = ownerId == null ? null : bot.JDA.getGuildById(ownerId);
					if (owner != null) logger.group.onAutomodRuleDeleted(owner, groupId, ruleId, ruleName);
				}
			});
	}

	private void ignoreExc(RunnableExc runnable) {
		try {
			runnable.run();
		} catch (Exception ignored) {}
	}

	@FunctionalInterface private interface RunnableExc { void run() throws Exception; }
}
