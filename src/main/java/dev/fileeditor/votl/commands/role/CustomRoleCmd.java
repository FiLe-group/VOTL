package dev.fileeditor.votl.commands.role;

import dev.fileeditor.votl.base.command.SlashCommand;
import dev.fileeditor.votl.base.command.SlashCommandEvent;
import dev.fileeditor.votl.objects.AccessPermission;
import dev.fileeditor.votl.objects.CmdModule;
import dev.fileeditor.votl.objects.constants.CmdCategory;
import dev.fileeditor.votl.objects.constants.Constants;
import dev.fileeditor.votl.utils.database.managers.CustomRoleAccessManager.AccessRecord;
import dev.fileeditor.votl.utils.exception.FormatterException;
import dev.fileeditor.votl.utils.message.TimeUtil;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.components.actionrow.ActionRow;
import net.dv8tion.jda.api.components.buttons.Button;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.MessageEmbed;
import net.dv8tion.jda.api.entities.Role;
import net.dv8tion.jda.api.entities.channel.ChannelType;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import net.dv8tion.jda.api.entities.emoji.Emoji;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import net.dv8tion.jda.api.utils.TimeFormat;
import net.dv8tion.jda.api.utils.messages.MessageCreateBuilder;
import org.jetbrains.annotations.Nullable;

import java.sql.SQLException;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

public class CustomRoleCmd extends SlashCommand {

	public CustomRoleCmd() {
		this.name = "custom_role";
		this.path = "bot.roles.custom_role";
		this.children = new SlashCommand[]{
			new SetupChannel(), new SetupReviewer(), new SetupPosition(),
			new NitroBoost(), new CreatePanel(),
			new Grant(), new Revoke(), new AccessList(), new DeleteRole(),
			new Request(), new Icon(), new View()
		};
		this.category = CmdCategory.ROLES;
		this.module = CmdModule.ROLES;
	}

	@Override
	protected void execute(SlashCommandEvent event) {}

	// ---- ADMIN: Setup channel ----

	private class SetupChannel extends SlashCommand {
		public SetupChannel() {
			this.name = "setup_channel";
			this.path = "bot.roles.custom_role.setup_channel";
			this.options = List.of(
				new OptionData(OptionType.CHANNEL, "channel", lu.getText(path+".channel.help"), true)
					.setChannelTypes(ChannelType.TEXT)
			);
			this.requiredPermission = AccessPermission.ADMIN;
		}

		@Override
		public void execute(SlashCommandEvent event) {
			Guild guild = event.getGuild();
			assert guild != null;

			TextChannel channel = event.optGuildChannel("channel") instanceof TextChannel tc ? tc : null;
			if (channel == null) {
				editError(event, "errors.option.channel");
				return;
			}
			try {
				bot.getDBUtil().customRoleSettings.setRequestsChannel(guild.getIdLong(), channel.getIdLong());
			} catch (SQLException ex) {
				editErrorDatabase(event, ex, "set requests channel");
				return;
			}
			editEmbed(event, bot.getEmbedUtil().getEmbed(Constants.COLOR_SUCCESS)
				.setDescription(lu.getText(event, path+".done").formatted(channel.getAsMention()))
				.build());
		}
	}

	// ---- ADMIN: Setup reviewer role ----

	private class SetupReviewer extends SlashCommand {
		public SetupReviewer() {
			this.name = "setup_reviewer";
			this.path = "bot.roles.custom_role.setup_reviewer";
			this.options = List.of(
				new OptionData(OptionType.ROLE, "role", lu.getText(path+".role.help"), true)
			);
			this.requiredPermission = AccessPermission.ADMIN;
		}

		@Override
		public void execute(SlashCommandEvent event) {
			Guild guild = event.getGuild();
			assert guild != null;

			Role role = event.optRole("role");
			if (role == null) {
				editError(event, "errors.option.role");
				return;
			}
			try {
				bot.getDBUtil().customRoleSettings.setReviewerRole(guild.getIdLong(), role.getIdLong());
			} catch (SQLException ex) {
				editErrorDatabase(event, ex, "set reviewer role");
				return;
			}
			editEmbed(event, bot.getEmbedUtil().getEmbed(Constants.COLOR_SUCCESS)
				.setDescription(lu.getText(event, path+".done").formatted(role.getAsMention()))
				.build());
		}
	}

	// ---- ADMIN: Setup position role ----

	private class SetupPosition extends SlashCommand {
		public SetupPosition() {
			this.name = "setup_position";
			this.path = "bot.roles.custom_role.setup_position";
			this.options = List.of(
				new OptionData(OptionType.ROLE, "role", lu.getText(path+".role.help"))
			);
			this.requiredPermission = AccessPermission.ADMIN;
		}

		@Override
		public void execute(SlashCommandEvent event) {
			Guild guild = event.getGuild();
			assert guild != null;

			Role role = event.optRole("role");
			try {
				bot.getDBUtil().customRoleSettings.setPositionRole(guild.getIdLong(), role == null ? null : role.getIdLong());
			} catch (SQLException ex) {
				editErrorDatabase(event, ex, "set position role");
				return;
			}
			String description = role == null
				? lu.getText(event, path+".cleared")
				: lu.getText(event, path+".done").formatted(role.getAsMention());
			editEmbed(event, bot.getEmbedUtil().getEmbed(Constants.COLOR_SUCCESS)
				.setDescription(description)
				.build());
		}
	}

	// ---- ADMIN: Nitro boost settings ----

	private class NitroBoost extends SlashCommand {
		public NitroBoost() {
			this.name = "nitro_boost";
			this.path = "bot.roles.custom_role.nitro_boost";
			this.options = List.of(
				new OptionData(OptionType.BOOLEAN, "enabled", lu.getText(path+".enabled.help")),
				new OptionData(OptionType.INTEGER, "expire_after", lu.getText(path+".expire_after.help"))
					.setRequiredRange(1, 30),
				new OptionData(OptionType.INTEGER, "renew_every", lu.getText(path+".renew_every.help"))
					.setRequiredRange(1, 29)
			);
			this.requiredPermission = AccessPermission.ADMIN;
		}

		@Override
		protected void execute(SlashCommandEvent event) {
			Guild guild = event.getGuild();
			assert guild != null;
			long guildId = guild.getIdLong();

			if (event.getOptions().isEmpty()) {
				var settings = bot.getDBUtil().customRoleSettings.getSettings(guildId);
				editEmbed(event, bot.getEmbedUtil().getEmbed(event)
					.setTitle(lu.getText(event, path+".view.title"))
					.addField(lu.getText(event, path+".view.enabled"), settings.isNitroAutoGrant() ? Constants.SUCCESS : Constants.FAILURE, true)
					.addField(lu.getText(event, path+".view.expire_after"), settings.getNitroExpireDays()+"d", true)
					.addField(lu.getText(event, path+".view.renew_every"), settings.getNitroRenewDays()+"d", true)
					.build());
				return;
			}

			try {
				boolean enabled = event.optBoolean("enabled");
				if (event.hasOption("enabled")) bot.getDBUtil().customRoleSettings.setNitroAutoGrant(guildId, enabled);

				Integer expireAfter = event.optInteger("expire_after");
				if (expireAfter != null) bot.getDBUtil().customRoleSettings.setNitroExpireDays(guildId, expireAfter);

				Integer renewEvery = event.optInteger("renew_every");
				if (renewEvery != null) bot.getDBUtil().customRoleSettings.setNitroRenewDays(guildId, renewEvery);
			} catch (SQLException ex) {
				editErrorDatabase(event, ex, "set nitro settings");
				return;
			}
			editEmbed(event, bot.getEmbedUtil().getEmbed(Constants.COLOR_SUCCESS)
				.setDescription(lu.getText(event, path+".done"))
				.build());
		}
	}

	// ---- ADMIN: Create request panel ----

	private class CreatePanel extends SlashCommand {
		public CreatePanel() {
			this.name = "create_panel";
			this.path = "bot.roles.custom_role.create_panel";
			this.options = List.of(
				new OptionData(OptionType.CHANNEL, "channel", lu.getText(path+".channel.help"), true)
					.setChannelTypes(ChannelType.TEXT)
			);
			this.requiredPermission = AccessPermission.ADMIN;
		}

		@Override
		public void execute(SlashCommandEvent event) {
			Guild guild = event.getGuild();
			assert guild != null;

			TextChannel channel = event.optGuildChannel("channel") instanceof TextChannel tc ? tc : null;
			if (channel == null) {
				editError(event, "errors.option.channel");
				return;
			}

			MessageEmbed embed = bot.getEmbedUtil().getEmbed()
				.setDescription(lu.getGuildText(event, path+".embed_text"))
				.build();
			Button button = Button.secondary("custom_role:create", lu.getGuildText(event, path+".button_text"))
				.withEmoji(Emoji.fromUnicode("👤"));

			channel.sendMessageEmbeds(embed).setComponents(ActionRow.of(button)).queue(
				_ -> editEmbed(event, bot.getEmbedUtil().getEmbed(Constants.COLOR_SUCCESS)
					.setDescription(lu.getText(event, path+".done").formatted(channel.getAsMention()))
					.build()),
				f -> editError(event, "errors.failed_send", f.getMessage())
			);
		}
	}

	// ---- ADMIN: Grant access ----

	private class Grant extends SlashCommand {
		public Grant() {
			this.name = "grant";
			this.path = "bot.roles.custom_role.grant";
			this.options = List.of(
				new OptionData(OptionType.USER, "user", lu.getText(path+".user.help"), true),
				new OptionData(OptionType.STRING, "duration", lu.getText(path+".duration.help"))
					.setMaxLength(16)
			);
			this.requiredPermission = AccessPermission.ADMIN;
		}

		@Override
		public void execute(SlashCommandEvent event) {
			Guild guild = event.getGuild();
			assert guild != null;

			Member target = event.optMember("user");
			if (target == null) {
				editError(event, "errors.option.member");
				return;
			}

			String durationStr = event.optString("duration");
			long expiresAt = 0;
			if (durationStr != null) {
				final Duration duration;
				try {
					duration = TimeUtil.stringToDuration(durationStr, false);
				} catch (FormatterException ex) {
					editError(event, ex.getPath());
					return;
				}
				if (duration.isZero() || duration.toDays() > 400) {
					editError(event, path+".time_limit");
					return;
				}
				expiresAt = Instant.now().plus(duration).getEpochSecond();
			}

			assert event.getMember() != null;
			try {
				bot.getDBUtil().customRoleAccess.grant(target.getIdLong(), guild.getIdLong(), event.getMember().getIdLong(), expiresAt, false);
			} catch (SQLException ex) {
				editErrorDatabase(event, ex, "grant custom role access");
				return;
			}

			String description = expiresAt == 0
				? lu.getText(event, path+".done_permanent").formatted(target.getAsMention())
				: lu.getText(event, path+".done_timed").formatted(target.getAsMention(), TimeFormat.DATE_TIME_SHORT.format(Instant.ofEpochSecond(expiresAt)));
			editEmbed(event, bot.getEmbedUtil().getEmbed(Constants.COLOR_SUCCESS)
				.setDescription(description)
				.build());
		}
	}

	// ---- ADMIN: Revoke access ----

	private class Revoke extends SlashCommand {
		public Revoke() {
			this.name = "revoke";
			this.path = "bot.roles.custom_role.revoke";
			this.options = List.of(
				new OptionData(OptionType.USER, "user", lu.getText(path+".user.help"), true)
			);
			this.requiredPermission = AccessPermission.ADMIN;
		}

		@Override
		public void execute(SlashCommandEvent event) {
			Guild guild = event.getGuild();
			assert guild != null;
			long guildId = guild.getIdLong();

			Member target = event.optMember("user");
			if (target == null) {
				editError(event, "errors.option.member");
				return;
			}
			long userId = target.getIdLong();

			try {
				bot.getDBUtil().customRoleAccess.revoke(userId, guildId);

				Long roleId = bot.getDBUtil().customRoles.getByOwner(userId, guildId);
				if (roleId != null) {
					Role role = guild.getRoleById(roleId);
					if (role != null) role.delete().reason("Custom role access revoked").queue();
					bot.getDBUtil().customRoles.remove(roleId);
				}
			} catch (SQLException ex) {
				editErrorDatabase(event, ex, "revoke custom role access");
				return;
			}
			editEmbed(event, bot.getEmbedUtil().getEmbed(Constants.COLOR_SUCCESS)
				.setDescription(lu.getText(event, path+".done").formatted(target.getAsMention()))
				.build());
		}
	}

	// ---- ADMIN: Access list ----

	private class AccessList extends SlashCommand {
		public AccessList() {
			this.name = "access_list";
			this.path = "bot.roles.custom_role.access_list";
			this.requiredPermission = AccessPermission.ADMIN;
			this.ephemeral = true;
		}

		@Override
		public void execute(SlashCommandEvent event) {
			Guild guild = event.getGuild();
			assert guild != null;

			List<AccessRecord> records = bot.getDBUtil().customRoleAccess.getGuildAccess(guild.getIdLong());
			if (records.isEmpty()) {
				editEmbed(event, bot.getEmbedUtil().getEmbed(event)
					.setDescription(lu.getText(event, path+".empty"))
					.build());
				return;
			}

			StringBuilder sb = new StringBuilder();
			for (AccessRecord r : records) {
				sb.append("<@").append(r.userId).append(">");
				if (r.isNitro) {
					sb.append(" *(nitro)*");
				} else if (r.grantedBy != 0) {
					sb.append(" *(granted by <@").append(r.grantedBy).append(">)*");
				}
				if (!r.isPermanent()) {
					sb.append(" — expires ").append(TimeFormat.DATE_TIME_SHORT.format(Instant.ofEpochSecond(r.expiresAt)));
				}
				sb.append("\n");
			}

			editEmbed(event, bot.getEmbedUtil().getEmbed(event)
				.setTitle(lu.getText(event, path+".title"))
				.setDescription(sb.toString())
				.build());
		}
	}

	// ---- ADMIN: Force-delete a user's custom role ----

	private class DeleteRole extends SlashCommand {
		public DeleteRole() {
			this.name = "delete_role";
			this.path = "bot.roles.custom_role.delete_role";
			this.options = List.of(
				new OptionData(OptionType.USER, "user", lu.getText(path+".user.help"), true)
			);
			this.requiredPermission = AccessPermission.ADMIN;
		}

		@Override
		public void execute(SlashCommandEvent event) {
			Guild guild = event.getGuild();
			assert guild != null;
			long guildId = guild.getIdLong();

			Member target = event.optMember("user");
			if (target == null) {
				editError(event, "errors.option.member");
				return;
			}
			long userId = target.getIdLong();

			Long roleId = bot.getDBUtil().customRoles.getByOwner(userId, guildId);
			if (roleId == null) {
				editError(event, path+".not_found");
				return;
			}

			Role role = guild.getRoleById(roleId);
			if (role != null) {
				role.delete().reason("Custom role deleted by admin").queue();
			}
			try {
				bot.getDBUtil().customRoles.remove(roleId);
			} catch (SQLException ex) {
				editErrorDatabase(event, ex, "delete custom role");
				return;
			}
			editEmbed(event, bot.getEmbedUtil().getEmbed(Constants.COLOR_SUCCESS)
				.setDescription(lu.getText(event, path+".done").formatted(target.getAsMention()))
				.build());
		}
	}

	// ---- USER: Request a custom role ----

	private class Request extends SlashCommand {
		public Request() {
			this.name = "request";
			this.path = "bot.roles.custom_role.request";
			this.options = List.of(
				new OptionData(OptionType.STRING, "name", lu.getText(path+".name.help"), true).setMaxLength(100),
				new OptionData(OptionType.STRING, "color", lu.getText(path+".color.help"), true).setMaxLength(7),
				new OptionData(OptionType.STRING, "second_color", lu.getText(path+".second_color.help")).setMaxLength(7),
				new OptionData(OptionType.STRING, "color_notes", lu.getText(path+".color_notes.help")).setMaxLength(200),
				new OptionData(OptionType.ATTACHMENT, "icon", lu.getText(path+".icon.help"))
			);
		}

		@Override
		public void execute(SlashCommandEvent event) {
			Guild guild = event.getGuild();
			assert guild != null && event.getMember() != null;
			long guildId = guild.getIdLong();
			long userId = event.getMember().getIdLong();

			if (!checkUserAccess(event, guildId, userId)) return;

			String name = event.optString("name", "");
			String color1 = event.optString("color", "");
			String color2 = event.optString("second_color");
			String colorNotes = event.optString("color_notes");

			if (!isValidHex(color1)) {
				editError(event, "bot.roles.custom_role.errors.invalid_hex", "Primary color: " + color1);
				return;
			}
			if (color2 != null && !isValidHex(color2)) {
				editError(event, "bot.roles.custom_role.errors.invalid_hex", "Secondary color: " + color2);
				return;
			}

			String iconUrl = null;
			try (var attachment = event.optAttachment("icon")) {
				if (attachment != null) {
					if (!attachment.isImage()) {
						editError(event, "bot.roles.custom_role.errors.icon_invalid");
						return;
					}
					if (attachment.getSize() > 256 * 1024) {
						editError(event, "bot.roles.custom_role.errors.icon_too_large");
						return;
					}
					iconUrl = attachment.getUrl();
				}
			}

			submitRequest(event, guildId, userId, name, color1, color2, colorNotes, iconUrl);
		}
	}

	// ---- USER: Add/replace icon on pending request ----

	private class Icon extends SlashCommand {
		public Icon() {
			this.name = "icon";
			this.path = "bot.roles.custom_role.icon";
			this.options = List.of(
				new OptionData(OptionType.ATTACHMENT, "image", lu.getText(path+".image.help"), true)
			);
		}

		@Override
		public void execute(SlashCommandEvent event) {
			Guild guild = event.getGuild();
			assert guild != null && event.getMember() != null;
			long guildId = guild.getIdLong();
			long userId = event.getMember().getIdLong();

			var pending = bot.getDBUtil().customRoleRequests.getPendingByUser(userId, guildId);
			if (pending == null) {
				editError(event, "bot.roles.custom_role.errors.no_pending");
				return;
			}

			try (var attachment = event.optAttachment("image")) {
				if (attachment == null || !attachment.isImage()) {
					editError(event, "bot.roles.custom_role.errors.icon_invalid");
					return;
				}
				if (attachment.getSize() > 256 * 1024) {
					editError(event, "bot.roles.custom_role.errors.icon_too_large");
					return;
				}

				try {
					bot.getDBUtil().customRoleRequests.updateDetails(
						pending.requestId, pending.roleName, pending.color1, pending.color2, attachment.getUrl()
					);
				} catch (SQLException ex) {
					editErrorDatabase(event, ex, "update icon");
					return;
				}
			}

			editEmbed(event, bot.getEmbedUtil().getEmbed(Constants.COLOR_SUCCESS)
				.setDescription(lu.getText(event, path+".done"))
				.build());
		}
	}

	// ---- USER: View own role/request status ----

	private class View extends SlashCommand {
		public View() {
			this.name = "view";
			this.path = "bot.roles.custom_role.view";
		}

		@Override
		public void execute(SlashCommandEvent event) {
			Guild guild = event.getGuild();
			assert guild != null && event.getMember() != null;
			long guildId = guild.getIdLong();
			long userId = event.getMember().getIdLong();

			EmbedBuilder eb = bot.getEmbedUtil().getEmbed(event)
				.setTitle(lu.getGuildText(event, path+".title"));

			Long roleId = bot.getDBUtil().customRoles.getByOwner(userId, guildId);
			if (roleId != null) {
				Role role = guild.getRoleById(roleId);
				eb.addField(lu.getGuildText(event, path+".active_role"),
					role != null ? role.getAsMention() : lu.getGuildText(event, path+".role_deleted"), false);
			}

			var pending = bot.getDBUtil().customRoleRequests.getPendingByUser(userId, guildId);
			if (pending != null) {
				eb.addField(lu.getGuildText(event, path+".pending_request"),
					"**%s** — %s".formatted(pending.roleName,
						lu.getGuildText(event, "bot.roles.custom_role.review.status.pending")), false);
			}

			if (roleId == null && pending == null) {
				eb.setDescription(lu.getGuildText(event, path+".none"));
			}

			editEmbed(event, eb.build());
		}
	}

	// ---- Shared helpers ----

	private boolean checkUserAccess(SlashCommandEvent event, long guildId, long userId) {
		var settings = bot.getDBUtil().customRoleSettings.getSettings(guildId);
		if (!settings.isConfigured()) {
			editError(event, "bot.roles.custom_role.errors.no_setup");
			return false;
		}
		if (!bot.getDBUtil().customRoleAccess.hasAccess(userId, guildId)) {
			editError(event, "bot.roles.custom_role.errors.no_access");
			return false;
		}
		if (bot.getDBUtil().customRoles.getByOwner(userId, guildId) != null) {
			editError(event, "bot.roles.custom_role.errors.has_role");
			return false;
		}
		if (bot.getDBUtil().customRoleRequests.getPendingByUser(userId, guildId) != null) {
			editError(event, "bot.roles.custom_role.errors.has_pending");
			return false;
		}
		return true;
	}

	private void submitRequest(SlashCommandEvent event, long guildId, long userId,
							   String name, String color1, @Nullable String color2,
	                           @Nullable String colorNotes, @Nullable String iconUrl) {
		Guild guild = event.getGuild();
		assert guild != null && event.getMember() != null;

		var settings = bot.getDBUtil().customRoleSettings.getSettings(guildId);

		long requestId;
		try {
			requestId = bot.getDBUtil().customRoleRequests.create(guildId, userId, name, color1, color2, colorNotes, iconUrl);
		} catch (SQLException ex) {
			editErrorDatabase(event, ex, "create custom role request");
			return;
		}

		TextChannel requestsChannel = settings.getRequestsChannelId() == null ? null : guild.getTextChannelById(settings.getRequestsChannelId());
		if (requestsChannel == null) {
			editError(event, "bot.roles.custom_role.errors.no_setup");
			return;
		}

		Role reviewerRole = settings.getReviewerRoleId() == null ? null : guild.getRoleById(settings.getReviewerRoleId());
		String ping = reviewerRole != null ? reviewerRole.getAsMention() : "";

		EmbedBuilder reviewEmbed = bot.getEmbedUtil().getEmbed()
			.setTitle(lu.getGuildText(event, "bot.roles.custom_role.review.title").formatted(requestId))
			.addField(lu.getGuildText(event, "bot.roles.custom_role.review.requested_by"), event.getMember().getAsMention(), true)
			.addField(lu.getGuildText(event, "bot.roles.custom_role.review.role_name"), name, true)
			.addField(lu.getGuildText(event, "bot.roles.custom_role.review.color_primary"), color1, true);
		if (color2 != null) {
			reviewEmbed.addField(lu.getGuildText(event, "bot.roles.custom_role.review.color_secondary"), color2, true);
		}
		if (colorNotes != null) {
			reviewEmbed.addField(lu.getGuildText(event, "bot.roles.custom_role.review.color_notes"), colorNotes, false);
		}
		if (iconUrl != null) {
			reviewEmbed.addField(lu.getGuildText(event, "bot.roles.custom_role.review.icon"), iconUrl, false)
				.setImage(iconUrl);
		}
		reviewEmbed.setFooter(lu.getGuildText(event, "bot.roles.custom_role.review.footer").formatted(guild.getName()));

		List<Button> buttons = new ArrayList<>();
		buttons.add(Button.success("cr:accept:" + requestId, lu.getGuildText(event, "bot.roles.custom_role.review.btn_accept")));
		buttons.add(Button.primary("cr:modify:" + requestId, lu.getGuildText(event, "bot.roles.custom_role.review.btn_modify")));
		buttons.add(Button.danger("cr:reject:" + requestId, lu.getGuildText(event, "bot.roles.custom_role.review.btn_reject")));

		final long fRequestId = requestId;
		requestsChannel.sendMessage(
			new MessageCreateBuilder()
				.setContent(ping)
				.setEmbeds(reviewEmbed.build())
				.setComponents(ActionRow.of(buttons))
				.build()
		).queue(msg -> {
			try {
				bot.getDBUtil().customRoleRequests.setMessageId(fRequestId, msg.getIdLong());
			} catch (SQLException ignored) {}
		});

		editEmbed(event, bot.getEmbedUtil().getEmbed(Constants.COLOR_SUCCESS)
			.setDescription(lu.getGuildText(event, "bot.roles.custom_role.request.submitted"))
			.build());
	}

	@SuppressWarnings("BooleanMethodIsAlwaysInverted")
	public static boolean isValidHex(@Nullable String hex) {
		if (hex == null || hex.isBlank()) return false;
		String h = hex.startsWith("#") ? hex.substring(1) : hex;
		return h.matches("[0-9A-Fa-f]{6}");
	}

	/** Normalizes a hex string to #RRGGBB format. */
	public static String normalizeHex(String hex) {
		return "#" + (hex.startsWith("#") ? hex.substring(1) : hex).toUpperCase();
	}

}
