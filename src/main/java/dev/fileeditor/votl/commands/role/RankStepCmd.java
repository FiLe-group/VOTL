package dev.fileeditor.votl.commands.role;

import dev.fileeditor.votl.base.command.SlashCommand;
import dev.fileeditor.votl.base.command.SlashCommandEvent;
import dev.fileeditor.votl.objects.AccessPermission;
import dev.fileeditor.votl.objects.CmdModule;
import dev.fileeditor.votl.objects.constants.CmdCategory;
import dev.fileeditor.votl.objects.constants.Constants;
import dev.fileeditor.votl.utils.database.managers.RankRolesManager.RankGroup;

import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.Role;
import net.dv8tion.jda.api.events.interaction.command.CommandAutoCompleteInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.Command;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Shared logic for {@code /promote} and {@code /demote}: both walk a member one step along a
 * rank ladder ({@link RankGroup}), granting the next/previous role and removing whichever ladder
 * role the member currently holds.
 */
abstract class RankStepCmd extends SlashCommand {

	protected RankStepCmd(String name, String path) {
		this.name = name;
		this.path = path;
		this.options = List.of(
			new OptionData(OptionType.USER, "member", lu.getText(path+".member.help"), true),
			new OptionData(OptionType.STRING, "group", lu.getText(path+".group.help"), true, true)
		);
		this.category = CmdCategory.ROLES;
		this.module = CmdModule.ROLES;
		this.requiredPermission = AccessPermission.CMD_RANK_ROLES;
	}

	/** {@code true} to move up the ladder (promote), {@code false} to move down (demote). */
	protected abstract boolean promote();

	@Override
	protected void execute(SlashCommandEvent event) {
		Guild guild = event.getGuild();
		assert guild != null;
		long guildId = guild.getIdLong();

		Member target = event.optMember("member");
		if (target == null) {
			editError(event, "errors.option.member");
			return;
		}
		Member mod = event.getMember();
		assert mod != null;
		if (target.getUser().isBot() || target.equals(guild.getSelfMember())) {
			editError(event, "errors.option.user_self");
			return;
		}
		if (!guild.getSelfMember().canInteract(target)
			|| (!mod.equals(target) && !mod.canInteract(target))) {
			editError(event, "errors.option.member_interact");
			return;
		}

		String groupName = event.optString("group", "");
		RankGroup group = bot.getDBUtil().rankRoles.getGroup(guildId, groupName);
		if (group == null) {
			editError(event, "bot.roles.rank_roles.group.not_found");
			return;
		}
		List<Long> ladder = group.roleIds();
		if (ladder.isEmpty()) {
			editError(event, path+".empty_group");
			return;
		}

		int currentIndex = -1;
		for (int i = ladder.size()-1; i >= 0; i--) {
			long roleId = ladder.get(i);
			if (target.getRoles().stream().anyMatch(r -> r.getIdLong() == roleId)) {
				currentIndex = i;
				break;
			}
		}

		int nextIndex;
		if (promote()) {
			if (currentIndex == ladder.size()-1) {
				editError(event, path+".at_top");
				return;
			}
			nextIndex = currentIndex + 1;
		} else {
			if (currentIndex == -1) {
				editError(event, path+".not_ranked");
				return;
			}
			nextIndex = currentIndex - 1;
		}

		Long addRoleId = nextIndex >= 0 ? ladder.get(nextIndex) : null;
		Long removeRoleId = currentIndex >= 0 ? ladder.get(currentIndex) : null;

		Role addRole = null;
		if (addRoleId != null) {
			addRole = guild.getRoleById(addRoleId);
			if (addRole == null) {
				// Stale ladder entry (role deleted outside of role-delete cleanup) - self-heal
				ignoreExc(() -> bot.getDBUtil().rankRoles.removeRole(group.groupId(), guildId, addRoleId));
				editError(event, path+".role_missing");
				return;
			}
			String denyReason = bot.getCheckUtil().denyRole(addRole, guild, mod, true);
			if (denyReason != null) {
				editError(event, "errors.option.role_interact", "Role: %s\n> %s".formatted(addRole.getAsMention(), denyReason));
				return;
			}
		}
		final Role finalAddRole = addRole;
		final Role finalRemoveRole = removeRoleId == null ? null : guild.getRoleById(removeRoleId);

		// Strip every ladder role the member currently holds, then add the new one (if any)
		Set<Long> ladderSet = new HashSet<>(ladder);
		List<Role> finalRoles = target.getRoles().stream()
			.filter(r -> !ladderSet.contains(r.getIdLong()))
			.collect(Collectors.toCollection(ArrayList::new));
		if (finalAddRole != null) finalRoles.add(finalAddRole);

		guild.modifyMemberRoles(target, finalRoles)
			.reason("Rank %s | by %s".formatted(promote() ? "promote" : "demote", mod.getEffectiveName()))
			.queue(_ -> {
				if (finalAddRole != null) bot.getGuildLogger().role.onRoleAdded(guild, event.getUser(), target.getUser(), finalAddRole);
				if (finalRemoveRole != null) bot.getGuildLogger().role.onRoleRemoved(guild, event.getUser(), target.getUser(), finalRemoveRole);

				String message = finalAddRole != null
					? lu.getGuildText(event, path+".done", target.getAsMention(), finalAddRole.getAsMention(), group.name())
					: lu.getGuildText(event, path+".done_removed", target.getAsMention(), group.name());

				editEmbed(event, bot.getEmbedUtil().getEmbed(Constants.COLOR_SUCCESS)
					.setDescription(message)
					.build());
			}, failure -> editError(event, path+".failed", failure.getMessage()));
	}

	@Override
	public void onAutoComplete(CommandAutoCompleteInteractionEvent event) {
		if (event.getGuild() == null) return;
		String query = event.getFocusedOption().getValue().toLowerCase();
		List<Command.Choice> choices = bot.getDBUtil().rankRoles
			.getGroupsForGuild(event.getGuild().getIdLong())
			.stream()
			.filter(g -> g.name().toLowerCase().contains(query))
			.limit(25)
			.map(g -> new Command.Choice(g.name(), g.name()))
			.collect(Collectors.toList());
		event.replyChoices(choices).queue();
	}
}
