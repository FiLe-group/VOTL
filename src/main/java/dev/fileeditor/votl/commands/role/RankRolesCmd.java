package dev.fileeditor.votl.commands.role;

import dev.fileeditor.votl.base.command.SlashCommand;
import dev.fileeditor.votl.base.command.SlashCommandEvent;
import dev.fileeditor.votl.objects.AccessPermission;
import dev.fileeditor.votl.objects.CmdModule;
import dev.fileeditor.votl.objects.constants.CmdCategory;
import dev.fileeditor.votl.objects.constants.Constants;
import dev.fileeditor.votl.objects.constants.Limits;
import dev.fileeditor.votl.utils.database.managers.RankRolesManager.RankGroup;

import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Role;
import net.dv8tion.jda.api.events.interaction.command.CommandAutoCompleteInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.Command;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import net.dv8tion.jda.api.interactions.commands.build.SubcommandGroupData;

import java.sql.SQLException;
import java.util.List;
import java.util.stream.Collectors;

public class RankRolesCmd extends SlashCommand {

	public RankRolesCmd() {
		this.name = "rank-roles";
		this.path = "bot.roles.rank_roles";
		this.children = new SlashCommand[]{
			new GroupCreate(), new GroupDelete(), new GroupList(),
			new RoleAdd(), new RoleRemove()
		};
		this.category = CmdCategory.ROLES;
		this.module = CmdModule.ROLES;
		this.requiredPermission = AccessPermission.ADMIN;
	}

	@Override
	protected void execute(SlashCommandEvent event) {}

	// ---- /rank-roles group create ----

	private class GroupCreate extends SlashCommand {
		public GroupCreate() {
			this.name = "create";
			this.path = "bot.roles.rank_roles.group.create";
			this.options = List.of(
				new OptionData(OptionType.STRING, "name", lu.getText(path+".name.help"), true)
					.setMaxLength(32)
			);
			this.subcommandGroup = new SubcommandGroupData("group", lu.getText("bot.roles.rank_roles.group.help"));
		}

		@Override
		protected void execute(SlashCommandEvent event) {
			Guild guild = event.getGuild();
			assert guild != null;
			long guildId = guild.getIdLong();

			if (bot.getDBUtil().rankRoles.countGroups(guildId) >= Limits.RANK_ROLE_GROUPS) {
				editErrorLimit(event, "rank groups", Limits.RANK_ROLE_GROUPS);
				return;
			}

			String name = event.optString("name", "").trim();
			if (!name.matches("[\\w\\s\\-]{1,32}")) {
				editError(event, path+".invalid_name");
				return;
			}
			if (bot.getDBUtil().rankRoles.getGroup(guildId, name) != null) {
				editError(event, path+".already_exists");
				return;
			}

			try {
				bot.getDBUtil().rankRoles.createGroup(guildId, name);
			} catch (SQLException ex) {
				editErrorDatabase(event, ex, "create rank group");
				return;
			}

			editEmbed(event, bot.getEmbedUtil().getEmbed(Constants.COLOR_SUCCESS)
				.setDescription(lu.getGuildText(event, path+".done", name))
				.build()
			);
		}
	}

	// ---- /rank-roles group delete ----

	private class GroupDelete extends SlashCommand {
		public GroupDelete() {
			this.name = "delete";
			this.path = "bot.roles.rank_roles.group.delete";
			this.options = List.of(
				new OptionData(OptionType.STRING, "group", lu.getText(path+".group.help"), true, true)
			);
			this.subcommandGroup = new SubcommandGroupData("group", lu.getText("bot.roles.rank_roles.group.help"));
		}

		@Override
		protected void execute(SlashCommandEvent event) {
			Guild guild = event.getGuild();
			assert guild != null;

			RankGroup group = resolveGroup(event, guild.getIdLong());
			if (group == null) return;

			try {
				bot.getDBUtil().rankRoles.deleteGroup(group.groupId(), guild.getIdLong());
			} catch (SQLException ex) {
				editErrorDatabase(event, ex, "delete rank group");
				return;
			}

			editEmbed(event, bot.getEmbedUtil().getEmbed(Constants.COLOR_SUCCESS)
				.setDescription(lu.getGuildText(event, path+".done", group.name()))
				.build()
			);
		}

		@Override
		public void onAutoComplete(CommandAutoCompleteInteractionEvent event) {
			replyGroupAutocomplete(event);
		}
	}

	// ---- /rank-roles group list ----

	private class GroupList extends SlashCommand {
		public GroupList() {
			this.name = "list";
			this.path = "bot.roles.rank_roles.group.list";
			this.subcommandGroup = new SubcommandGroupData("group", lu.getText("bot.roles.rank_roles.group.help"));
			this.ephemeral = true;
		}

		@Override
		protected void execute(SlashCommandEvent event) {
			Guild guild = event.getGuild();
			assert guild != null;

			List<RankGroup> groups = bot.getDBUtil().rankRoles.getGroupsForGuild(guild.getIdLong());
			if (groups.isEmpty()) {
				editEmbed(event, bot.getEmbedUtil().getEmbed()
					.setDescription(lu.getGuildText(event, path+".none"))
					.build()
				);
				return;
			}

			EmbedBuilder eb = bot.getEmbedUtil().getEmbed()
				.setTitle(lu.getGuildText(event, path+".title"));
			StringBuilder sb = new StringBuilder();
			for (RankGroup g : groups) {
				sb.append("**").append(g.name()).append("**\n");
				if (g.roleIds().isEmpty()) {
					sb.append("> ").append(lu.getGuildText(event, path+".none_roles")).append("\n");
				} else {
					String ladder = g.roleIds().stream()
						.map("<@&%d>"::formatted)
						.collect(Collectors.joining(" → "));
					sb.append("> ").append(ladder).append("\n");
				}
			}
			editEmbed(event, eb.setDescription(sb.toString()).build());
		}
	}

	// ---- /rank-roles role add ----

	private class RoleAdd extends SlashCommand {
		public RoleAdd() {
			this.name = "add";
			this.path = "bot.roles.rank_roles.role.add";
			this.options = List.of(
				new OptionData(OptionType.STRING, "group", lu.getText(path+".group.help"), true, true),
				new OptionData(OptionType.ROLE, "role", lu.getText(path+".role.help"), true)
			);
			this.subcommandGroup = new SubcommandGroupData("role", lu.getText("bot.roles.rank_roles.role.help"));
		}

		@Override
		protected void execute(SlashCommandEvent event) {
			Guild guild = event.getGuild();
			assert guild != null && event.getMember() != null;
			long guildId = guild.getIdLong();

			RankGroup group = resolveGroup(event, guildId);
			if (group == null) return;

			Role role = event.optRole("role");
			if (role == null) {
				editError(event, "errors.option.role");
				return;
			}
			// The invoker must actually be able to manage this role, since it will later be
			// handed out to members via /promote.
			String denyReason = bot.getCheckUtil().denyRole(role, guild, event.getMember(), true);
			if (denyReason != null) {
				editError(event, "errors.option.role_interact", "Role: %s\n> %s".formatted(role.getAsMention(), denyReason));
				return;
			}
			if (group.roleIds().contains(role.getIdLong())) {
				editError(event, path+".already");
				return;
			}
			if (group.roleIds().size() >= Limits.RANK_ROLE_GROUP_ROLES) {
				editErrorLimit(event, "roles in rank group", Limits.RANK_ROLE_GROUP_ROLES);
				return;
			}

			try {
				bot.getDBUtil().rankRoles.addRole(group.groupId(), guildId, role.getIdLong());
			} catch (SQLException ex) {
				editErrorDatabase(event, ex, "add role to rank group");
				return;
			}

			editEmbed(event, bot.getEmbedUtil().getEmbed(Constants.COLOR_SUCCESS)
				.setDescription(lu.getGuildText(event, path+".done", role.getAsMention(), group.name()))
				.build()
			);
		}

		@Override
		public void onAutoComplete(CommandAutoCompleteInteractionEvent event) {
			replyGroupAutocomplete(event);
		}
	}

	// ---- /rank-roles role remove ----

	private class RoleRemove extends SlashCommand {
		public RoleRemove() {
			this.name = "remove";
			this.path = "bot.roles.rank_roles.role.remove";
			this.options = List.of(
				new OptionData(OptionType.STRING, "group", lu.getText(path+".group.help"), true, true),
				new OptionData(OptionType.ROLE, "role", lu.getText(path+".role.help"), true)
			);
			this.subcommandGroup = new SubcommandGroupData("role", lu.getText("bot.roles.rank_roles.role.help"));
		}

		@Override
		protected void execute(SlashCommandEvent event) {
			Guild guild = event.getGuild();
			assert guild != null;
			long guildId = guild.getIdLong();

			RankGroup group = resolveGroup(event, guildId);
			if (group == null) return;

			Role role = event.optRole("role");
			if (role == null) {
				editError(event, "errors.option.role");
				return;
			}
			if (!group.roleIds().contains(role.getIdLong())) {
				editError(event, path+".not_in_group");
				return;
			}

			try {
				bot.getDBUtil().rankRoles.removeRole(group.groupId(), guildId, role.getIdLong());
			} catch (SQLException ex) {
				editErrorDatabase(event, ex, "remove role from rank group");
				return;
			}

			editEmbed(event, bot.getEmbedUtil().getEmbed(Constants.COLOR_SUCCESS)
				.setDescription(lu.getGuildText(event, path+".done", role.getAsMention(), group.name()))
				.build()
			);
		}

		@Override
		public void onAutoComplete(CommandAutoCompleteInteractionEvent event) {
			replyGroupAutocomplete(event);
		}
	}

	// ---- Shared helpers ----

	private RankGroup resolveGroup(SlashCommandEvent event, long guildId) {
		String name = event.optString("group", "");
		RankGroup group = bot.getDBUtil().rankRoles.getGroup(guildId, name);
		if (group == null) {
			editError(event, "bot.roles.rank_roles.group.not_found");
		}
		return group;
	}

	private void replyGroupAutocomplete(CommandAutoCompleteInteractionEvent event) {
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
