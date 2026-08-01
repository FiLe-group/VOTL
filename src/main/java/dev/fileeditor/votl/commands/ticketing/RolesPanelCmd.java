package dev.fileeditor.votl.commands.ticketing;

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

import dev.fileeditor.votl.base.command.SlashCommand;
import dev.fileeditor.votl.base.command.SlashCommandEvent;
import dev.fileeditor.votl.objects.AccessPermission;
import dev.fileeditor.votl.objects.CmdModule;
import dev.fileeditor.votl.objects.RoleType;
import dev.fileeditor.votl.objects.constants.CmdCategory;
import dev.fileeditor.votl.objects.constants.Constants;
import dev.fileeditor.votl.utils.database.managers.RoleManager;
import dev.fileeditor.votl.utils.message.MessageUtil;

import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.components.actionrow.ActionRow;
import net.dv8tion.jda.api.components.buttons.Button;
import net.dv8tion.jda.api.components.container.Container;
import net.dv8tion.jda.api.components.container.ContainerChildComponent;
import net.dv8tion.jda.api.components.section.Section;
import net.dv8tion.jda.api.components.separator.Separator;
import net.dv8tion.jda.api.components.textdisplay.TextDisplay;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.entities.Role;
import net.dv8tion.jda.api.entities.channel.ChannelType;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import net.dv8tion.jda.api.entities.channel.middleman.GuildChannel;
import net.dv8tion.jda.api.entities.emoji.Emoji;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;

import javax.annotation.Nullable;

public class RolesPanelCmd extends SlashCommand {
	
	public RolesPanelCmd() {
		this.name = "rolespanel";
		this.path = "bot.ticketing.rolespanel";
		this.children = new SlashCommand[]{
			new Create(), new Update(), new RowText()
		};
		this.botPermissions = new Permission[]{Permission.MESSAGE_SEND};
		this.module = CmdModule.TICKETING;
		this.category = CmdCategory.TICKETING;
		this.requiredPermission = AccessPermission.ADMIN;
	}

	@Override
	protected void execute(SlashCommandEvent event) {}

	/**
	 * Builds the role requests panel as a Components V2 container.
	 *
	 * @param customRoles whether to append the custom (personal) roles section
	 * @return the container, or {@code null} if no requestable or toggleable roles are set
	 */
	@Nullable
	private Container buildPanel(SlashCommandEvent event, Guild guild, boolean customRoles) {
		long guildId = guild.getIdLong();

		int assignRolesSize = bot.getDBUtil().roles.countRoles(guildId, RoleType.ASSIGN);
		List<RoleManager.RoleData> toggleRoles = bot.getDBUtil().roles.getToggleable(guildId);
		if (assignRolesSize == 0 && toggleRoles.isEmpty()) return null;

		List<ContainerChildComponent> children = new ArrayList<>();
		children.add(TextDisplay.of("## "+lu.getGuildText(event, "bot.ticketing.embeds.role_title")));

		if (assignRolesSize > 0) {
			children.add(Section.of(
				Button.success("role:start_request", lu.getGuildText(event, "bot.ticketing.embeds.button_request")),
				TextDisplay.of(lu.getGuildText(event, "bot.ticketing.embeds.role_request"))
			));
			children.add(Separator.createDivider(Separator.Spacing.SMALL));
		}
		children.add(Section.of(
			Button.danger("role:remove", lu.getGuildText(event, "bot.ticketing.embeds.button_remove")),
			TextDisplay.of(lu.getGuildText(event, "bot.ticketing.embeds.role_remove"))
		));

		if (!toggleRoles.isEmpty()) {
			List<Button> buttons = new ArrayList<>();
			toggleRoles.forEach(data -> {
				if (buttons.size() >= 5) return;
				Role role = guild.getRoleById(data.getIdLong());
				if (role == null) return;
				buttons.add(Button.primary("role:toggle:"+role.getId(), MessageUtil.limitString(data.getDescription("-"), 80)));
			});
			if (!buttons.isEmpty()) {
				children.add(Separator.createDivider(Separator.Spacing.SMALL));
				children.add(TextDisplay.of(lu.getGuildText(event, "bot.ticketing.embeds.role_toggle")));
				children.add(ActionRow.of(buttons));
			}
		}

		if (customRoles) {
			children.add(Separator.createDivider(Separator.Spacing.LARGE));
			children.add(TextDisplay.of(lu.getGuildText(event, "bot.ticketing.embeds.role_custom")));
			children.add(ActionRow.of(
				Button.secondary("custom_role:create", lu.getGuildText(event, "bot.roles.custom_role.create_panel.button_text"))
					.withEmoji(Emoji.fromUnicode("👤")),
				Button.secondary("custom_role:edit", lu.getGuildText(event, "bot.roles.custom_role.create_panel.edit_button_text"))
					.withEmoji(Emoji.fromUnicode("✏️"))
			));
		}

		return Container.of(children)
			.withAccentColor(bot.getDBUtil().getGuildSettings(guild).getColor());
	}

	private class Create extends SlashCommand {
		public Create() {
			this.name = "create";
			this.path = "bot.ticketing.rolespanel.create";
			this.options = List.of(
				new OptionData(OptionType.CHANNEL, "channel", lu.getText(path+".channel.help"), true)
					.setChannelTypes(ChannelType.TEXT),
				new OptionData(OptionType.BOOLEAN, "custom_roles", lu.getText(path+".custom_roles.help"))
			);
		}

		@Override
		protected void execute(SlashCommandEvent event) {
			Guild guild = event.getGuild();
			assert guild != null;

			TextChannel channel = (TextChannel) event.optGuildChannel("channel");
			if (channel == null) {
				editError(event, "errors.option.channel", "Received: No channel");
				return;
			}

			boolean customRoles = event.optBoolean("custom_roles");
			if (customRoles && !bot.getDBUtil().customRoleSettings.getSettings(guild.getIdLong()).isConfigured()) {
				editError(event, "bot.roles.custom_role.errors.no_setup");
				return;
			}

			Container container = buildPanel(event, guild, customRoles);
			if (container == null) {
				editError(event, path+".empty_roles");
				return;
			}

			channel.sendMessageComponents(container).useComponentsV2().queue(_ ->
				editEmbed(event, bot.getEmbedUtil()
					.getEmbed(Constants.COLOR_SUCCESS)
					.setDescription(lu.getGuildText(event, path+".done", channel.getAsMention()))
					.build()
				)
			);
		}
	}

	private class Update extends SlashCommand {
		public Update() {
			this.name = "update";
			this.path = "bot.ticketing.rolespanel.update";
			this.options = List.of(
				new OptionData(OptionType.CHANNEL, "channel", lu.getText(path+".channel.help"), true)
					.setChannelTypes(ChannelType.TEXT),
				new OptionData(OptionType.BOOLEAN, "custom_roles", lu.getText(path+".custom_roles.help"))
			);
		}

		@Override
		protected void execute(SlashCommandEvent event) {
			Guild guild = event.getGuild();
			assert guild != null;

			GuildChannel channel = event.optGuildChannel("channel");
			if (channel == null) {
				editError(event, "errors.option.channel", "Received: No channel");
				return;
			}
			TextChannel tc = (TextChannel) channel;

			boolean customRoles = event.optBoolean("custom_roles");
			if (customRoles && !bot.getDBUtil().customRoleSettings.getSettings(guild.getIdLong()).isConfigured()) {
				editError(event, "bot.roles.custom_role.errors.no_setup");
				return;
			}

			tc.getIterableHistory()
				.takeAsync(5)
				.thenAccept(messages -> {
					Message message = messages.stream()
						.filter(msg -> msg.getAuthor().getIdLong() == event.getJDA().getSelfUser().getIdLong())
						.findFirst()
						.orElse(null);

					if (message == null) {
						editError(event, path+".not_found", "No bot's message found");
						return;
					}

					Container container = buildPanel(event, guild, customRoles);
					if (container == null) {
						editError(event, path+".empty_roles");
						return;
					}

					// Replace clears the legacy embed, as Components V2 messages can't have one
					message.editMessageComponents(container)
						.useComponentsV2()
						.setReplace(true)
						.queue(done ->
							editEmbed(event, bot.getEmbedUtil()
								.getEmbed(Constants.COLOR_SUCCESS)
								.setDescription(lu.getGuildText(event, path+".done", done.getJumpUrl()))
								.build()
							),
							failure -> editErrorOther(event, "Failed to update message.\n"+failure.getMessage())
						);
				});
		}
	}

	private class RowText extends SlashCommand {
		public RowText() {
			this.name = "row";
			this.path = "bot.ticketing.rolespanel.row";
			this.options = List.of(
				new OptionData(OptionType.INTEGER, "row", lu.getText(path+".row.help"), true)
					.setRequiredRange(1, 3),
				new OptionData(OptionType.STRING, "text", lu.getText(path+".text.help"), true)
					.setMaxLength(100)
			);
		}

		@Override
		protected void execute(SlashCommandEvent event) {
			Integer row = event.optInteger("row");
			String text = event.optString("text");

			assert event.getGuild() != null;
			try {
				bot.getDBUtil().ticketSettings.setRowText(event.getGuild().getIdLong(), row, text);
			} catch (SQLException ex) {
				editErrorDatabase(event, ex, "set ticket row text");
				return;
			}

			editEmbed(event, bot.getEmbedUtil().getEmbed(Constants.COLOR_SUCCESS)
				.setDescription(lu.getGuildText(event, path+".done", row, text))
				.build());
		}
	}

}
