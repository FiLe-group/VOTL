package dev.fileeditor.votl.utils;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import dev.fileeditor.votl.App;
import dev.fileeditor.votl.objects.CaseType;
import dev.fileeditor.votl.utils.database.DBUtil;
import dev.fileeditor.votl.utils.logs.GuildLogger;

import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.User;
import org.jetbrains.annotations.NotNull;

public class GroupHelper {

	private final App bot;
	private final GuildLogger logger;
	private final DBUtil db;

	public GroupHelper(App bot) {
		this.bot = bot;
		this.logger = bot.getGuildLogger();
		this.db = bot.getDBUtil();
	}

	// Recursively collects all guild IDs that should receive a sync action for the given group.
	// Traverses through every member's owned sub-groups and prevents cycles via visitedGroups.
	private void collectRecursive(int groupId, Set<Long> guildIds, Set<Integer> visitedGroups) {
		if (!visitedGroups.add(groupId)) return;
		for (long memberId : db.group.getGroupMembers(groupId)) {
			guildIds.add(memberId);
			for (int ownedGroupId : db.group.getOwnedGroups(memberId)) {
				collectRecursive(ownedGroupId, guildIds, visitedGroups);
			}
		}
	}

	private Set<Long> collectGuildIds(int groupId) {
		Set<Long> guildIds = new LinkedHashSet<>();
		collectRecursive(groupId, guildIds, new HashSet<>());
		return guildIds;
	}

	// Collects all group IDs this guild is a member of, walking up through group owners.
	// Prevents cycles by tracking visited guild IDs.
	private void collectGroupsUpward(long guildId, Set<Integer> groupIds, Set<Long> visitedGuilds) {
		if (!visitedGuilds.add(guildId)) return;
		for (int groupId : db.group.getGuildGroups(guildId)) {
			if (groupIds.add(groupId)) {
				Long ownerId = db.group.getOwner(groupId);
				if (ownerId != null) {
					collectGroupsUpward(ownerId, groupIds, visitedGuilds);
				}
			}
		}
	}

	public Set<Integer> collectParentGroupIds(long guildId) {
		Set<Integer> groupIds = new LinkedHashSet<>();
		collectGroupsUpward(guildId, groupIds, new HashSet<>());
		return groupIds;
	}

	private void banUser(int groupId, @NotNull Guild master, @NotNull User target, @NotNull String reason, @NotNull String modName) {
		final Set<Long> guildIds = collectGuildIds(groupId);
		if (guildIds.isEmpty()) return;

		final int maxCount = guildIds.size();
		final List<CompletableFuture<Void>> completableFutures = new ArrayList<>();
		final String newReason = "Sync #%s by @%s: %s".formatted(groupId, modName, reason);
		for (long guildId : guildIds) {
			final Guild guild = bot.JDA.getGuildById(guildId);
			if (guild == null) continue;
			// fail-safe check if the target has temporal ban (to prevent auto unban)
			db.cases.setInactiveByType(target.getIdLong(), guildId, CaseType.BAN);

			completableFutures.add(guild.ban(target, 0, TimeUnit.SECONDS).reason(newReason).submit());
		}

		CompletableFuture.allOf(completableFutures.toArray(new CompletableFuture[0]))
			.whenComplete((_, _) -> {
				int banned = 0;
				for (CompletableFuture<Void> future : completableFutures) {
					if (!future.isCompletedExceptionally()) banned++;
				}
				// Log in server where 
				logger.mod.onHelperSyncBan(groupId, master, target, reason, banned, maxCount);
			});
	}

	private void unbanUser(int groupId, @NotNull Guild master, @NotNull User target, @NotNull String reason, @NotNull String modName) {
		final Set<Long> guildIds = collectGuildIds(groupId);
		if (guildIds.isEmpty()) return;

		final int maxCount = guildIds.size();
		final List<CompletableFuture<Void>> completableFutures = new ArrayList<>();
		final String newReason = "Sync #%s by @%s: %s".formatted(groupId, modName, reason);
		for (long guildId : guildIds) {
			final Guild guild = bot.JDA.getGuildById(guildId);
			if (guild == null) continue;
			// Remove temporal ban case
			db.cases.setInactiveByType(target.getIdLong(), guildId, CaseType.BAN);

			completableFutures.add(guild.unban(target).reason(newReason).submit());
		}

		CompletableFuture.allOf(completableFutures.toArray(new CompletableFuture[0]))
			.whenComplete((_, _) -> {
				int unbanned = 0;
				for (CompletableFuture<Void> future : completableFutures) {
					if (!future.isCompletedExceptionally()) unbanned++;
				}
				logger.mod.onHelperSyncUnban(groupId, master, target, reason, unbanned, maxCount);
			});
	}

	private void kickUser(int groupId, @NotNull Guild master, @NotNull User target, @NotNull String reason, @NotNull String modName) {
		final Set<Long> guildIds = collectGuildIds(groupId);
		if (guildIds.isEmpty()) return;

		final int maxCount = guildIds.size();
		final List<CompletableFuture<Void>> completableFutures = new ArrayList<>();
		final String newReason = "Sync #%s by @%s: %s".formatted(groupId, modName, reason);
		for (long guildId : guildIds) {
			final Guild guild = bot.JDA.getGuildById(guildId);
			if (guild == null) continue;

			completableFutures.add(guild.kick(target).reason(newReason).submit());
		}

		CompletableFuture.allOf(completableFutures.toArray(new CompletableFuture[0]))
			.whenComplete((_, _) -> {
				int kicked = 0;
				for (CompletableFuture<Void> future : completableFutures) {
					if (!future.isCompletedExceptionally()) kicked++;
				}
				logger.mod.onHelperSyncKick(groupId, master, target, reason, kicked, maxCount);
			});
	}

	public void runBan(int groupId, @NotNull Guild master, @NotNull User target, @NotNull String reason, @NotNull User mod) {
		CompletableFuture.runAsync(() -> banUser(groupId, master, target, reason, mod.getName()));
	}

	public void runUnban(int groupId, @NotNull Guild master, @NotNull User target, @NotNull String reason, @NotNull User mod) {
		CompletableFuture.runAsync(() -> unbanUser(groupId, master, target, reason, mod.getName()));
	}

	public void runKick(int groupId, @NotNull Guild master, @NotNull User target, @NotNull String reason, @NotNull User mod) {
		CompletableFuture.runAsync(() -> kickUser(groupId, master, target, reason, mod.getName()));
	}
	
}
