package dev.fileeditor.votl.commands.ticketing;

import static dev.fileeditor.votl.utils.CastUtil.castLong;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import dev.fileeditor.votl.base.command.SlashCommand;
import dev.fileeditor.votl.base.command.SlashCommandEvent;
import dev.fileeditor.votl.commands.CommandBase;
import dev.fileeditor.votl.objects.CmdAccessLevel;
import dev.fileeditor.votl.objects.CmdModule;
import dev.fileeditor.votl.objects.RoleType;
import dev.fileeditor.votl.objects.constants.CmdCategory;
import dev.fileeditor.votl.objects.constants.Constants;

import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Role;
import net.dv8tion.jda.api.entities.MessageEmbed.Field;
import net.dv8tion.jda.api.interactions.commands.Command.Choice;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;

public class RolesManageCmd extends CommandBase {
	
	public RolesManageCmd() {
		this.name = "rolesmanage";
		this.path = "bot.ticketing.rolesmanage";
		this.children = new SlashCommand[]{new Add(), new Update(), new Remove(), new View()};
		this.module = CmdModule.TICKETING;
		this.category = CmdCategory.TICKETING;
		this.accessLevel = CmdAccessLevel.ADMIN;
	}

	@Override
	protected void execute(SlashCommandEvent event) {}

	private class Add extends SlashCommand {

		public Add() {
			this.name = "add";
			this.path = "bot.ticketing.rolesmanage.add";
			this.options = List.of(
				new OptionData(OptionType.ROLE, "role", lu.getText(path+".role.help"), true),
				new OptionData(OptionType.STRING, "type", lu.getText(path+".type.help"), true)
					.addChoices(List.of(
						new Choice(lu.getText(RoleType.ASSIGN.getPath()), RoleType.ASSIGN.toString()),
						new Choice(lu.getText(RoleType.TOGGLE.getPath()), RoleType.TOGGLE.toString()),
						new Choice(lu.getText(RoleType.CUSTOM.getPath()), RoleType.CUSTOM.toString())
					)),
				new OptionData(OptionType.STRING, "description", lu.getText(path+".description.help"))
					.setMaxLength(80),
				new OptionData(OptionType.INTEGER, "row", lu.getText(path+".row.help"))
					.addChoices(List.of(
						new Choice("1", 1),
						new Choice("2", 2),
						new Choice("3", 3)
					)),
				new OptionData(OptionType.BOOLEAN, "timed", lu.getText(path+".timed.help"))
			);
		}

		@Override
		protected void execute(SlashCommandEvent event) {
			event.deferReply(true).queue();
			long guildId = event.getGuild().getIdLong();
			
			Role role = event.optRole("role");
			if (role == null || role.hasPermission(Permission.ADMINISTRATOR, Permission.MANAGE_ROLES, Permission.MANAGE_SERVER)) {
				editError(event, path+".no_role");
				return;
			}
			if (!event.getGuild().getSelfMember().canInteract(role)) {
				editError(event, path+".cant_interact");
				return;
			}
			long roleId = role.getIdLong();
			if (bot.getDBUtil().roles.existsRole(roleId)) {
				editError(event, path+".exists");
				return;
			}
			
			String type = event.optString("type");
			if (type.equals(RoleType.ASSIGN.toString())) {
				int row = event.optInteger("row", 0);
				if (row == 0) {
					for (int i = 1; i <= 3; i++) {
						if (bot.getDBUtil().roles.getRowSize(guildId, i) < 25) {
							row = i;
							break;
						}
					}
					if (row == 0) {
						editError(event, path+".rows_max");
						return;
					}
				} else {
					if (bot.getDBUtil().roles.getRowSize(guildId, row) >= 25) {
						editError(event, path+".row_max", "Row: %s".formatted(row));
						return;
					}
				}
				boolean timed = event.optBoolean("timed", false);
				bot.getDBUtil().roles.add(guildId, roleId, event.optString("description", "NULL"), row, RoleType.ASSIGN, timed);
				sendSuccess(event, type, role);

			} else if (type.equals(RoleType.TOGGLE.toString())) {
				if (bot.getDBUtil().roles.getToggleable(guildId).size() >= 5) {
					editError(event, path+".toggle_max");
					return;
				}
				String description = event.optString("description", role.getName());
				bot.getDBUtil().roles.add(guildId, roleId, description, null, RoleType.TOGGLE, false);
				sendSuccess(event, type, role);
			} else if (type.equals(RoleType.CUSTOM.toString())) {
				if (bot.getDBUtil().roles.getCustom(guildId).size() >= 25) {
					editError(event, path+".custom_max");
					return;
				}
				bot.getDBUtil().roles.add(guildId, roleId, event.optString("description", "NULL"), null, RoleType.CUSTOM, false);
				sendSuccess(event, type, role);
			} else {
				editError(event, path+".no_type");
			}
		}

		private void sendSuccess(SlashCommandEvent event, String type, Role role) {
			editHookEmbed(event, bot.getEmbedUtil().getEmbed(Constants.COLOR_SUCCESS)
				.setDescription(lu.getText(event, path+".done").replace("{role}", role.getAsMention()).replace("{type}", type))
				.build());
		}

	}

	private class Update extends SlashCommand {

		public Update() {
			this.name = "update";
			this.path = "bot.ticketing.rolesmanage.update";
			this.options = List.of(
				new OptionData(OptionType.ROLE, "role", lu.getText(path+".role.help"), true),
				new OptionData(OptionType.STRING, "description", lu.getText(path+".description.help"))
					.setMaxLength(80),
				new OptionData(OptionType.INTEGER, "row", lu.getText(path+".row.help"))
					.addChoices(List.of(
						new Choice("1", 1),
						new Choice("2", 2),
						new Choice("3", 3)
					)),
				new OptionData(OptionType.BOOLEAN, "timed", lu.getText(path+".timed.help"))
			);
		}

		@Override
		protected void execute(SlashCommandEvent event) {
			Role role = event.optRole("role");
			if (role == null) {
				createError(event, path+".no_role");
				return;
			}
			long roleId = role.getIdLong();
			if (!bot.getDBUtil().roles.existsRole(roleId)) {
				createError(event, path+".not_exists");
				return;
			}
			
			event.deferReply(true).queue();
			StringBuffer response = new StringBuffer();

			if (event.hasOption("description")) {
				String description = event.optString("description");
				if (description.equalsIgnoreCase("null")) description = null;

				if (bot.getDBUtil().roles.isToggleable(roleId)) {
					if (description == null) {
						description = role.getName();
						response.append(lu.getText(event, path+".default_description"));
					} else {
						response.append(lu.getText(event, path+".changed_description").replace("{text}", description));
					}
				} else {
					if (description == null) {
						description = "NULL";
						response.append(lu.getText(event, path+".default_description"));
					} else {
						response.append(lu.getText(event, path+".changed_description").replace("{text}", description));
					}
				}
				bot.getDBUtil().roles.setDescription(roleId, description);
			}

			if (event.hasOption("row")) {
				Integer row = event.optInteger("row");
				bot.getDBUtil().roles.setRow(roleId, row);
				response.append(lu.getText(event, path+".changed_row").replace("{row}", row.toString()));
			}

			if (event.hasOption("timed")) {
				boolean timed = event.optBoolean("timed", false);
				bot.getDBUtil().roles.setTimed(roleId, timed);
				response.append(lu.getText(event, path+".changed_timed").replace("{is}", timed ? Constants.SUCCESS : Constants.FAILURE));
			}

			sendReply(event, response, role);
		}

		private void sendReply(SlashCommandEvent event, StringBuffer response, Role role) {
			if (response.isEmpty()) {
				editError(event, path+".no_options");
				return;
			}
			editHookEmbed(event, bot.getEmbedUtil().getEmbed(Constants.COLOR_SUCCESS)
				.setDescription(lu.getText(event, path+".embed_title").replace("{role}", role.getAsMention()))
				.appendDescription(response.toString())
				.build());
		}

	}

	private class Remove extends SlashCommand {

		public Remove() {
			this.name = "remove";
			this.path = "bot.ticketing.rolesmanage.remove";
			this.options = List.of(
				new OptionData(OptionType.STRING, "id", lu.getText(path+".id.help"), true)
			);
		}

		@Override
		protected void execute(SlashCommandEvent event) {
			Long roleId = castLong(event.optString("id"));
			if (!bot.getDBUtil().roles.existsRole(roleId)) {
				createError(event, path+".no_role");
				return;
			}
			bot.getDBUtil().roles.remove(roleId);
			createReplyEmbed(event, bot.getEmbedUtil().getEmbed(Constants.COLOR_SUCCESS)
				.setDescription(lu.getText(event, path+".done").replace("{id}", String.valueOf(roleId)))
				.build());
		}
		
	}

	private class View extends SlashCommand {

		public View() {
			this.name = "view";
			this.path = "bot.ticketing.rolesmanage.view";
		}

		@Override
		protected void execute(SlashCommandEvent event) {
			event.deferReply(true).queue();
			Guild guild = event.getGuild();
			long guildId = guild.getIdLong();
			EmbedBuilder builder = bot.getEmbedUtil().getEmbed()
				.setTitle(lu.getText(event, path+".title"));
			
			for (RoleType type : RoleType.values()) {
				if (type.equals(RoleType.ASSIGN)) {
					for (int row = 1; row <= 3; row++) {
						List<Map<String, Object>> roles = bot.getDBUtil().roles.getAssignableByRow(guildId, row);
						String title = "%s-%s | %s".formatted(lu.getText(event, type.getPath()), row, bot.getDBUtil().getTicketSettings(guild).getRowText(row));
						if (roles.isEmpty()) {
							builder.addField(title, lu.getText(event, path+".none"), false);
						} else {
							generateField(guild, title, roles).forEach(builder::addField);
						}
					}
				} else {
					List<Map<String, Object>> roles = bot.getDBUtil().roles.getRolesByType(guildId, type);
					String title = lu.getText(event, type.getPath());
					if (roles.isEmpty()) {
						builder.addField(title, lu.getText(event, path+".none"), false);
					} else {
						generateField(guild, title, roles).forEach(builder::addField);
					}
				}
			}

			event.getHook().editOriginalEmbeds(builder.build()).queue();
		}

	}
	
	private List<Field> generateField(final Guild guild, final String title, final List<Map<String, Object>> roles) {
		List<Field> fields = new ArrayList<>();
		StringBuffer buffer = new StringBuffer();
		roles.forEach(data -> {
			Long roleId = castLong(data.get("roleId"));
			Role role = guild.getRoleById(roleId);
			if (role == null) {
				bot.getDBUtil().roles.remove(roleId);
				return;
			}
			boolean timed = Optional.ofNullable(data.get("timed")).map(o -> ((Integer) o) == 1).orElse(false);
			buffer.append(String.format("%s%s `%s` | %s\n", timed ? "**Timed**" : "", role.getAsMention(), roleId, data.get("description")));
			if (buffer.length() > 900) {
				fields.add(new Field((fields.isEmpty() ? title : ""), buffer.toString(), false));
				buffer.setLength(0);
			}
		});
		if (!buffer.isEmpty()) {
			fields.add(new Field((fields.isEmpty() ? title : ""), buffer.toString(), false));
		}
		return fields;
	}

}
