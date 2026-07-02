package dev.fileeditor.votl.commands.guild;

import dev.fileeditor.votl.base.command.SlashCommand;
import dev.fileeditor.votl.base.command.SlashCommandEvent;
import dev.fileeditor.votl.objects.AccessPermission;
import dev.fileeditor.votl.objects.constants.CmdCategory;
import dev.fileeditor.votl.objects.constants.Constants;
import dev.fileeditor.votl.objects.constants.Limits;
import dev.fileeditor.votl.utils.database.managers.AccessGroupManager.GroupData;
import dev.fileeditor.votl.utils.exception.FormatterException;
import dev.fileeditor.votl.utils.message.TimeUtil;

import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.Role;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.events.interaction.command.CommandAutoCompleteInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.Command;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import net.dv8tion.jda.api.interactions.commands.build.SubcommandGroupData;

import java.sql.SQLException;
import java.time.Duration;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

public class AccessCmd extends SlashCommand {

	public AccessCmd() {
		this.name = "access";
		this.path = "bot.guild.access";
		this.children = new SlashCommand[]{
			new GroupCreate(), new GroupDelete(), new GroupRename(),
			new GroupPermission(), new GroupLimit(),
			new GroupInfo(), new GroupList(),
			new MemberAddRole(), new MemberRemoveRole(),
			new MemberAddUser(), new MemberRemoveUser()
		};
		this.category = CmdCategory.GUILD;
		this.requiredPermission = AccessPermission.ADMIN;
	}

	@Override
	protected void execute(SlashCommandEvent event) {}

	// ---- /access group create ----

	private class GroupCreate extends SlashCommand {
		public GroupCreate() {
			this.name = "create";
			this.path = "bot.guild.access.group.create";
			this.options = List.of(
				new OptionData(OptionType.STRING, "name", lu.getText(path+".name.help"), true)
					.setMaxLength(32)
			);
			this.subcommandGroup = new SubcommandGroupData("group", lu.getText("bot.guild.access.group.help"));
			this.requiredPermission = AccessPermission.OWNER;
		}

		@Override
		protected void execute(SlashCommandEvent event) {
			Guild guild = event.getGuild();
			assert guild != null;
			long guildId = guild.getIdLong();

			if (bot.getDBUtil().accessGroups.countGroups(guildId) >= Limits.ACCESS_GROUPS) {
				editErrorLimit(event, "groups", Limits.ACCESS_GROUPS);
				return;
			}

			String name = event.optString("name", "").trim();
			if (!name.matches("[\\w\\s\\-]{1,32}")) {
				editError(event, "bot.guild.access.group.create.invalid_name");
				return;
			}
			if (bot.getDBUtil().accessGroups.getGroup(guildId, name) != null) {
				editError(event, "bot.guild.access.group.create.already_exists");
				return;
			}

			try {
				bot.getDBUtil().accessGroups.createGroup(guildId, name);
			} catch (SQLException ex) {
				editErrorDatabase(event, ex, "create group");
				return;
			}

			editEmbed(event, bot.getEmbedUtil().getEmbed(Constants.COLOR_SUCCESS)
				.setDescription(lu.getGuildText(event, path+".done", name))
				.build()
			);
		}
	}

	// ---- /access group delete ----

	private class GroupDelete extends SlashCommand {
		public GroupDelete() {
			this.name = "delete";
			this.path = "bot.guild.access.group.delete";
			this.options = List.of(
				new OptionData(OptionType.STRING, "group", lu.getText(path+".group.help"), true, true)
			);
			this.subcommandGroup = new SubcommandGroupData("group", lu.getText("bot.guild.access.group.help"));
			this.requiredPermission = AccessPermission.OWNER;
		}

		@Override
		protected void execute(SlashCommandEvent event) {
			Guild guild = event.getGuild();
			assert guild != null;

			GroupData group = resolveGroup(event, guild.getIdLong());
			if (group == null) return;

			try {
				bot.getDBUtil().accessGroups.deleteGroup(group.groupId(), guild.getIdLong());
			} catch (SQLException ex) {
				editErrorDatabase(event, ex, "delete group");
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

	// ---- /access group rename ----

	private class GroupRename extends SlashCommand {
		public GroupRename() {
			this.name = "rename";
			this.path = "bot.guild.access.group.rename";
			this.options = List.of(
				new OptionData(OptionType.STRING, "group", lu.getText(path+".group.help"), true, true),
				new OptionData(OptionType.STRING, "name", lu.getText(path+".name.help"), true)
					.setMaxLength(32)
			);
			this.subcommandGroup = new SubcommandGroupData("group", lu.getText("bot.guild.access.group.help"));
			this.requiredPermission = AccessPermission.OWNER;
		}

		@Override
		protected void execute(SlashCommandEvent event) {
			Guild guild = event.getGuild();
			assert guild != null;
			long guildId = guild.getIdLong();

			GroupData group = resolveGroup(event, guildId);
			if (group == null) return;

			String newName = event.optString("name", "").trim();
			if (!newName.matches("[\\w\\s\\-]{1,32}")) {
				editError(event, "bot.guild.access.group.create.invalid_name");
				return;
			}
			if (bot.getDBUtil().accessGroups.getGroup(guildId, newName) != null) {
				editError(event, "bot.guild.access.group.create.already_exists");
				return;
			}

			try {
				bot.getDBUtil().accessGroups.renameGroup(group.groupId(), guildId, newName);
			} catch (SQLException ex) {
				editErrorDatabase(event, ex, "rename group");
				return;
			}

			editEmbed(event, bot.getEmbedUtil().getEmbed(Constants.COLOR_SUCCESS)
				.setDescription(lu.getGuildText(event, path+".done", group.name(), newName))
				.build()
			);
		}

		@Override
		public void onAutoComplete(CommandAutoCompleteInteractionEvent event) {
			replyGroupAutocomplete(event);
		}
	}

	// ---- /access group permission ----

	private class GroupPermission extends SlashCommand {
		public GroupPermission() {
			this.name = "permission";
			this.path = "bot.guild.access.group.permission";
			this.options = List.of(
				new OptionData(OptionType.STRING, "group", lu.getText(path+".group.help"), true, true),
				new OptionData(OptionType.STRING, "permission", lu.getText(path+".permission.help"), true, true),
				new OptionData(OptionType.BOOLEAN, "value", lu.getText(path+".value.help"), true)
			);
			this.subcommandGroup = new SubcommandGroupData("group", lu.getText("bot.guild.access.group.help"));
			this.requiredPermission = AccessPermission.OWNER;
		}

		@Override
		protected void execute(SlashCommandEvent event) {
			Guild guild = event.getGuild();
			assert guild != null;
			long guildId = guild.getIdLong();

			GroupData group = resolveGroup(event, guildId);
			if (group == null) return;

			String permName = event.optString("permission", "");
			AccessPermission perm;
			try {
				perm = AccessPermission.valueOf(permName.toUpperCase());
			} catch (IllegalArgumentException ex) {
				editError(event, "bot.guild.access.group.permission.invalid");
				return;
			}
			if (perm.hidden) {
				editError(event, "bot.guild.access.group.permission.invalid");
				return;
			}

			boolean value = event.optBoolean("value");
			long newBitmask = value
				? group.permissions() | perm.toBit()
				: group.permissions() & ~perm.toBit();

			try {
				bot.getDBUtil().accessGroups.setPermissions(group.groupId(), guildId, newBitmask);
			} catch (SQLException ex) {
				editErrorDatabase(event, ex, "set permission");
				return;
			}

			editEmbed(event, bot.getEmbedUtil().getEmbed(Constants.COLOR_SUCCESS)
				.setDescription(lu.getGuildText(event, path+".done",
					group.name(), perm.name().toLowerCase(), value ? "✅" : "❌"))
				.build()
			);
		}

		@Override
		public void onAutoComplete(CommandAutoCompleteInteractionEvent event) {
			String focused = event.getFocusedOption().getName();
			if (focused.equals("group")) {
				replyGroupAutocomplete(event);
			} else {
				String query = event.getFocusedOption().getValue().toUpperCase();
				List<Command.Choice> choices = Arrays.stream(AccessPermission.values())
					.filter(p -> !p.hidden)
					.filter(p -> p.name().contains(query))
					.limit(25)
					.map(p -> new Command.Choice(p.name().toLowerCase(), p.name()))
					.collect(Collectors.toList());
				event.replyChoices(choices).queue();
			}
		}
	}

	// ---- /access group limit ----

	private class GroupLimit extends SlashCommand {
		public GroupLimit() {
			this.name = "limit";
			this.path = "bot.guild.access.group.limit";
			this.options = List.of(
				new OptionData(OptionType.STRING, "group", lu.getText(path+".group.help"), true, true),
				new OptionData(OptionType.STRING, "type", lu.getText(path+".type.help"), true)
					.addChoice("ban", "ban")
					.addChoice("mute", "mute"),
				new OptionData(OptionType.STRING, "duration", lu.getText(path+".duration.help"), true)
					.setMaxLength(16)
			);
			this.subcommandGroup = new SubcommandGroupData("group", lu.getText("bot.guild.access.group.help"));
			this.requiredPermission = AccessPermission.OWNER;
		}

		@Override
		protected void execute(SlashCommandEvent event) {
			Guild guild = event.getGuild();
			assert guild != null;
			long guildId = guild.getIdLong();

			GroupData group = resolveGroup(event, guildId);
			if (group == null) return;

			String type = event.optString("type", "ban");
			String durationStr = event.optString("duration", "none");

			Long seconds = null;
			String display;
			if (!durationStr.equalsIgnoreCase("none")) {
				try {
					Duration d = TimeUtil.stringToDuration(durationStr, false);
					if (d.isZero()) {
						editError(event, "bot.guild.access.group.limit.invalid_duration");
						return;
					}
					if (d.toDays() > 90) {
						editError(event, "bot.guild.access.group.limit.max_duration");
						return;
					}
					seconds = d.toSeconds();
					display = TimeUtil.durationToString(d);
				} catch (FormatterException ex) {
					editError(event, ex.getPath());
					return;
				}
			} else {
				display = lu.getGuildText(event, path+".unlimited");
			}

			try {
				if (type.equals("ban")) {
					bot.getDBUtil().accessGroups.setMaxBanDuration(group.groupId(), guildId, seconds);
				} else {
					bot.getDBUtil().accessGroups.setMaxMuteDuration(group.groupId(), guildId, seconds);
				}
			} catch (SQLException ex) {
				editErrorDatabase(event, ex, "set limit");
				return;
			}

			editEmbed(event, bot.getEmbedUtil().getEmbed(Constants.COLOR_SUCCESS)
				.setDescription(lu.getGuildText(event, path+".done", group.name(), type, display))
				.build()
			);
		}

		@Override
		public void onAutoComplete(CommandAutoCompleteInteractionEvent event) {
			replyGroupAutocomplete(event);
		}
	}

	// ---- /access group info ----

	private class GroupInfo extends SlashCommand {
		public GroupInfo() {
			this.name = "info";
			this.path = "bot.guild.access.group.info";
			this.options = List.of(
				new OptionData(OptionType.STRING, "group", lu.getText(path+".group.help"), true, true)
			);
			this.subcommandGroup = new SubcommandGroupData("group", lu.getText("bot.guild.access.group.help"));
			this.ephemeral = true;
		}

		@Override
		protected void execute(SlashCommandEvent event) {
			Guild guild = event.getGuild();
			assert guild != null;

			GroupData group = resolveGroup(event, guild.getIdLong());
			if (group == null) return;

			List<Long> roleIds = bot.getDBUtil().accessGroups.getRolesForGroup(group.groupId());
			List<Long> userIds = bot.getDBUtil().accessGroups.getUsersForGroup(group.groupId());

			StringBuilder sb = new StringBuilder();

			// Permissions
			sb.append("**").append(lu.getGuildText(event, path+".permissions")).append("**\n");
			for (AccessPermission perm : AccessPermission.values()) {
				if ((group.permissions() & perm.toBit()) != 0L) {
					sb.append("> `").append(perm.name().toLowerCase()).append("`\n");
				}
			}

			// Limits
			sb.append("\n**").append(lu.getGuildText(event, path+".limits")).append("**\n");
			sb.append("> Ban: ").append(group.maxBanSeconds() == null
				? lu.getGuildText(event, path+".unlimited")
				: TimeUtil.durationToString(java.time.Duration.ofSeconds(group.maxBanSeconds()))).append("\n");
			sb.append("> Mute: ").append(group.maxMuteSeconds() == null
				? lu.getGuildText(event, path+".unlimited")
				: TimeUtil.durationToString(java.time.Duration.ofSeconds(group.maxMuteSeconds()))).append("\n");

			// Roles
			sb.append("\n**").append(lu.getGuildText(event, path+".roles")).append("**\n");
			if (roleIds.isEmpty()) {
				sb.append("> ").append(lu.getGuildText(event, path+".none")).append("\n");
			} else {
				for (Long roleId : roleIds) {
					Role role = guild.getRoleById(roleId);
					if (role == null) {
						ignoreExc(() -> bot.getDBUtil().accessGroups.removeRole(group.groupId(), guild.getIdLong(), roleId));
					} else {
						sb.append("> ").append(role.getAsMention()).append(" `").append(roleId).append("`\n");
					}
				}
			}

			// Users
			sb.append("\n**").append(lu.getGuildText(event, path+".users")).append("**\n");
			if (userIds.isEmpty()) {
				sb.append("> ").append(lu.getGuildText(event, path+".none")).append("\n");
			} else {
				for (Long userId : userIds) {
					sb.append("> ").append(User.fromId(userId).getAsMention()).append(" `").append(userId).append("`\n");
				}
			}

			editEmbed(event, bot.getEmbedUtil().getEmbed()
				.setTitle(lu.getGuildText(event, path+".title", group.name()))
				.setDescription(sb.toString())
				.build()
			);
		}

		@Override
		public void onAutoComplete(CommandAutoCompleteInteractionEvent event) {
			replyGroupAutocomplete(event);
		}
	}

	// ---- /access group list ----

	private class GroupList extends SlashCommand {
		public GroupList() {
			this.name = "list";
			this.path = "bot.guild.access.group.list";
			this.subcommandGroup = new SubcommandGroupData("group", lu.getText("bot.guild.access.group.help"));
			this.ephemeral = true;
		}

		@Override
		protected void execute(SlashCommandEvent event) {
			Guild guild = event.getGuild();
			assert guild != null;

			List<GroupData> groups = bot.getDBUtil().accessGroups.getGroupsForGuild(guild.getIdLong());
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
			for (GroupData g : groups) {
				int roleCount = bot.getDBUtil().accessGroups.getRolesForGroup(g.groupId()).size();
				int userCount = bot.getDBUtil().accessGroups.getUsersForGroup(g.groupId()).size();
				long permCount = Long.bitCount(g.permissions());
				sb.append("**").append(g.name()).append("**")
					.append(" — ").append(permCount).append(" perm(s)")
					.append(", ").append(roleCount).append(" role(s)")
					.append(", ").append(userCount).append(" user(s)\n");
			}
			editEmbed(event, eb.setDescription(sb.toString()).build());
		}
	}

	// ---- /access member addrole ----

	private class MemberAddRole extends SlashCommand {
		public MemberAddRole() {
			this.name = "addrole";
			this.path = "bot.guild.access.member.addrole";
			this.options = List.of(
				new OptionData(OptionType.STRING, "group", lu.getText(path+".group.help"), true, true),
				new OptionData(OptionType.ROLE, "role", lu.getText(path+".role.help"), true)
			);
			this.subcommandGroup = new SubcommandGroupData("member", lu.getText("bot.guild.access.member.help"));
		}

		@Override
		protected void execute(SlashCommandEvent event) {
			Guild guild = event.getGuild();
			assert guild != null;
			long guildId = guild.getIdLong();

			GroupData group = resolveGroup(event, guildId);
			if (group == null) return;

			Role role = event.optRole("role");
			if (role == null) {
				editError(event, "errors.option.role");
				return;
			}
			if (role.isPublicRole() || role.isManaged()) {
				editError(event, "errors.option.role_interact");
				return;
			}
			if (bot.getDBUtil().accessGroups.isRoleInGroup(group.groupId(), role.getIdLong())) {
				editError(event, path+".already");
				return;
			}
			int total = bot.getDBUtil().accessGroups.getRolesForGroup(group.groupId()).size()
				+ bot.getDBUtil().accessGroups.getUsersForGroup(group.groupId()).size();
			if (total >= Limits.ACCESS_GROUP_MEMBERS) {
				editErrorLimit(event, "group members", Limits.ACCESS_GROUP_MEMBERS);
				return;
			}

			try {
				bot.getDBUtil().accessGroups.addRole(group.groupId(), guildId, role.getIdLong());
			} catch (SQLException ex) {
				editErrorDatabase(event, ex, "add role to group");
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

	// ---- /access member removerole ----

	private class MemberRemoveRole extends SlashCommand {
		public MemberRemoveRole() {
			this.name = "removerole";
			this.path = "bot.guild.access.member.removerole";
			this.options = List.of(
				new OptionData(OptionType.STRING, "group", lu.getText(path+".group.help"), true, true),
				new OptionData(OptionType.ROLE, "role", lu.getText(path+".role.help"), true)
			);
			this.subcommandGroup = new SubcommandGroupData("member", lu.getText("bot.guild.access.member.help"));
		}

		@Override
		protected void execute(SlashCommandEvent event) {
			Guild guild = event.getGuild();
			assert guild != null;
			long guildId = guild.getIdLong();

			GroupData group = resolveGroup(event, guildId);
			if (group == null) return;

			Role role = event.optRole("role");
			if (role == null) {
				editError(event, "errors.option.role");
				return;
			}
			if (!bot.getDBUtil().accessGroups.isRoleInGroup(group.groupId(), role.getIdLong())) {
				editError(event, path+".not_in_group");
				return;
			}

			try {
				bot.getDBUtil().accessGroups.removeRole(group.groupId(), guildId, role.getIdLong());
			} catch (SQLException ex) {
				editErrorDatabase(event, ex, "remove role from group");
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

	// ---- /access member adduser ----

	private class MemberAddUser extends SlashCommand {
		public MemberAddUser() {
			this.name = "adduser";
			this.path = "bot.guild.access.member.adduser";
			this.options = List.of(
				new OptionData(OptionType.STRING, "group", lu.getText(path+".group.help"), true, true),
				new OptionData(OptionType.USER, "user", lu.getText(path+".user.help"), true)
			);
			this.subcommandGroup = new SubcommandGroupData("member", lu.getText("bot.guild.access.member.help"));
		}

		@Override
		protected void execute(SlashCommandEvent event) {
			Guild guild = event.getGuild();
			assert guild != null;
			long guildId = guild.getIdLong();

			GroupData group = resolveGroup(event, guildId);
			if (group == null) return;

			Member member = event.optMember("user");
			if (member == null) {
				editError(event, "errors.option.member");
				return;
			}
			if (member.isOwner() || member.getUser().isBot()) {
				editError(event, "errors.option.member_interact");
				return;
			}
			if (bot.getDBUtil().accessGroups.isUserInGroup(group.groupId(), member.getIdLong())) {
				editError(event, path+".already");
				return;
			}
			int total = bot.getDBUtil().accessGroups.getRolesForGroup(group.groupId()).size()
				+ bot.getDBUtil().accessGroups.getUsersForGroup(group.groupId()).size();
			if (total >= Limits.ACCESS_GROUP_MEMBERS) {
				editErrorLimit(event, "group members", Limits.ACCESS_GROUP_MEMBERS);
				return;
			}

			try {
				bot.getDBUtil().accessGroups.addUser(group.groupId(), guildId, member.getIdLong());
			} catch (SQLException ex) {
				editErrorDatabase(event, ex, "add user to group");
				return;
			}

			editEmbed(event, bot.getEmbedUtil().getEmbed(Constants.COLOR_SUCCESS)
				.setDescription(lu.getGuildText(event, path+".done", member.getAsMention(), group.name()))
				.build()
			);
		}

		@Override
		public void onAutoComplete(CommandAutoCompleteInteractionEvent event) {
			replyGroupAutocomplete(event);
		}
	}

	// ---- /access member removeuser ----

	private class MemberRemoveUser extends SlashCommand {
		public MemberRemoveUser() {
			this.name = "removeuser";
			this.path = "bot.guild.access.member.removeuser";
			this.options = List.of(
				new OptionData(OptionType.STRING, "group", lu.getText(path+".group.help"), true, true),
				new OptionData(OptionType.USER, "user", lu.getText(path+".user.help"), true)
			);
			this.subcommandGroup = new SubcommandGroupData("member", lu.getText("bot.guild.access.member.help"));
		}

		@Override
		protected void execute(SlashCommandEvent event) {
			Guild guild = event.getGuild();
			assert guild != null;
			long guildId = guild.getIdLong();

			GroupData group = resolveGroup(event, guildId);
			if (group == null) return;

			User user = event.optUser("user");
			if (user == null) {
				editError(event, "errors.option.user");
				return;
			}
			if (!bot.getDBUtil().accessGroups.isUserInGroup(group.groupId(), user.getIdLong())) {
				editError(event, path+".not_in_group");
				return;
			}

			try {
				bot.getDBUtil().accessGroups.removeUser(group.groupId(), guildId, user.getIdLong());
			} catch (SQLException ex) {
				editErrorDatabase(event, ex, "remove user from group");
				return;
			}

			editEmbed(event, bot.getEmbedUtil().getEmbed(Constants.COLOR_SUCCESS)
				.setDescription(lu.getGuildText(event, path+".done", user.getAsMention(), group.name()))
				.build()
			);
		}

		@Override
		public void onAutoComplete(CommandAutoCompleteInteractionEvent event) {
			replyGroupAutocomplete(event);
		}
	}

	// ---- Shared helpers ----

	private GroupData resolveGroup(SlashCommandEvent event, long guildId) {
		String name = event.optString("group", "");
		GroupData group = bot.getDBUtil().accessGroups.getGroup(guildId, name);
		if (group == null) {
			editError(event, "bot.guild.access.group.not_found");
		}
		return group;
	}

	private void replyGroupAutocomplete(CommandAutoCompleteInteractionEvent event) {
		if (event.getGuild() == null) return;
		String query = event.getFocusedOption().getValue().toLowerCase();
		List<Command.Choice> choices = bot.getDBUtil().accessGroups
			.getGroupsForGuild(event.getGuild().getIdLong())
			.stream()
			.filter(g -> g.name().toLowerCase().contains(query))
			.limit(25)
			.map(g -> new Command.Choice(g.name(), g.name()))
			.collect(Collectors.toList());
		event.replyChoices(choices).queue();
	}
}
