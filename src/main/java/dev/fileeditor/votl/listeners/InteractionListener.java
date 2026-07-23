package dev.fileeditor.votl.listeners;

import static dev.fileeditor.votl.utils.CastUtil.castLong;

import dev.fileeditor.votl.commands.role.CustomRoleCmd;
import dev.fileeditor.votl.utils.database.managers.CustomRoleRequestsManager.CustomRoleRequest;
import dev.fileeditor.votl.utils.database.managers.CustomRoleSettingsManager.CustomRoleSettings;

import java.net.URI;
import java.sql.SQLException;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import ch.qos.logback.classic.Logger;
import dev.fileeditor.votl.App;
import dev.fileeditor.votl.base.command.CooldownScope;
import dev.fileeditor.votl.base.waiter.EventWaiter;
import dev.fileeditor.votl.commands.role.TempRoleCmd;
import dev.fileeditor.votl.metrics.Metrics;
import dev.fileeditor.votl.objects.CaseType;
import dev.fileeditor.votl.objects.AccessPermission;
import dev.fileeditor.votl.objects.Emote;
import dev.fileeditor.votl.objects.constants.Constants;
import dev.fileeditor.votl.utils.database.DBUtil;
import dev.fileeditor.votl.utils.database.managers.ServerBlacklistManager;
import dev.fileeditor.votl.utils.database.managers.CaseManager.CaseData;
import dev.fileeditor.votl.utils.database.managers.RoleManager;
import dev.fileeditor.votl.utils.database.managers.TicketTagManager.Tag;
import dev.fileeditor.votl.utils.exception.FormatterException;
import dev.fileeditor.votl.utils.file.lang.LocaleUtil;
import dev.fileeditor.votl.utils.message.MessageUtil;

import dev.fileeditor.votl.utils.message.TimeUtil;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.components.actionrow.ActionRow;
import net.dv8tion.jda.api.components.buttons.Button;
import net.dv8tion.jda.api.components.label.Label;
import net.dv8tion.jda.api.components.selections.EntitySelectMenu;
import net.dv8tion.jda.api.components.selections.SelectOption;
import net.dv8tion.jda.api.components.selections.StringSelectMenu;
import net.dv8tion.jda.api.components.textinput.TextInput;
import net.dv8tion.jda.api.components.textinput.TextInputStyle;
import net.dv8tion.jda.api.entities.*;
import net.dv8tion.jda.api.entities.MessageEmbed.Field;
import net.dv8tion.jda.api.entities.channel.concrete.Category;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import net.dv8tion.jda.api.entities.channel.concrete.ThreadChannel;
import net.dv8tion.jda.api.entities.channel.concrete.VoiceChannel;
import net.dv8tion.jda.api.entities.channel.middleman.GuildChannel;
import net.dv8tion.jda.api.entities.emoji.Emoji;
import net.dv8tion.jda.api.events.interaction.GenericInteractionCreateEvent;
import net.dv8tion.jda.api.events.interaction.ModalInteractionEvent;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import net.dv8tion.jda.api.events.interaction.component.EntitySelectInteractionEvent;
import net.dv8tion.jda.api.events.interaction.component.StringSelectInteractionEvent;
import net.dv8tion.jda.api.exceptions.ErrorHandler;
import net.dv8tion.jda.api.exceptions.InsufficientPermissionException;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import net.dv8tion.jda.api.interactions.DiscordLocale;
import net.dv8tion.jda.api.interactions.callbacks.IReplyCallback;
import net.dv8tion.jda.api.interactions.modals.ModalMapping;
import net.dv8tion.jda.api.managers.channel.concrete.VoiceChannelManager;
import net.dv8tion.jda.api.modals.Modal;
import net.dv8tion.jda.api.requests.ErrorResponse;
import net.dv8tion.jda.api.requests.restaction.ChannelAction;
import net.dv8tion.jda.api.utils.TimeFormat;
import net.dv8tion.jda.api.utils.messages.MessageEditData;
import net.dv8tion.jda.internal.utils.tuple.Pair;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.slf4j.LoggerFactory;

public class InteractionListener extends ListenerAdapter {

	private final Logger LOG = (Logger) LoggerFactory.getLogger(InteractionListener.class);

	private final App bot;
	private final LocaleUtil lu;
	private final DBUtil db;
	private final EventWaiter waiter;

	private final Set<Permission> adminPerms = Set.of(Permission.ADMINISTRATOR, Permission.MANAGE_SERVER, Permission.MANAGE_PERMISSIONS, Permission.MANAGE_ROLES, Permission.MANAGE_WEBHOOKS);
	private final int MAX_GROUP_SELECT = 1;

	public InteractionListener(App bot, EventWaiter waiter) {
		this.bot = bot;
		this.lu = bot.getLocaleUtil();
		this.db = bot.getDBUtil();
		this.waiter = waiter;
	}

	public void editError(IReplyCallback event, String... text) {
		if (text.length > 1) {
			event.getHook().editOriginalEmbeds(bot.getEmbedUtil().getError(event, text[0], text[1])).queue();
		} else {
			event.getHook().editOriginalEmbeds(bot.getEmbedUtil().getError(event, text[0])).queue();
		}
	}

	public void sendErrorLive(IReplyCallback event, String path) {
		event.replyEmbeds(bot.getEmbedUtil().getError(event, path)).setEphemeral(true).queue();
	}

	public void sendError(IReplyCallback event, String path) {
		event.getHook().sendMessageEmbeds(bot.getEmbedUtil().getError(event, path)).setEphemeral(true).queue();
	}

	public void sendError(IReplyCallback event, String path, String info) {
		event.getHook().sendMessageEmbeds(bot.getEmbedUtil().getError(event, path, info)).setEphemeral(true).queue();
	}

	public void sendSuccess(IReplyCallback event, String path) {
		event.getHook().sendMessageEmbeds(new EmbedBuilder().setColor(Constants.COLOR_SUCCESS).setDescription(lu.getText(event, path)).build()).setEphemeral(true).queue();
	}

	// Check for cooldown parameters, if exists - check if cooldown active, else apply it
	private void runButtonInteraction(ButtonInteractionEvent event, @Nullable Cooldown cooldown, @NotNull Runnable function) {
		// Acknowledge interaction
		event.deferEdit().queue(null, new ErrorHandler().ignore(ErrorResponse.UNKNOWN_INTERACTION));

		if (cooldown != null) {
			String key = getCooldownKey(cooldown, event);
			int remaining = bot.getClient().getRemainingCooldown(key);
			if (remaining > 0) {
				event.getHook().sendMessage(getCooldownErrorString(cooldown, event, remaining)).setEphemeral(true).queue();
				return;
			} else {
				bot.getClient().applyCooldown(key, cooldown.getTime());
			}
		}

		function.run();
	}

	private void runModalButtonInteraction(ButtonInteractionEvent event, @Nullable Cooldown cooldown, @NotNull Runnable function) {
		if (cooldown != null) {
			String key = getCooldownKey(cooldown, event);
			int remaining = bot.getClient().getRemainingCooldown(key);
			if (remaining > 0) {
				event.reply(getCooldownErrorString(cooldown, event, remaining)).setEphemeral(true)
					.queue(null, new ErrorHandler().ignore(ErrorResponse.UNKNOWN_INTERACTION));
				return;
			} else {
				bot.getClient().applyCooldown(key, cooldown.getTime());
			}
		}

		function.run();
	}

	@Override
	public void onButtonInteraction(@NotNull ButtonInteractionEvent event) {
		// Check if blacklisted
		if (bot.getBlacklist().isBlacklisted(event)) return;

		String[] actions = event.getComponentId().split(":");

		try {
			switch (actions[0]) {
				case "verify" -> {
					Metrics.interactionReceived.labelValue("verify").inc();
					runButtonInteraction(event, Cooldown.BUTTON_VERIFY, () -> buttonVerify(event));
				}
				case "role" -> {
					Metrics.interactionReceived.labelValue("role:"+actions[1]).inc();
					switch (actions[1]) {
						case "start_request" -> runButtonInteraction(event, Cooldown.BUTTON_ROLE_SHOW, () -> buttonRoleShowSelection(event));
						case "other" -> runButtonInteraction(event, Cooldown.BUTTON_ROLE_OTHER, () -> buttonRoleSelectionOther(event));
						case "clear" -> runButtonInteraction(event, Cooldown.BUTTON_ROLE_CLEAR, () -> buttonRoleSelectionClear(event));
						case "remove" -> runButtonInteraction(event, Cooldown.BUTTON_ROLE_REMOVE, () -> buttonRoleRemove(event));
						case "toggle" -> runButtonInteraction(event, Cooldown.BUTTON_ROLE_TOGGLE, () -> buttonRoleToggle(event));
					}
				}
				case "ticket" -> {
					Metrics.interactionReceived.labelValue("ticket:"+actions[1]).inc();
					switch (actions[1]) {
						case "role_create" -> runButtonInteraction(event, Cooldown.BUTTON_ROLE_TICKET, () -> buttonRoleTicketCreate(event));
						case "role_approve" -> runButtonInteraction(event, Cooldown.BUTTON_ROLE_APPROVE, () -> buttonRoleTicketApprove(event));
						case "close" -> runButtonInteraction(event, Cooldown.BUTTON_TICKET_CLOSE, () -> buttonTicketClose(event));
						case "cancel" -> runButtonInteraction(event, Cooldown.BUTTON_TICKET_CANCEL, () -> buttonTicketCloseCancel(event));
						case "claim" -> runButtonInteraction(event, Cooldown.BUTTON_TICKET_CLAIM, () -> buttonTicketClaim(event));
						case "unclaim" -> runButtonInteraction(event, Cooldown.BUTTON_TICKET_UNCLAIM, () -> buttonTicketUnclaim(event));
					}
				}
				case "tag" -> {
					Metrics.interactionReceived.labelValue("tag").inc();
					runButtonInteraction(event, Cooldown.BUTTON_TICKET_CREATE, () -> buttonTagCreateTicket(event));
				}
				case "delete" -> {
					Metrics.interactionReceived.labelValue("delete").inc();
					runButtonInteraction(event, Cooldown.BUTTON_REPORT_DELETE, () -> buttonReportDelete(event));
				}
				case "voice" -> {
					assert event.getGuild() != null && event.getMember() != null;
					if (event.getMember().getVoiceState() == null || !event.getMember().getVoiceState().inAudioChannel()) {
						sendErrorLive(event, "bot.voice.listener.not_in_voice");
						return;
					}
					Long channelId = db.voice.getChannel(event.getUser().getIdLong());
					if (channelId == null) {
						sendErrorLive(event, "errors.no_channel");
						return;
					}
					VoiceChannel vc = event.getGuild().getVoiceChannelById(channelId);
					if (vc == null) return;

					Metrics.interactionReceived.labelValue("voice:"+actions[1]).inc();
					switch (actions[1]) {
						case "lock" -> runButtonInteraction(event, null, () -> buttonVoiceLock(event, vc));
						case "unlock" -> runButtonInteraction(event, null, () -> buttonVoiceUnlock(event, vc));
						case "ghost" -> runButtonInteraction(event, null, () -> buttonVoiceGhost(event, vc));
						case "unghost" -> runButtonInteraction(event, null, () -> buttonVoiceUnghost(event, vc));
						case "permit" -> runButtonInteraction(event, null, () -> buttonVoicePermit(event));
						case "reject" -> runButtonInteraction(event, null, () -> buttonVoiceReject(event));
						case "perms" -> runButtonInteraction(event, null, () -> buttonVoicePerms(event, vc));
						case "delete" -> runButtonInteraction(event, null, () -> buttonVoiceDelete(event, vc));
					}
				}
				case "blacklist" -> {
					Metrics.interactionReceived.labelValue("blacklist").inc();
					runButtonInteraction(event, Cooldown.BUTTON_SYNC_ACTION, () -> buttonBlacklist(event));
				}
				case "sync_unban" -> {
					Metrics.interactionReceived.labelValue("sync_unban").inc();
					runButtonInteraction(event, null, () -> buttonSyncUnban(event));
				}
				case "sync_ban" -> {
					Metrics.interactionReceived.labelValue("sync_ban").inc();
					runButtonInteraction(event, Cooldown.BUTTON_SYNC_ACTION, () -> buttonSyncBan(event));
				}
				case "sync_kick" -> {
					Metrics.interactionReceived.labelValue("sync_kick").inc();
					runButtonInteraction(event, Cooldown.BUTTON_SYNC_ACTION, () -> buttonSyncKick(event));
				}
				case "strikes" -> {
					Metrics.interactionReceived.labelValue("strikes").inc();
					runButtonInteraction(event, Cooldown.BUTTON_SHOW_STRIKES, () -> buttonShowStrikes(event));
				}
				case "manage-confirm" -> runButtonInteraction(event, Cooldown.BUTTON_MODIFY_CONFIRM, () -> buttonModifyConfirm(event));
				case "custom_role" -> {
					Metrics.interactionReceived.labelValue("custom_role").inc();
					if ("create".equals(actions[1])) runModalButtonInteraction(event, Cooldown.BUTTON_CUSTOM_ROLE_REQUEST, () -> buttonCustomRoleRequest(event));
					else if ("edit".equals(actions[1])) runModalButtonInteraction(event, Cooldown.BUTTON_CUSTOM_ROLE_REQUEST, () -> buttonCustomRoleEdit(event));
				}
				case "cr" -> {
					Metrics.interactionReceived.labelValue("cr:"+actions[1]).inc();
					switch (actions[1]) {
						case "accept" -> runButtonInteraction(event, null, () -> buttonCustomRoleAccept(event, Long.parseLong(actions[2])));
						case "modify" -> runModalButtonInteraction(event, null, () -> buttonCustomRoleModify(event, Long.parseLong(actions[2])));
						case "reject" -> runModalButtonInteraction(event, null, () -> buttonCustomRoleReject(event, Long.parseLong(actions[2])));
					}
				}
				default -> LOG.debug("Unknown button interaction: {}", event.getComponentId());
			}
		} catch (Throwable t) {
			// Logs throwable and tries to respond to the user with the error
			// Thrown errors are not user's error, but code's fault as such things should be caught earlier and replied properly
			LOG.error("ButtonInteraction Exception", t);
			bot.getEmbedUtil().sendUnknownError(event.getHook(), lu.getLocale(event), t.getMessage());
		}
	}

	private void buttonVerify(ButtonInteractionEvent event) {
		Member member = event.getMember();
		Guild guild = event.getGuild();
		assert guild != null && member != null;

		Long verifyRoleId = db.getVerifySettings(guild).getRoleId();
		if (verifyRoleId == null) {
			sendError(event, "bot.verification.failed_role", "The verification role is not configured.");
			return;
		}
		Role verifyRole = guild.getRoleById(verifyRoleId);
		if (verifyRole == null) {
			sendError(event, "bot.verification.failed_role", "Verification role not found.");
			return;
		}
		if (member.getRoles().contains(verifyRole)) {
			sendError(event, "bot.verification.you_verified");
			return;
		}

		// Check if user is blacklisted in any group this guild owns or is part of (including parent groups up the hierarchy)
		List<Integer> groupIds = new ArrayList<>();
		groupIds.addAll(db.group.getOwnedGroups(guild.getIdLong()));
		groupIds.addAll(bot.getHelper().collectParentGroupIds(guild.getIdLong()));
		for (int groupId : groupIds) {
			ServerBlacklistManager.BlacklistData data = db.serverBlacklist.getInfo(groupId, member.getIdLong());
			if (data != null && db.group.getAppealGuildId(groupId)!=guild.getIdLong()) {
				sendError(event, "bot.verification.blacklisted", "Reason: "+data.getReason());
				return;
			}
		}

		Set<Long> additionalRoles = db.getVerifySettings(guild).getAdditionalRoles();
		if (additionalRoles.isEmpty()) {
			guild.addRoleToMember(member, verifyRole).reason("Verification completed").queue(
				_ -> event.getHook().sendMessage(Constants.SUCCESS).setEphemeral(true).queue(),
				failure -> {
					sendError(event, "bot.verification.failed_role");
					LOG.warn("Was unable to add verify role to user in {}({})\n  {}", guild.getName(), guild.getId(), failure.getMessage());
				}
			);
		} else {
			List<Role> finalRoles = new ArrayList<>(member.getRoles());
			// add verify role
			finalRoles.add(verifyRole);
			// add each additional role
			additionalRoles.stream()
				.map(guild::getRoleById)
				.filter(Objects::nonNull)
				.forEach(finalRoles::add);
			// modify
			guild.modifyMemberRoles(member, finalRoles)
				.reason("Verification completed")
				.queue(
					_ -> event.getHook().sendMessage(Constants.SUCCESS).setEphemeral(true).queue(),
				failure -> {
					sendError(event, "bot.verification.failed_role");
					LOG.warn("Was unable to add roles to user in {}({})\n  {}", guild.getName(), guild.getId(), failure.getMessage());
				}
			);
		}
	}

	// Role selection
	private void buttonRoleShowSelection(ButtonInteractionEvent event) {
		Guild guild = event.getGuild();
		assert guild != null && event.getMember() != null;

		Long channelId = db.tickets.getOpenedChannel(event.getMember().getIdLong(), guild.getIdLong(), 0);
		if (channelId != null) {
			ThreadChannel channel = guild.getThreadChannelById(channelId);
			if (channel != null) {
				event.getHook().sendMessageEmbeds(new EmbedBuilder().setColor(Constants.COLOR_FAILURE)
					.setDescription(lu.getGuildText(event, "bot.ticketing.listener.ticket_exists", channel.getAsMention()))
					.build()
				).setEphemeral(true).queue();
				return;
			}
			ignoreExc(() -> db.tickets.closeTicket(Instant.now(), channelId, "BOT: Channel deleted (not found)"));
		}

		List<ActionRow> actionRows = new ArrayList<>();
		// String select menu IDs "menu:role_row:1/2/3"
		for (int row = 1; row <= 3; row++) {
			ActionRow actionRow = createRoleRow(guild, row);
			if (actionRow != null) {
				actionRows.add(actionRow);
			}
		}
		if (db.getTicketSettings(guild).otherRoleEnabled()) {
			actionRows.add(ActionRow.of(Button.secondary("role:other", lu.getGuildText(event, "bot.ticketing.listener.request_other"))));
		}
		actionRows.add(ActionRow.of(Button.danger("role:clear", lu.getGuildText(event, "bot.ticketing.listener.request_clear")),
			Button.success("ticket:role_create", lu.getGuildText(event, "bot.ticketing.listener.request_continue"))));

		MessageEmbed embed = new EmbedBuilder()
			.setColor(Constants.COLOR_DEFAULT)
			.setDescription(lu.getGuildText(event, "bot.ticketing.listener.request_title"))
			.build();

		event.getHook().sendMessageEmbeds(embed).setComponents(actionRows).setEphemeral(true).queue();
	}

	@Nullable
	private ActionRow createRoleRow(final Guild guild, int row) {
		List<RoleManager.RoleData> roles = bot.getDBUtil().roles.getAssignableByRow(guild.getIdLong(), row);
		if (roles.isEmpty()) return null;
		List<SelectOption> options = new ArrayList<>();
		for (RoleManager.RoleData data : roles) {
			if (options.size() >= 25) break;
			Role role = guild.getRoleById(data.getIdLong());
			if (role == null) continue;
			options.add(SelectOption.of(role.getName(), role.getId()).withDescription(data.getDescription(null)));
		}
		if (options.isEmpty()) return null;
		StringSelectMenu menu = StringSelectMenu.create("menu:role_row:"+row)
			.setPlaceholder(db.getTicketSettings(guild).getRowText(row))
			.setMaxValues(25)
			.addOptions(options)
			.build();
		return ActionRow.of(menu);
	}

	private void buttonRoleSelectionOther(ButtonInteractionEvent event) {
		List<Field> fields = event.getMessage().getEmbeds().getFirst().getFields();
		List<Long> roleIds = MessageUtil.getRoleIdsFromString(fields.isEmpty() ? "" : Objects.requireNonNull(fields.getFirst().getValue()));
		if (roleIds.contains(0L))
			roleIds.remove(0L);
		else
			roleIds.add(0L);
		
		MessageEmbed embed = new EmbedBuilder(event.getMessage().getEmbeds().getFirst())
			.clearFields()
			.addField(lu.getGuildText(event, "bot.ticketing.listener.request_selected"), selectedRolesString(roleIds, lu.getLocale(event)), false)
			.build();
		event.getHook().editOriginalEmbeds(embed).queue();
	}

	private void buttonRoleSelectionClear(ButtonInteractionEvent event) {
		MessageEmbed embed = new EmbedBuilder(event.getMessage().getEmbeds().getFirst())
			.clearFields()
			.addField(lu.getGuildText(event, "bot.ticketing.listener.request_selected"), selectedRolesString(Collections.emptyList(), lu.getLocale(event)), false)
			.build();
		event.getHook().editOriginalEmbeds(embed).queue();
	}

	private void buttonRoleRemove(ButtonInteractionEvent event) {
		Guild guild = event.getGuild();
		assert guild != null && event.getMember() != null;

		List<Role> currentRoles = event.getMember().getRoles();

		List<Role> allRoles = new ArrayList<>();
		db.roles.getAssignable(guild.getIdLong()).forEach(data -> allRoles.add(guild.getRoleById(data.getIdLong())));
		db.roles.getCustom(guild.getIdLong()).forEach(data -> allRoles.add(guild.getRoleById(data.getIdLong())));
		List<Role> roles = allRoles.stream().filter(currentRoles::contains).toList();
		if (roles.isEmpty()) {
			event.getHook().sendMessageEmbeds(bot.getEmbedUtil().getError(event, "bot.ticketing.listener.no_assigned")).setEphemeral(true).queue();
			return;
		}

		List<SelectOption> options = roles.stream().map(role -> SelectOption.of(role.getName(), role.getId())).toList();	
		StringSelectMenu menu = StringSelectMenu.create("menu:role_remove")
			.setPlaceholder(lu.getGuildText(event, "bot.ticketing.listener.request_template"))
			.setMaxValues(options.size())
			.addOptions(options)
			.build();
		event.getHook()
			.sendMessageEmbeds(bot.getEmbedUtil()
				.getEmbed()
				.setDescription(lu.getGuildText(event, "bot.ticketing.listener.remove_title"))
				.build()
			).setComponents(ActionRow.of(menu))
			.setEphemeral(true)
			.queue(msg ->
				waiter.waitForEvent(
					StringSelectInteractionEvent.class,
					e -> e.getComponentId().equals("menu:role_remove"),
					actionEvent -> {
						List<Role> remove = actionEvent.getSelectedOptions().stream()
							.map(option -> guild.getRoleById(option.getValue()))
							.filter(Objects::nonNull)
							.toList();
						guild.modifyMemberRoles(event.getMember(), null, remove).reason("User request").queue(_ ->
							msg.editMessageEmbeds(bot.getEmbedUtil()
								.getEmbed()
								.setDescription(lu.getGuildText(event, "bot.ticketing.listener.remove_done", remove.stream().map(Role::getAsMention).collect(Collectors.joining(", "))))
								.setColor(Constants.COLOR_SUCCESS)
								.build()
							)
							.setComponents()
							.queue(),
						failure -> msg.editMessageEmbeds(bot.getEmbedUtil().getError(event, "bot.ticketing.listener.remove_failed", failure.getMessage()))
							.setComponents()
							.queue()
						);
					},
					40,
					TimeUnit.SECONDS,
					() -> msg.editMessageComponents(ActionRow.of(
						menu.createCopy().setPlaceholder(lu.getGuildText(event, "errors.timed_out")).setDisabled(true).build())
					).queue()
				)
			);
	}

	private void buttonRoleToggle(ButtonInteractionEvent event) {
		assert event.getGuild() != null && event.getMember() != null;
		assert event.getButton().getCustomId() != null;
		Long roleId = castLong(event.getButton().getCustomId().split(":")[2]);
		Role role = event.getGuild().getRoleById(roleId);
		if (role == null || !db.roles.isToggleable(roleId)) {
			sendError(event, "bot.ticketing.listener.toggle_failed", "Role not found or can't be toggled");
			return;
		}

		if (event.getMember().getRoles().contains(role)) {
			event.getGuild().removeRoleFromMember(event.getMember(), role).queue(_ -> event.getHook()
					.sendMessageEmbeds(bot.getEmbedUtil()
						.getEmbed()
						.setDescription(lu.getGuildText(event, "bot.ticketing.listener.toggle_removed", role.getAsMention()))
						.setColor(Constants.COLOR_SUCCESS)
						.build()
					)
					.setEphemeral(true)
					.queue(),
				failure -> sendError(event, "bot.ticketing.listener.toggle_failed", failure.getMessage())
			);
		} else {
			event.getGuild().addRoleToMember(event.getMember(), role).queue(_ -> event.getHook()
					.sendMessageEmbeds(bot.getEmbedUtil()
						.getEmbed()
						.setDescription(lu.getGuildText(event, "bot.ticketing.listener.toggle_added", role.getAsMention()))
						.setColor(Constants.COLOR_SUCCESS)
						.build()
					)
					.setEphemeral(true)
					.queue(),
				failure -> sendError(event, "bot.ticketing.listener.toggle_failed", failure.getMessage())
			);
		}
	}

	// Role ticket
	private void buttonRoleTicketCreate(ButtonInteractionEvent event) {
		Guild guild = event.getGuild();
		assert guild != null && event.getMember() != null;
		long guildId = guild.getIdLong();

		final List<Long> supportRoleIds = db.ticketSettings.getSettings(guild.getIdLong()).getRoleSupportIds();
		if (supportRoleIds.isEmpty()) {
			sendError(event, "bot.ticketing.listener.no_support_roles");
			return;
		}

		// Check if user has selected any role
		List<Field> fields = event.getMessage().getEmbeds().getFirst().getFields();
		List<Long> roleIds = MessageUtil.getRoleIdsFromString(fields.isEmpty() ? "" : Objects.requireNonNull(fields.getFirst().getValue()));
		if (roleIds.isEmpty()) {
			sendError(event, "bot.ticketing.listener.request_none");
			return;
		}
		// Check if bot is able to give selected roles
		boolean otherRole = roleIds.contains(0L);
		List<Role> memberRoles = event.getMember().getRoles();
		List<Role> add = roleIds.stream()
			.filter(option -> !option.equals(0L))
			.map(guild::getRoleById)
			.filter(role -> role != null && !memberRoles.contains(role))
			.toList();
		if (!otherRole && add.isEmpty()) {
			sendError(event, "bot.ticketing.listener.request_empty");
			return;
		}

		// final role IDs list
		List<String> finalRoleIds = new ArrayList<>();
		add.forEach(role -> {
			if (db.roles.isTemp(role.getIdLong()))
				finalRoleIds.add("t"+role.getId());
			else
				finalRoleIds.add(role.getId());
		});

		int ticketId = 1 + db.tickets.lastIdByTag(guildId, 0);
		event.getChannel().asTextChannel().createThreadChannel(lu.getGuildText(event, "ticket.role")+"-"+ticketId, true).setInvitable(false).queue(
			channel -> {
				db.tickets.addRoleTicket(
					ticketId, event.getMember().getIdLong(), guildId, channel.getIdLong(),
					String.join(";", finalRoleIds), bot.getDBUtil().getTicketSettings(guild).getTimeToReply()
				);
				
				StringBuffer mentions = new StringBuffer(event.getMember().getAsMention());
				// Get support roles
				mentions.append("||");
				supportRoleIds.forEach(roleId -> mentions.append(" <@&").append(roleId).append(">"));
				mentions.append("||");
				// Send message
				channel.sendMessage(mentions.toString()).queue(msg -> {
					if (db.getTicketSettings(guild).deletePingsEnabled())
						msg.delete().queueAfter(5, TimeUnit.SECONDS, null, new ErrorHandler().ignore(ErrorResponse.UNKNOWN_CHANNEL));
				});
				
				String rolesString = String.join(" ", add.stream().map(Role::getAsMention).collect(Collectors.joining(" ")), (otherRole ? lu.getGuildText(event, "bot.ticketing.embeds.other") : ""));
				String proofString = add.stream().map(role -> db.roles.getDescription(role.getIdLong())).filter(Objects::nonNull).distinct().collect(Collectors.joining("\n- ", "- ", ""));
				MessageEmbed embed = new EmbedBuilder().setColor(db.getGuildSettings(guild).getColor())
					.setDescription(String.format("%s\n> %s\n\n%s, %s\n%s\n\n%s",
						lu.getGuildText(event, "ticket.role_title"),
						rolesString,
						event.getMember().getEffectiveName(),
						lu.getGuildText(event, "ticket.role_header"),
						(proofString.length() < 3 ? lu.getGuildText(event, "ticket.role_proof") : proofString),
						lu.getGuildText(event, "ticket.role_footer")
					))
					.build();
				Button approve = Button.success("ticket:role_approve", lu.getGuildText(event, "ticket.role_approve"));
				Button close = Button.danger("ticket:close", lu.getGuildText(event, "ticket.close")).withEmoji(Emoji.fromUnicode("🔒")).asDisabled();
				channel.sendMessageEmbeds(embed)
					.setAllowedMentions(Collections.emptyList())
					.setComponents(ActionRow.of(approve, close))
					.queue(msg -> msg.editMessageComponents(ActionRow.of(approve, close.asEnabled())).queueAfter(10, TimeUnit.SECONDS));

				// Log
				bot.getGuildLogger().ticket.onCreate(guild, channel, event.getUser());
				// Send reply
				event.getHook().editOriginalEmbeds(new EmbedBuilder().setColor(Constants.COLOR_SUCCESS)
					.setDescription(lu.getGuildText(event, "bot.ticketing.listener.created", channel.getAsMention()))
					.build()
				).setComponents().queue();
			}, failure -> event.getHook().editOriginalEmbeds(bot.getEmbedUtil().getError(event, "bot.ticketing.listener.cant_create", failure.getMessage())).setComponents().queue()
		);
	}

	private void buttonRoleTicketApprove(ButtonInteractionEvent event) {
		assert event.getGuild() != null && event.getMember() != null;
		List<Long> supportRoleIds = bot.getDBUtil().getTicketSettings(event.getGuild()).getRoleSupportIds();
		if (denyTicketAction(supportRoleIds, event.getMember())) {
			sendError(event, "errors.interaction.no_access", "Ticket support or Admin permission");
			return;
		}
		long channelId = event.getChannel().getIdLong();
		if (db.tickets.isClosed(channelId)) {
			sendError(event, "bot.ticketing.listener.is_closed");
			return;
		}
		Guild guild = event.getGuild();
		Long userId = db.tickets.getUserId(channelId);

		guild.retrieveMemberById(userId).queue(member -> {
			List<Role> tempRoles = new ArrayList<>();
			List<Role> roles = new ArrayList<>();
			db.tickets.getRoleIds(channelId).forEach(v -> {
				if (v.charAt(0) == 't') {
					long roleId = castLong(v.substring(1));
					Role role = guild.getRoleById(roleId);
					if (role != null) tempRoles.add(role);
				} else {
					long roleId = castLong(v);
					Role role = guild.getRoleById(roleId);
					if (role != null) roles.add(role);
				}
			});
			if (!tempRoles.isEmpty()) {
				// Has temp roles - send modal
				List<Label> labels = new ArrayList<>();
				for (Role role : tempRoles) {
					if (labels.size() >= 5) continue;
					TextInput input = TextInput.create(role.getId(), TextInputStyle.SHORT)
						.setPlaceholder("1w - 1 Week, 30d - 30 Days, 0 - permanently")
						.setRequired(true)
						.setMaxLength(10)
						.build();
					labels.add(Label.of(role.getName(), input));
				}
				Modal modal = Modal.create("role_temp:"+channelId, lu.getGuildText(event, "bot.ticketing.listener.temp_time"))
					.addComponents(labels)
					.build();
				String buttonUuid = UUID.randomUUID().toString();
				Button continueButton = Button.success(buttonUuid, "Continue");
				event.getHook()
					.sendMessageEmbeds(bot.getEmbedUtil()
						.getEmbed(event)
						.setDescription(lu.getGuildText(event, "bot.ticketing.listener.temp_continue", labels.size()))
						.build()
					)
					.setComponents(ActionRow.of(continueButton))
					.setEphemeral(true)
					.queue(msg -> waiter.waitForEvent(
						ButtonInteractionEvent.class,
						e -> e.getComponentId().equals(buttonUuid),
						buttonEvent -> {
							buttonEvent.replyModal(modal).queue();
							msg.delete().queue();
							// Maybe reply, that other mod started to fill modal
						},
						10,
						TimeUnit.SECONDS,
						() -> msg.delete().queue()
					));
				return;
			}
			if (roles.isEmpty()) {
				event.getHook().sendMessageEmbeds(bot.getEmbedUtil().getEmbed(event)
					.setDescription(lu.getGuildText(event, "bot.ticketing.listener.role_none"))
					.setColor(Constants.COLOR_WARNING)
					.build()
				).setEphemeral(true).queue();
				return;
			}

			final int ticketId = db.tickets.getTicketId(channelId);
			guild.modifyMemberRoles(member, roles, null)
				.reason("Request role-"+ticketId+" approved by "+event.getMember().getEffectiveName())
				.queue(_ -> {
					bot.getGuildLogger().role.onApproved(member, event.getMember(), guild, roles, ticketId);
					db.tickets.setClaimed(channelId, event.getMember().getIdLong());
					event.getHook().sendMessageEmbeds(bot.getEmbedUtil().getEmbed(event)
						.setDescription(lu.getGuildText(event, "bot.ticketing.listener.role_added"))
						.setColor(Constants.COLOR_SUCCESS)
						.build()
					).queue(null, new ErrorHandler().ignore(ErrorResponse.UNKNOWN_WEBHOOK));
					member.getUser().openPrivateChannel().queue(dm -> dm.sendMessage(lu.getGuildText(event, "bot.ticketing.listener.role_dm")
						.replace("{roles}", roles.stream().map(Role::getName).collect(Collectors.joining(" | ")))
						.replace("{server}", guild.getName())
						.replace("{id}", String.valueOf(ticketId))
						.replace("{mod}", event.getMember().getEffectiveName())
					).queue(null, new ErrorHandler().ignore(ErrorResponse.CANNOT_SEND_TO_USER, ErrorResponse.NO_MUTUAL_GUILDS)));
				}, failure -> sendError(event, "bot.ticketing.listener.role_failed", failure.getMessage()));
		}, failure -> sendError(event, "bot.ticketing.listener.no_member", failure.getMessage()));
	}

	private void buttonTicketClose(ButtonInteractionEvent event) {
		assert event.getGuild() != null && event.getMember() != null;
		long channelId = event.getChannelIdLong();
		if (db.tickets.isClosed(channelId)) {
			// Ticket is closed
			event.getChannel().delete().queue();
			return;
		}
		// Check who can close tickets
		final boolean isAuthor = db.tickets.getUserId(channelId).equals(event.getUser().getIdLong());
		if (!isAuthor) {
			switch (db.getTicketSettings(event.getGuild()).getAllowClose()) {
				case EVERYONE -> {}
				case SUPPORT_PERMISSION -> {
					// Check if user has Helper+ access
					if (!bot.getCheckUtil().resolve(event.getMember()).has(AccessPermission.TICKET_SUPPORT)) {
						// No access - reject
						sendError(event, "errors.interaction.no_access", "`Ticket support` permission");
						return;
					}
				}
				case DIRECT_SUPPORT_ROLES -> {
					// Check if user is ticket support(or mod if support empty) or has Admin+ access
					int tagId = db.tickets.getTag(channelId);
					List<Long> supportRoleIds;
					if (tagId==0) {
						// Role request ticket
						supportRoleIds = db.getTicketSettings(event.getGuild()).getRoleSupportIds();
					} else {
						// Standard ticket
						supportRoleIds = Stream.of(db.ticketTags.getSupportRolesString(tagId).split(";"))
							.filter(v -> !v.isBlank())
							.map(Long::parseLong)
							.toList();
					}
					// Check
					if (denyTicketAction(supportRoleIds, event.getMember())) {
						sendError(event, "errors.interaction.no_access", "'Support' for this ticket or Admin+ access");
						return;
					}
				}
			}
		}
		// Close
		String reason = isAuthor
			? lu.getGuildText(event, "bot.ticketing.listener.closed_author")
			: lu.getGuildText(event, "bot.ticketing.listener.closed_support");
		event.editButton(Button.danger("ticket:close", bot.getLocaleUtil().getGuildText(event, "ticket.close")).withEmoji(Emoji.fromUnicode("🔒")).asDisabled()).queue();
		// Send message
		event.getHook()
			.sendMessageEmbeds(bot.getEmbedUtil()
				.getEmbed(event)
				.setDescription(lu.getGuildText(event, "bot.ticketing.listener.delete_countdown"))
				.build()
			)
			.queue(msg -> bot.getTicketUtil()
				.closeTicket(channelId, event.getUser(), reason, t -> {
					if (ErrorResponse.UNKNOWN_MESSAGE.test(t) || ErrorResponse.UNKNOWN_CHANNEL.test(t)) return;
					LOG.error("Couldn't close ticket with channelID '{}'", channelId, t);
					msg.editMessageEmbeds(bot.getEmbedUtil().getError(event, "bot.ticketing.listener.close_failed", t.getMessage())).queue();
				})
			);
	}

	private boolean denyTicketAction(List<Long> roleIds, Member member) {
		if (!roleIds.isEmpty()) {
			final List<Role> roles = member.getRoles(); // Check if user has any support role
			if (!roles.isEmpty() && roles.stream().anyMatch(r -> roleIds.contains(r.getIdLong()))) return false;
		}
		return !bot.getCheckUtil().hasAccess(member, AccessPermission.ADMIN); // if user has Admin access
	}

	private void buttonTicketCloseCancel(ButtonInteractionEvent event) {
		long channelId = event.getChannel().getIdLong();
		Guild guild = event.getGuild();
		assert guild != null;

		if (db.tickets.isClosed(channelId)) {
			// Ticket is closed
			event.getChannel().delete().queue();
			return;
		}
		db.tickets.setRequestStatus(channelId, -1L);
		MessageEmbed embed = new EmbedBuilder()
			.setColor(db.getGuildSettings(guild).getColor())
			.setDescription(lu.getGuildText(event, "ticket.autoclose_cancel"))
			.build();
		event.getHook().editOriginalEmbeds(embed).setComponents().queue();
	}

	// Ticket management
	private void buttonTicketClaim(ButtonInteractionEvent event) {
		assert event.getMember() != null;
		if (!bot.getCheckUtil().resolve(event.getMember()).has(AccessPermission.TICKET_SUPPORT)) {
			// User has no Helper's access or higher to approve role request
			sendError(event, "errors.interaction.no_access");
			return;
		}
		long channelId = event.getChannel().getIdLong();
		if (db.tickets.isClosed(channelId)) {
			sendError(event, "bot.ticketing.listener.is_closed");
			return;
		}

		db.tickets.setClaimed(channelId, event.getUser().getIdLong());
		event.getHook().sendMessageEmbeds(new EmbedBuilder().setColor(Constants.COLOR_SUCCESS)
			.setDescription(lu.getGuildText(event, "bot.ticketing.listener.claimed").replace("{user}", event.getUser().getAsMention()))
			.build()
		).queue();

		Button close = Button.danger("ticket:close", lu.getGuildText(event, "ticket.close")).withEmoji(Emoji.fromUnicode("🔒"));
		Button claimed = Button.primary("ticket:claimed", lu.getGuildText(event, "ticket.claimed", event.getUser().getName())).asDisabled();
		Button unclaim = Button.primary("ticket:unclaim", lu.getGuildText(event, "ticket.unclaim"));
		event.getMessage().editMessageComponents(ActionRow.of(close, claimed, unclaim)).queue(null, new ErrorHandler().ignore(ErrorResponse.UNKNOWN_MESSAGE));
	}

	private void buttonTicketUnclaim(ButtonInteractionEvent event) {
		assert event.getMember() != null;
		if (!bot.getCheckUtil().resolve(event.getMember()).has(AccessPermission.TICKET_SUPPORT)) {
			// User has no Helper's access or higher to approve role request
			sendError(event, "errors.interaction.no_access");
			return;
		}
		long channelId = event.getChannel().getIdLong();
		if (db.tickets.isClosed(channelId)) {
			sendError(event, "bot.ticketing.listener.is_closed");
			return;
		}

		db.tickets.setUnclaimed(channelId);
		event.getHook().sendMessageEmbeds(new EmbedBuilder().setColor(Constants.COLOR_SUCCESS)
			.setDescription(lu.getGuildText(event, "bot.ticketing.listener.unclaimed"))
			.build()
		).queue();

		Button close = Button.danger("ticket:close", lu.getGuildText(event, "ticket.close")).withEmoji(Emoji.fromUnicode("🔒"));
		Button claim = Button.primary("ticket:claim", lu.getGuildText(event, "ticket.claim"));
		event.getMessage().editMessageComponents(ActionRow.of(close, claim)).queue(null, new ErrorHandler().ignore(ErrorResponse.UNKNOWN_MESSAGE));
	}

	// Tag, create ticket
	private void buttonTagCreateTicket(ButtonInteractionEvent event) {
		assert event.getGuild() != null && event.getMember() != null;
		long guildId = event.getGuild().getIdLong();
		int tagId = Integer.parseInt(event.getComponentId().split(":")[1]);

		Long channelId = db.tickets.getOpenedChannel(event.getMember().getIdLong(), guildId, tagId);
		if (channelId != null) {
			GuildChannel channel = event.getGuild().getGuildChannelById(channelId);
			if (channel != null) {
				event.getHook().sendMessageEmbeds(new EmbedBuilder().setColor(Constants.COLOR_FAILURE)
					.setDescription(lu.getGuildText(event, "bot.ticketing.listener.ticket_exists", channel.getAsMention()))
					.build()
				).setEphemeral(true).queue();
				return;
			}
			ignoreExc(() -> db.tickets.closeTicket(Instant.now(), channelId, "BOT: Channel deleted (not found)"));
		}

		Tag tag = db.ticketTags.getTagInfo(tagId);
		if (tag == null) {
			sendTicketError(event, "Unknown tag with ID: "+tagId);
			return;
		}

		User user = event.getUser();

		// Pings text
		StringBuffer mentions = new StringBuffer(user.getAsMention());
		List<String> supportRoles = tag.getSupportRoles();
		mentions.append("||");
		supportRoles.forEach(roleId -> mentions.append(" <@&%s>".formatted(roleId)));
		mentions.append("||");

		// Ticket message
		String message = Optional.ofNullable(tag.getMessage())
			.map(text -> text.replace("{username}", user.getName()).replace("{tag_username}", user.getAsMention()))
			.orElse("Ticket's controls");

		int ticketId = 1 + db.tickets.lastIdByTag(guildId, tagId);
		String ticketName = (tag.getTicketName()+ticketId).replace("{username}", user.getName());
		if (tag.getTagType() == 1) {
			// Thread ticket
			event.getChannel().asTextChannel().createThreadChannel(ticketName, true).setInvitable(false).queue(channel -> {
				db.tickets.addTicket(
					ticketId, user.getIdLong(), guildId, channel.getIdLong(), tagId,
					bot.getDBUtil().getTicketSettings(event.getGuild()).getTimeToReply()
				);

				bot.getTicketUtil().createTicket(event, channel, mentions.toString(), message);
			},
				_ -> sendTicketError(event, "Unable to create new thread in this channel"));
		} else {
			// Channel ticket
			Category category = Optional.ofNullable(tag.getLocation()).map(id -> event.getGuild().getCategoryById(id)).orElse(event.getChannel().asTextChannel().getParentCategory());
			if (category == null) {
				sendTicketError(event, "Target category not found, with ID: "+tag.getLocation());
				return;
			}

			ChannelAction<TextChannel> action = category.createTextChannel(ticketName).clearPermissionOverrides();
			for (String roleId : supportRoles) action = action.addRolePermissionOverride(Long.parseLong(roleId), EnumSet.of(Permission.VIEW_CHANNEL, Permission.MESSAGE_SEND), null);
			action.addPermissionOverride(event.getGuild().getPublicRole(), null, EnumSet.of(Permission.VIEW_CHANNEL))
				.addMemberPermissionOverride(user.getIdLong(), EnumSet.of(Permission.VIEW_CHANNEL, Permission.MESSAGE_SEND), null)
				.queue(channel -> {
					db.tickets.addTicket(
						ticketId, user.getIdLong(), guildId, channel.getIdLong(), tagId,
						bot.getDBUtil().getTicketSettings(event.getGuild()).getTimeToReply()
					);

					bot.getTicketUtil().createTicket(event, channel, mentions.toString(), message);
			},
					_ -> sendTicketError(event, "Unable to create new channel in target category, with ID: "+tag.getLocation()));
		}
	}

	private void sendTicketError(ButtonInteractionEvent event, String reason) {
		event.getHook().sendMessageEmbeds(bot.getEmbedUtil().getError(event, "bot.ticketing.listener.cant_create", reason)).setEphemeral(true).queue();
	}

	// Report
	private void buttonReportDelete(ButtonInteractionEvent event) {
		assert event.getGuild() != null && event.getMember() != null;
		event.getHook().editOriginalComponents().queue();

		String channelId = event.getComponentId().split(":")[1];
		String messageId = event.getComponentId().split(":")[2];

		TextChannel channel = event.getGuild().getTextChannelById(channelId);
		if (channel == null) {
			event.getHook().sendMessageEmbeds(bot.getEmbedUtil().getError(event, "misc.unknown", "Unknown channel")).queue();
			return;
		}
		channel.deleteMessageById(messageId).reason("Deleted by %s".formatted(event.getMember().getEffectiveName())).queue(_ ->
			event.getHook().sendMessageEmbeds(new EmbedBuilder().setColor(Constants.COLOR_SUCCESS)
				.setDescription(lu.getGuildText(event, "menus.report.deleted", event.getMember().getAsMention()))
				.build()
			).queue(),
		failure -> event.getHook().sendMessageEmbeds(bot.getEmbedUtil().getError(event, "misc.unknown", failure.getMessage())).queue()
		);
	}

	// Voice
	private void buttonVoiceLock(ButtonInteractionEvent event, VoiceChannel vc) {
		assert event.getGuild() != null;
		// Verify role
		Long verifyRoleId = bot.getDBUtil().getVerifySettings(event.getGuild()).getRoleId();

		try {
			if (verifyRoleId != null) {
				Role verifyRole = event.getGuild().getRoleById(verifyRoleId);
				if (verifyRole != null) {
					vc.upsertPermissionOverride(verifyRole).deny(Permission.VOICE_CONNECT).queue();
				}
			} else {
				vc.upsertPermissionOverride(event.getGuild().getPublicRole()).deny(Permission.VOICE_CONNECT).queue();
			}
		} catch (InsufficientPermissionException ex) {
			event.getHook().sendMessage(bot.getEmbedUtil().createPermError(event, ex.getPermission(), true)).setEphemeral(true).queue();
			return;
		}
		sendSuccess(event, "bot.voice.listener.panel.lock");
	}

	private void buttonVoiceUnlock(ButtonInteractionEvent event, VoiceChannel vc) {
		assert event.getGuild() != null;
		// Verify role
		Long verifyRoleId = bot.getDBUtil().getVerifySettings(event.getGuild()).getRoleId();

		try {
			if (verifyRoleId != null) {
				Role verifyRole = event.getGuild().getRoleById(verifyRoleId);
				if (verifyRole != null) {
					vc.upsertPermissionOverride(verifyRole).grant(Permission.VOICE_CONNECT).queue();
				}
			} else {
				vc.upsertPermissionOverride(event.getGuild().getPublicRole()).clear(Permission.VOICE_CONNECT).queue();
			}
		} catch (InsufficientPermissionException ex) {
			event.getHook().sendMessage(bot.getEmbedUtil().createPermError(event, ex.getPermission(), true)).setEphemeral(true).queue();
			return;
		}
		sendSuccess(event, "bot.voice.listener.panel.unlock");
	}

	private void buttonVoiceGhost(ButtonInteractionEvent event, VoiceChannel vc) {
		assert event.getGuild() != null;
		// Verify role
		Long verifyRoleId = bot.getDBUtil().getVerifySettings(event.getGuild()).getRoleId();

		try {
			if (verifyRoleId != null) {
				Role verifyRole = event.getGuild().getRoleById(verifyRoleId);
				if (verifyRole != null) {
					vc.upsertPermissionOverride(verifyRole).deny(Permission.VIEW_CHANNEL).queue();
				}
			} else {
				vc.upsertPermissionOverride(event.getGuild().getPublicRole()).deny(Permission.VIEW_CHANNEL).queue();
			}
		} catch (InsufficientPermissionException ex) {
			event.getHook().sendMessage(bot.getEmbedUtil().createPermError(event, ex.getPermission(), true)).setEphemeral(true).queue();
			return;
		}
		sendSuccess(event, "bot.voice.listener.panel.ghost");
	}

	private void buttonVoiceUnghost(ButtonInteractionEvent event, VoiceChannel vc) {
		assert event.getGuild() != null;
		// Verify role
		Long verifyRoleId = bot.getDBUtil().getVerifySettings(event.getGuild()).getRoleId();

		try {
			if (verifyRoleId != null) {
				Role verifyRole = event.getGuild().getRoleById(verifyRoleId);
				if (verifyRole != null) {
					vc.upsertPermissionOverride(verifyRole).grant(Permission.VIEW_CHANNEL).queue();
				}
			} else {
				vc.upsertPermissionOverride(event.getGuild().getPublicRole()).clear(Permission.VIEW_CHANNEL).queue();
			}
		} catch (InsufficientPermissionException ex) {
			event.getHook().sendMessage(bot.getEmbedUtil().createPermError(event, ex.getPermission(), true)).setEphemeral(true).queue();
			return;
		}
		sendSuccess(event, "bot.voice.listener.panel.unghost");
	}

	private void buttonVoicePermit(ButtonInteractionEvent event) {
		String text = lu.getGuildText(event, "bot.voice.listener.panel.permit_label");
		event.getHook()
			.sendMessage(text)
			.setComponents(ActionRow.of(EntitySelectMenu.create("voice:permit", EntitySelectMenu.SelectTarget.USER, EntitySelectMenu.SelectTarget.ROLE)
				.setMaxValues(10)
				.build()
			))
			.setEphemeral(true)
			.queue();
	}

	private void buttonVoiceReject(ButtonInteractionEvent event) {
		String text = lu.getGuildText(event, "bot.voice.listener.panel.reject_label");
		event.getHook()
			.sendMessage(text)
			.setComponents(ActionRow.of(EntitySelectMenu.create("voice:reject", EntitySelectMenu.SelectTarget.USER, EntitySelectMenu.SelectTarget.ROLE)
				.setMaxValues(10)
				.build()
			))
			.setEphemeral(true)
			.queue();
	}

	private void buttonVoicePerms(ButtonInteractionEvent event, VoiceChannel vc) {
		Guild guild = event.getGuild();
		assert guild != null && event.getMember() != null;
		List<MessageEmbed> embeds = new ArrayList<>();
		EmbedBuilder embedBuilder = bot.getEmbedUtil().getEmbed()
			.setTitle(lu.getGuildText(event, "bot.voice.listener.panel.perms.title", vc.getAsMention()))
			.setDescription(lu.getGuildText(event, "bot.voice.listener.panel.perms.field")+"\n\n");

		//@Everyone
		PermissionOverride publicOverride = vc.getPermissionOverride(guild.getPublicRole());

		String view = contains(publicOverride, Permission.VIEW_CHANNEL);
		String join = contains(publicOverride, Permission.VOICE_CONNECT);

		embedBuilder = embedBuilder.appendDescription("> %s | %s | `%s`\n\n%s\n".formatted(view, join, lu.getGuildText(event, "bot.voice.listener.panel.perms.everyone"),
			lu.getGuildText(event, "bot.voice.listener.panel.perms.roles")));

		//Roles
		List<PermissionOverride> overrides = new ArrayList<>(vc.getRolePermissionOverrides()); // cause given override list is immutable
		try {
			overrides.remove(vc.getPermissionOverride(Objects.requireNonNull(guild.getBotRole()))); // removes bot's role
			overrides.remove(vc.getPermissionOverride(guild.getPublicRole())); // removes @everyone role
		} catch (NullPointerException ex) {
			LOG.warn("PermsCmd null pointer at role override remove");
		}

		if (overrides.isEmpty()) {
			embedBuilder.appendDescription(lu.getGuildText(event, "bot.voice.listener.panel.perms.none") + "\n");
		} else {
			for (PermissionOverride ov : overrides) {
				view = contains(ov, Permission.VIEW_CHANNEL);
				join = contains(ov, Permission.VOICE_CONNECT);

				assert ov.getRole() != null;
				String t = "> %s | %s | `%s`\n".formatted(view, join, ov.getRole().getName());
				if (t.length() + embedBuilder.getDescriptionBuilder().length() >= 4000) {
					embeds.add(embedBuilder.build());
					embedBuilder = new EmbedBuilder();
				}
				embedBuilder = embedBuilder.appendDescription(t);
			}
		}

		//Members
		embedBuilder.appendDescription("\n%s\n".formatted(lu.getGuildText(event, "bot.voice.listener.panel.perms.members")));

		overrides = new ArrayList<>(vc.getMemberPermissionOverrides());
		try {
			overrides.remove(vc.getPermissionOverride(event.getMember())); // removes user
			overrides.remove(vc.getPermissionOverride(guild.getSelfMember())); // removes bot
		} catch (NullPointerException ex) {
			LOG.warn("PermsCmd null pointer at member override remove");
		}

		List<PermissionOverride> ovs = overrides;

		final EmbedBuilder finalEmbedBuilder = embedBuilder;
		guild.retrieveMembersByIds(false, overrides.stream().map(PermissionOverride::getId).toArray(String[]::new)).onSuccess(
			members -> {
				EmbedBuilder embedBuilder2 = new EmbedBuilder(finalEmbedBuilder);
				if (members.isEmpty()) {
					embedBuilder2.appendDescription(lu.getGuildText(event, "bot.voice.listener.panel.perms.none") + "\n");
				} else {
					for (PermissionOverride ov : ovs) {
						String view2 = contains(ov, Permission.VIEW_CHANNEL);
						String join2 = contains(ov, Permission.VOICE_CONNECT);

						String name = members.stream()
							.filter(m -> m.getId().equals(ov.getId()))
							.findFirst()
							.map(Member::getEffectiveName)
							.orElse("Unknown");
						String t = "> %s | %s | `%s`\n".formatted(view2, join2, name);
						if (t.length() + embedBuilder2.getDescriptionBuilder().length() >= 4000) {
							embeds.add(embedBuilder2.build());
							embedBuilder2 = new EmbedBuilder();
						}
						embedBuilder2 = embedBuilder2.appendDescription(t);
					}
				}

				event.getHook().sendMessageEmbeds(embeds).setEphemeral(true).queue();
			}
		);
	}

	private void buttonVoiceDelete(ButtonInteractionEvent event, VoiceChannel vc) {
		bot.getDBUtil().voice.remove(vc.getIdLong());

		vc.delete().reason("Channel owner request").queue();
		sendSuccess(event, "bot.voice.listener.panel.delete");
	}

	// Blacklist
	private void buttonBlacklist(ButtonInteractionEvent event) {
		assert event.getGuild() != null && event.getMember() != null;
		if (!bot.getCheckUtil().resolve(event.getMember()).has(AccessPermission.BLACKLIST_MANAGE)) {
			sendError(event, "errors.interaction.no_access");
			return;
		}

		String targetId = event.getComponentId().split(":")[1];
		CaseData caseData = db.cases.getMemberActive(Long.parseLong(targetId), event.getGuild().getIdLong(), CaseType.BAN);
		if (caseData == null || !caseData.getDuration().isZero()) {
			sendError(event, "bot.moderation.blacklist.expired");
			return;
		}

		long guildId = event.getGuild().getIdLong();
		List<Integer> groupIds = new ArrayList<>();
		groupIds.addAll(bot.getDBUtil().group.getOwnedGroups(guildId));
		groupIds.addAll(bot.getDBUtil().group.getManagedGroups(guildId));
		if (groupIds.isEmpty()) {
			sendError(event, "bot.moderation.blacklist.no_groups");
			return;
		}

		MessageEmbed embed = bot.getEmbedUtil().getEmbed()
			.setColor(Constants.COLOR_WARNING)
			.setDescription(lu.getGuildText(event, "bot.moderation.blacklist.title"))
			.build();
		StringSelectMenu menu = StringSelectMenu.create("groupId")
			.setPlaceholder(lu.getGuildText(event, "bot.moderation.blacklist.value"))
			.addOptions(groupIds.stream().map(groupId ->
				SelectOption.of(bot.getDBUtil().group.getName(groupId), groupId.toString()).withDescription("ID: "+groupId)
			).collect(Collectors.toList()))
			.setMaxValues(MAX_GROUP_SELECT)
			.build();

		event.getHook()
			.sendMessageEmbeds(embed)
			.setComponents(ActionRow.of(menu))
			.setEphemeral(true)
			.queue(msg -> waiter.waitForEvent(
				StringSelectInteractionEvent.class,
				e -> e.getMessageId().equals(msg.getId()),
				selectEvent -> {
					selectEvent.deferEdit().queue();
					List<Integer> selected = selectEvent.getValues().stream().map(Integer::parseInt).toList();

					event.getJDA().retrieveUserById(targetId).queue(target -> {
						selected.forEach(groupId -> {
							if (!db.serverBlacklist.inGroupUser(groupId, caseData.getTargetId())) {
								assert selectEvent.getGuild() != null;
								db.serverBlacklist.add(selectEvent.getGuild().getIdLong(), groupId, target.getIdLong(), caseData.getReason(), selectEvent.getUser().getIdLong());
							}

							bot.getHelper().runBan(groupId, event.getGuild(), target, caseData.getReason(), event.getUser());
						});

						// Log to master
						bot.getGuildLogger().mod.onBlacklistAdded(event.getUser(), target, selected);
						// Reply
						selectEvent.getHook().editOriginalEmbeds(bot.getEmbedUtil().getEmbed()
							.setColor(Constants.COLOR_SUCCESS)
							.setDescription(lu.getGuildText(event, "bot.moderation.blacklist.done"))
							.build())
						.setComponents().queue();
					},
					failure -> selectEvent.getHook().editOriginalEmbeds(
						bot.getEmbedUtil().getError(selectEvent, "bot.moderation.blacklist.no_user", failure.getMessage())
					).setComponents().queue());
				},
				20,
				TimeUnit.SECONDS,
				() -> msg.editMessageComponents(ActionRow.of(menu.asDisabled())).queue()
			));
	}

	private void buttonSyncBan(ButtonInteractionEvent event) {
		assert event.getGuild() != null && event.getMember() != null;
		if (!bot.getCheckUtil().resolve(event.getMember()).has(AccessPermission.BLACKLIST_MANAGE)) {
			sendError(event, "errors.interaction.no_access");
			return;
		}

		String targetId = event.getComponentId().split(":")[1];
		CaseData caseData = db.cases.getMemberActive(Long.parseLong(targetId), event.getGuild().getIdLong(), CaseType.BAN);
		if (caseData == null || !caseData.getDuration().isZero()) {
			sendError(event, "bot.moderation.sync.expired");
			return;
		}

		long guildId = event.getGuild().getIdLong();
		List<Integer> groupIds = new ArrayList<>();
		groupIds.addAll(bot.getDBUtil().group.getOwnedGroups(guildId));
		groupIds.addAll(bot.getDBUtil().group.getManagedGroups(guildId));
		if (groupIds.isEmpty()) {
			sendError(event, "bot.moderation.sync.no_groups");
			return;
		}

		MessageEmbed embed = bot.getEmbedUtil().getEmbed()
			.setColor(Constants.COLOR_WARNING)
			.setDescription(lu.getGuildText(event, "bot.moderation.sync.ban.title"))
			.build();
		StringSelectMenu menu = StringSelectMenu.create("groupId")
			.setPlaceholder(lu.getGuildText(event, "bot.moderation.sync.select"))
			.addOptions(groupIds.stream().map(groupId ->
				SelectOption.of(bot.getDBUtil().group.getName(groupId), groupId.toString()).withDescription("ID: "+groupId)
			).collect(Collectors.toList()))
			.setMaxValues(MAX_GROUP_SELECT)
			.build();

		event.getHook()
			.sendMessageEmbeds(embed)
			.setComponents(ActionRow.of(menu))
			.setEphemeral(true)
			.queue(msg -> waiter.waitForEvent(
				StringSelectInteractionEvent.class,
				e -> e.getMessageId().equals(msg.getId()),
				selectEvent -> {
					selectEvent.deferEdit().queue();
					List<Integer> selected = selectEvent.getValues().stream().map(Integer::parseInt).toList();

					event.getJDA().retrieveUserById(targetId).queue(target -> {
						selected.forEach(groupId -> bot.getHelper().runBan(groupId, event.getGuild(), target, caseData.getReason(), event.getUser()));
						// Reply
						selectEvent.getHook().editOriginalEmbeds(
							bot.getEmbedUtil().getEmbed()
								.setColor(Constants.COLOR_SUCCESS)
								.setDescription(lu.getGuildText(event, "bot.moderation.sync.ban.done"))
								.build())
							.setComponents().queue();
					},
					failure -> selectEvent.getHook().editOriginalEmbeds(
						bot.getEmbedUtil().getError(selectEvent, "bot.moderation.sync.no_user", failure.getMessage())
					).setComponents().queue());
				},
				20,
				TimeUnit.SECONDS,
				() -> msg.editMessageComponents(ActionRow.of(menu.asDisabled())).queue()
			));
	}

	private void buttonSyncUnban(ButtonInteractionEvent event) {
		assert event.getGuild() != null && event.getMember() != null;
		if (!bot.getCheckUtil().resolve(event.getMember()).has(AccessPermission.BLACKLIST_MANAGE)) {
			sendError(event, "errors.interaction.no_access");
			return;
		}

		long guildId = event.getGuild().getIdLong();
		List<Integer> groupIds = new ArrayList<>();
		groupIds.addAll(bot.getDBUtil().group.getOwnedGroups(guildId));
		groupIds.addAll(bot.getDBUtil().group.getManagedGroups(guildId));
		if (groupIds.isEmpty()) {
			sendError(event, "bot.moderation.sync.no_groups");
			return;
		}

		MessageEmbed embed = bot.getEmbedUtil().getEmbed()
			.setColor(Constants.COLOR_WARNING)
			.setDescription(lu.getGuildText(event, "bot.moderation.sync.unban.title"))
			.build();
		StringSelectMenu menu = StringSelectMenu.create("groupId")
			.setPlaceholder(lu.getGuildText(event, "bot.moderation.sync.select"))
			.addOptions(groupIds.stream().map(groupId ->
				SelectOption.of(bot.getDBUtil().group.getName(groupId), groupId.toString()).withDescription("ID: "+groupId)
			).collect(Collectors.toList()))
			.setMaxValues(MAX_GROUP_SELECT)
			.build();

		event.getHook()
			.sendMessageEmbeds(embed)
			.setComponents(ActionRow.of(menu))
			.setEphemeral(true)
			.queue(msg -> waiter.waitForEvent(
				StringSelectInteractionEvent.class,
				e -> e.getMessageId().equals(msg.getId()),
				selectEvent -> {
					selectEvent.deferEdit().queue();
					List<Integer> selected = selectEvent.getValues().stream().map(Integer::parseInt).toList();

					event.getJDA().retrieveUserById(event.getComponentId().split(":")[1]).queue(target -> {
						selected.forEach(groupId -> {
							if (db.serverBlacklist.inGroupUser(groupId, target.getIdLong())) {
								ignoreExc(() -> db.serverBlacklist.removeUser(groupId, target.getIdLong()));
								bot.getGuildLogger().mod.onBlacklistRemoved(event.getUser(), target, groupId);
							}

							bot.getHelper().runUnban(groupId, event.getGuild(), target, "Sync group unban, by "+event.getUser().getName(), event.getUser());
						});

						// Reply
						selectEvent.getHook().editOriginalEmbeds(bot.getEmbedUtil().getEmbed()
							.setColor(Constants.COLOR_SUCCESS)
							.setDescription(lu.getGuildText(event, "bot.moderation.sync.unban.done"))
							.build())
						.setComponents().queue();
					},
					failure -> selectEvent.getHook().editOriginalEmbeds(
						bot.getEmbedUtil().getError(selectEvent, "bot.moderation.sync.no_user", failure.getMessage())
					).setComponents().queue());
				},
				20,
				TimeUnit.SECONDS,
				() -> msg.editMessageComponents(ActionRow.of(menu.asDisabled())).queue()
			));
	}

	private void buttonSyncKick(ButtonInteractionEvent event) {
		assert event.getGuild() != null && event.getMember() != null;
		if (!bot.getCheckUtil().resolve(event.getMember()).has(AccessPermission.BLACKLIST_MANAGE)) {
			sendError(event, "errors.interaction.no_access");
			return;
		}

		String targetId = event.getComponentId().split(":")[1];
		CaseData caseData = db.cases.getMemberActive(Long.parseLong(targetId), event.getGuild().getIdLong(), CaseType.BAN);
		if (caseData == null || !caseData.getDuration().isZero()) {
			sendError(event, "bot.moderation.sync.expired");
			return;
		}

		long guildId = event.getGuild().getIdLong();
		List<Integer> groupIds = new ArrayList<>();
		groupIds.addAll(bot.getDBUtil().group.getOwnedGroups(guildId));
		groupIds.addAll(bot.getDBUtil().group.getManagedGroups(guildId));
		if (groupIds.isEmpty()) {
			sendError(event, "bot.moderation.sync.no_groups");
			return;
		}

		MessageEmbed embed = bot.getEmbedUtil().getEmbed()
			.setColor(Constants.COLOR_WARNING)
			.setDescription(lu.getGuildText(event, "bot.moderation.sync.kick.title"))
			.build();
		StringSelectMenu menu = StringSelectMenu.create("groupId")
			.setPlaceholder(lu.getGuildText(event, "bot.moderation.sync.select"))
			.addOptions(groupIds.stream().map(groupId ->
				SelectOption.of(bot.getDBUtil().group.getName(groupId), groupId.toString()).withDescription("ID: "+groupId)
			).collect(Collectors.toList()))
			.setMaxValues(MAX_GROUP_SELECT)
			.build();

		event.getHook()
			.sendMessageEmbeds(embed)
			.setComponents(ActionRow.of(menu))
			.setEphemeral(true)
			.queue(msg -> waiter.waitForEvent(
				StringSelectInteractionEvent.class,
				e -> e.getMessageId().equals(msg.getId()),
				selectEvent -> {
					selectEvent.deferEdit().queue();
					List<Integer> selected = selectEvent.getValues().stream().map(Integer::parseInt).toList();

					event.getJDA().retrieveUserById(targetId).queue(target -> {
						selected.forEach(groupId -> bot.getHelper().runKick(groupId, event.getGuild(), target, caseData.getReason(), event.getUser()));
						// Reply
						selectEvent.getHook().editOriginalEmbeds(
							bot.getEmbedUtil().getEmbed()
								.setColor(Constants.COLOR_SUCCESS)
								.setDescription(lu.getGuildText(event, "bot.moderation.sync.kick.done"))
								.build())
							.setComponents().queue();
					},
					failure -> selectEvent.getHook().editOriginalEmbeds(
						bot.getEmbedUtil().getError(selectEvent, "bot.moderation.sync.no_user", failure.getMessage())
					).setComponents().queue());
				},
				20,
				TimeUnit.SECONDS,
				() -> msg.editMessageComponents(ActionRow.of(menu.asDisabled())).queue()
			));
	}

	// Strikes
	private void buttonShowStrikes(ButtonInteractionEvent event) {
		long guildId = Long.parseLong(event.getComponentId().split(":")[1]);
		Guild guild = event.getJDA().getGuildById(guildId);
		if (guild == null) {
			sendError(event, "errors.error", "Server not found.");
			return;
		}
		Pair<Integer, Integer> strikeData = bot.getDBUtil().strikes.getDataCountAndDate(guildId, event.getUser().getIdLong());
		if (strikeData == null) {
			event.getHook().sendMessageEmbeds(bot.getEmbedUtil().getEmbed()
				.setDescription(lu.getText(event, "bot.moderation.no_strikes", guild.getName()))
				.build()).queue();
			return;
		}

		Instant time = Instant.ofEpochSecond(strikeData.getRight());
		event.getHook().sendMessageEmbeds(bot.getEmbedUtil().getEmbed()
			.setDescription(lu.getText(event, "bot.moderation.strikes_embed", strikeData.getLeft(), TimeFormat.RELATIVE.atInstant(time)))
			.build()
		).setEphemeral(true).queue();
	}

	// Roles modify
	private void buttonModifyConfirm(ButtonInteractionEvent event) {
		assert event.getGuild() != null && event.getMember() != null;
		long guildId = event.getGuild().getIdLong();
		long userId = event.getUser().getIdLong();
		long targetId = Long.parseLong(event.getComponentId().split(":")[2]);

		// If expired don't allow to modify embed
		if (db.modifyRole.isExpired(guildId, userId, targetId)) {
			event.getHook().editOriginalEmbeds(bot.getEmbedUtil().getError(event, "bot.roles.role.modify.expired"))
				.setComponents().queue();
			return;
		}

		event.getGuild().retrieveMemberById(targetId).queue(target -> {
			List<Long> addIds = new ArrayList<>();
			List<Long> removeIds = new ArrayList<>();
			// Retrieve selected roles
			for (String line : db.modifyRole.getRoles(guildId, userId, targetId).split(":")) {
				if (line.isBlank()) continue;
				String[] roleIds = line.split(";");
				for (String roleId : roleIds) {
					// Check if first char is '+' add or '-' remove
					if (roleId.charAt(0) == '+') addIds.add(Long.parseLong(roleId.substring(1)));
					else removeIds.add(Long.parseLong(roleId.substring(1)));
				}
			}
			if (addIds.isEmpty() && removeIds.isEmpty()) {
				sendError(event, "bot.roles.role.modify.no_change");
				return;
			}

			Guild guild = target.getGuild();
			List<Role> finalRoles = new ArrayList<>(target.getRoles());
			finalRoles.addAll(addIds.stream().map(guild::getRoleById).toList());
			finalRoles.removeAll(removeIds.stream().map(guild::getRoleById).toList());

			guild.modifyMemberRoles(target, finalRoles).reason("by "+event.getMember().getEffectiveName()).queue(_ -> {
				// Remove from DB
				db.modifyRole.remove(guildId, userId, targetId);
				// text
				StringBuilder builder = new StringBuilder();
				if (!addIds.isEmpty()) builder.append("\n**Added**: ")
					.append(addIds.stream().map(String::valueOf).collect(Collectors.joining(">, <@&", "<@&", ">")));
				if (!removeIds.isEmpty()) builder.append("\n**Removed**: ")
					.append(removeIds.stream().map(String::valueOf).collect(Collectors.joining(">, <@&", "<@&", ">")));
				String rolesString = builder.toString();
				// Log
				bot.getGuildLogger().role.onRolesModified(guild, event.getUser(), target.getUser(), rolesString);
				// Send reply
				event.getHook().editOriginalEmbeds(bot.getEmbedUtil().getEmbed(Constants.COLOR_SUCCESS)
					.setDescription(lu.getGuildText(event, "bot.roles.role.modify.done", target.getAsMention(), rolesString))
					.build()
				).setComponents().queue();
			}, _ -> event.getHook().editOriginalEmbeds(bot.getEmbedUtil().getError(event, "errors.error", "Unable to modify roles, User ID: "+targetId))
				.setComponents().queue()
			);
		}, _ -> event.getHook().editOriginalEmbeds(bot.getEmbedUtil().getError(event, "errors.error", "Member not found, ID: "+targetId))
			.setComponents().queue()
		);
	}


	@Override
	public void onModalInteraction(@NotNull ModalInteractionEvent event) {
		// Check if blacklisted
		if (bot.getBlacklist().isBlacklisted(event)) return;

		if (event.getModalId().startsWith("role_temp") || event.getModalId().startsWith("cr")) {
			event.deferEdit().queue();
			String[] modalId = event.getModalId().split(":");

			switch (modalId[0]) {
				case "role_temp" -> modalTempRole(event, castLong(modalId[1]));
				case "cr" -> {
					switch (modalId[1]) {
						case "request" -> modalCustomRoleRequest(event);
						case "edit_request" -> modalCustomRoleEditRequest(event);
						case "modify" -> modalCustomRoleModify(event, Long.parseLong(modalId[2]));
						case "reject" -> modalCustomRoleReject(event, Long.parseLong(modalId[2]));
					}
				}
			}
		}
	}

	private void modalTempRole(ModalInteractionEvent event, long channelId) {
		// Check if ticket is open
		if (db.tickets.isClosed(channelId)) {
			// Ignore
			return;
		}
		final Guild guild = event.getGuild();
		assert guild != null && event.getMember() != null;
		final long userId = db.tickets.getUserId(channelId);

		// Get roles and tempRoles
		List<Role> roles = new ArrayList<>();
		db.tickets.getRoleIds(channelId).forEach(v -> {
			long roleId = castLong(v.charAt(0) == 't' ? v.substring(1) : v);
			Role role = guild.getRoleById(roleId);
			if (role != null) roles.add(role);
		});
		if (roles.isEmpty()) {
			event.getHook().sendMessageEmbeds(bot.getEmbedUtil().getEmbed(event)
				.setDescription(lu.getGuildText(event, "bot.ticketing.listener.role_none"))
				.setColor(Constants.COLOR_WARNING)
				.build()
			).setEphemeral(true).queue();
			return;
		}

		// Get member add set roles
		guild.retrieveMemberById(userId).queue(member -> {
			// Add role durations to list
			Map<Long, Duration> roleDurations = new HashMap<>();
			for (ModalMapping map : event.getValues()) {
				final long roleId = castLong(map.getCustomId());
				final String value = map.getAsString();
				// Check duration
				final Duration duration;
				try {
					duration = TimeUtil.stringToDuration(value, false);
				} catch (FormatterException ex) {
					sendError(event, ex.getPath());
					return;
				}
				// Add to temp only if duration not zero and between 10 minutes and MAX_DAYS days
				if (!duration.isZero()) {
					if (duration.toMinutes() < 10 || duration.toDays() > TempRoleCmd.MAX_DAYS) {
						sendError(event, "bot.ticketing.listener.time_limit", "Received: "+duration);
						return;
					}
					roleDurations.put(roleId, duration);
				}
			}

			final int ticketId = db.tickets.getTicketId(channelId);
			// Modify roles
			guild.modifyMemberRoles(member, roles, null)
				.reason("Request role-" + ticketId + " approved by " + event.getMember().getEffectiveName())
				.queue(_ -> {
					// Set claimed
					db.tickets.setClaimed(channelId, event.getMember().getIdLong());
					// Add tempRoles to db and log them
					roleDurations.forEach((id, duration) -> {
						ignoreExc(() ->
							bot.getDBUtil().tempRoles.add(guild.getIdLong(), id, userId, false, Instant.now().plus(duration))
						);
						// Log
						bot.getGuildLogger().role.onTempRoleAdded(guild, event.getUser(), member.getUser(), id, duration, false);
					});
					// Log approval
					bot.getGuildLogger().role.onApproved(member, event.getMember(), guild, roles, ticketId);
					// Reply and send DM to the target member
					event.getHook().sendMessageEmbeds(bot.getEmbedUtil().getEmbed(event)
						.setDescription(lu.getGuildText(event, "bot.ticketing.listener.role_added"))
						.setColor(Constants.COLOR_SUCCESS)
						.build()
					).queue(null, new ErrorHandler().ignore(ErrorResponse.UNKNOWN_WEBHOOK));
					member.getUser().openPrivateChannel().queue(dm -> dm.sendMessage(lu.getGuildText(event, "bot.ticketing.listener.role_dm")
						.replace("{roles}", roles.stream().map(Role::getName).collect(Collectors.joining(" | ")))
						.replace("{server}", guild.getName())
						.replace("{id}", String.valueOf(ticketId))
						.replace("{mod}", event.getMember().getEffectiveName())
					).queue(null, new ErrorHandler().ignore(ErrorResponse.CANNOT_SEND_TO_USER)));
				}, failure -> sendError(event, "bot.ticketing.listener.role_failed", failure.getMessage()));
		}, failure -> sendError(event, "bot.ticketing.listener.no_member", failure.getMessage()));
	}

	@Override
	public void onStringSelectInteraction(@NotNull StringSelectInteractionEvent event) {
		// Check if blacklisted
		if (bot.getBlacklist().isBlacklisted(event)) return;
		
		String menuId = event.getComponentId();

		if (menuId.startsWith("menu:role_row")) {
			event.deferEdit().queue(null, new ErrorHandler().ignore(ErrorResponse.UNKNOWN_INTERACTION));

			List<Field> fields = event.getMessage().getEmbeds().getFirst().getFields();
			List<Long> roleIds = MessageUtil.getRoleIdsFromString(fields.isEmpty() ? "" : Objects.requireNonNull(fields.getFirst().getValue()));
			event.getSelectedOptions().forEach(option -> {
				Long value = castLong(option.getValue());
				if (!roleIds.contains(value)) roleIds.add(value);
			});

			MessageEmbed embed = new EmbedBuilder(event.getMessage().getEmbeds().getFirst())
				.clearFields()
				.addField(lu.getGuildText(event, "bot.ticketing.listener.request_selected"), selectedRolesString(roleIds, lu.getLocale(event)), false)
				.build();
			event.getHook().editOriginalEmbeds(embed).queue();
		} else if (menuId.startsWith("role:manage-select")) {
			listModifySelect(event);
		}
	}

	private final Pattern splitPattern = Pattern.compile(":");

	// Roles modify
	private void listModifySelect(StringSelectInteractionEvent event) {
		assert event.getGuild() != null;
		event.deferEdit().queue();
		try {
			long guildId = event.getGuild().getIdLong();
			long userId = event.getUser().getIdLong();
			long targetId = Long.parseLong(event.getComponentId().split(":")[3]);

			// If expired don't allow to modify
			if (db.modifyRole.isExpired(guildId, userId, targetId)) {
				event.getHook().editOriginalEmbeds(bot.getEmbedUtil().getError(event, "bot.roles.role.modify.expired"))
					.setComponents().queue();
				return;
			}

			List<String> changes = new ArrayList<>();

			List<SelectOption> defaultOptions = event.getSelectMenu().getOptions().stream().filter(SelectOption::isDefault).toList();
			List<SelectOption> selectedOptions = event.getSelectedOptions();
			// if default is not in selected - role is removed
			for (SelectOption option : defaultOptions) {
				if (!selectedOptions.contains(option)) changes.add("-"+option.getValue());
			}
			// if selected is not in default - role is added
			for (SelectOption option : selectedOptions) {
				if (!defaultOptions.contains(option)) changes.add("+"+option.getValue());
			}

			String newValue = String.join(";", changes);

			// "1:2:3:4"
			// each section stores changes for each menu
			int menuId = Integer.parseInt(event.getComponentId().split(":")[2]);
			String[] data = splitPattern.split(db.modifyRole.getRoles(guildId, userId, targetId), 4);
			if (data.length != 4) data = new String[]{"", "", "", ""};
			data[menuId-1] = newValue;
			db.modifyRole.update(guildId, userId, targetId, String.join(":", data), Instant.now().plus(2, ChronoUnit.MINUTES));
		} catch(Throwable t) {
			// Log throwable and try to respond to the user with the error
			// Thrown errors are not user's error, but code's fault as such things should be caught earlier and replied properly
			LOG.error("Role modify Exception", t);
			bot.getEmbedUtil().sendUnknownError(event.getHook(), lu.getLocale(event), t.getMessage());
		}
	}

	@Override
	public void onEntitySelectInteraction(@NotNull EntitySelectInteractionEvent event) {
		// Check if blacklisted
		if (bot.getBlacklist().isBlacklisted(event)) return;

		String menuId = event.getComponentId();
		if (menuId.startsWith("voice")) {
			event.deferEdit().queue();

			Member author = event.getMember();
			assert author != null;
			if (author.getVoiceState() == null || !author.getVoiceState().inAudioChannel()) {
				sendError(event, "bot.voice.listener.not_in_voice");
				return;
			}
			Long channelId = db.voice.getChannel(author.getIdLong());
			if (channelId == null) {
				sendError(event, "errors.no_channel");
				return;
			}
			Guild guild = event.getGuild();
			assert guild != null;
			VoiceChannel vc = guild.getVoiceChannelById(channelId);
			if (vc == null) return;
			String action = menuId.split(":")[1];
			if (action.equals("permit") || action.equals("reject")) {
				Mentions mentions = event.getMentions();

				List<Member> members = mentions.getMembers();
				List<Role> roles = mentions.getRoles();
				if (members.isEmpty() && roles.isEmpty()) {
					return;
				}
				if (members.contains(author) || members.contains(guild.getSelfMember())) {
					event.getHook().editOriginalEmbeds(bot.getEmbedUtil().getError(event, "bot.voice.listener.panel.not_self"))
						.setContent("").setComponents().queue();
					return;
				}

				List<String> mentionStrings = new ArrayList<>();
				String text;

				VoiceChannelManager manager = vc.getManager();

				if (action.equals("permit")) {
					for (Member member : members) {
						manager = manager.putPermissionOverride(member, EnumSet.of(Permission.VOICE_CONNECT, Permission.VIEW_CHANNEL), null);
						mentionStrings.add(member.getEffectiveName());
					}

					for (Role role : roles) {
						EnumSet<Permission> rolePerms = EnumSet.copyOf(role.getPermissions());
						rolePerms.retainAll(adminPerms);
						if (rolePerms.isEmpty()) {
							manager = manager.putPermissionOverride(role, EnumSet.of(Permission.VOICE_CONNECT, Permission.VIEW_CHANNEL), null);
							mentionStrings.add(role.getName());
						}
					}

					text = lu.getTargetText(event, "bot.voice.listener.panel.permit_done", mentionStrings);
				} else {
					for (Member member : members) {
						manager = manager.putPermissionOverride(member, null, EnumSet.of(Permission.VOICE_CONNECT, Permission.VIEW_CHANNEL));
						if (vc.getMembers().contains(member)) {
							guild.kickVoiceMember(member).queue();
						}
						mentionStrings.add(member.getEffectiveName());
					}

					for (Role role : roles) {
						EnumSet<Permission> rolePerms = EnumSet.copyOf(role.getPermissions());
						rolePerms.retainAll(adminPerms);
						if (rolePerms.isEmpty()) {
							manager = manager.putPermissionOverride(role, null, EnumSet.of(Permission.VOICE_CONNECT, Permission.VIEW_CHANNEL));
							mentionStrings.add(role.getName());
						}
					}

					text = lu.getTargetText(event, "bot.voice.listener.panel.reject_done", mentionStrings);
				}

				final MessageEmbed embed = bot.getEmbedUtil().getEmbed(event).setDescription(text).build();
				manager.queue(_ -> event.getHook()
						.editOriginalEmbeds(embed)
						.setContent("")
						.setComponents()
						.queue(),
					_ -> event.getHook()
						.editOriginal(MessageEditData.fromCreateData(bot.getEmbedUtil().createPermError(event, Permission.MANAGE_PERMISSIONS, true)))
						.setContent("")
						.setComponents()
						.queue()
				);
			}
		}
	}


	// TOOLS
	private String selectedRolesString(List<Long> roleIds, DiscordLocale locale) {
		if (roleIds.isEmpty()) return "None";
		return roleIds.stream()
			.map(id -> (id.equals(0L) ? "+"+lu.getLocalized(locale, "bot.ticketing.embeds.other") : "<@&%s>".formatted(id)))
			.collect(Collectors.joining(", "));
	}

	private String contains(PermissionOverride override, Permission perm) {
		if (override != null) {
			if (override.getAllowed().contains(perm))
				return Emote.CHECK_C.getEmote();
			else if (override.getDenied().contains(perm))
				return Emote.CROSS_C.getEmote();
		}
		return Emote.NONE.getEmote();
	}

	// Cooldown objects
	private enum Cooldown {
		BUTTON_VERIFY(10, CooldownScope.USER),
		BUTTON_ROLE_SHOW(20, CooldownScope.USER),
		BUTTON_ROLE_OTHER(4, CooldownScope.USER),
		BUTTON_ROLE_CLEAR(4, CooldownScope.USER),
		BUTTON_ROLE_REMOVE(10, CooldownScope.USER),
		BUTTON_ROLE_TOGGLE(2, CooldownScope.USER),
		BUTTON_ROLE_TICKET(30, CooldownScope.USER),
		BUTTON_ROLE_APPROVE(10, CooldownScope.CHANNEL),
		BUTTON_TICKET_CLOSE(10, CooldownScope.CHANNEL),
		BUTTON_TICKET_CANCEL(4, CooldownScope.CHANNEL),
		BUTTON_TICKET_CLAIM(20, CooldownScope.USER_CHANNEL),
		BUTTON_TICKET_UNCLAIM(20, CooldownScope.USER_CHANNEL),
		BUTTON_TICKET_CREATE(30, CooldownScope.USER),
		BUTTON_REPORT_DELETE(4, CooldownScope.GUILD),
		BUTTON_SHOW_STRIKES(30, CooldownScope.USER),
		BUTTON_SYNC_ACTION(10, CooldownScope.CHANNEL),
		BUTTON_MODIFY_CONFIRM(10, CooldownScope.USER),
		BUTTON_CUSTOM_ROLE_REQUEST(30, CooldownScope.USER);

		private final int time;
		private final CooldownScope scope;

		Cooldown(int time, @NotNull CooldownScope scope) {
			this.time = time;
			this.scope = scope;
		}

		public int getTime() {
			return this.time;
		}

		public CooldownScope getScope() {
			return this.scope;
		}
	}

	private String getCooldownKey(Cooldown cooldown, GenericInteractionCreateEvent event) {
		String name = cooldown.toString();
		CooldownScope cooldownScope = cooldown.getScope();

		final long userId = event.getUser().getIdLong();
		final long guildId = Optional.ofNullable(event.getGuild()).map(ISnowflake::getIdLong).orElse(0L);
		final long channelId = Optional.ofNullable(event.getChannel()).map(ISnowflake::getIdLong).orElse(0L);

		return switch (cooldown.getScope()) {
			case USER -> cooldownScope.genKey(name, userId);
			case USER_GUILD -> guildId > 0 ? cooldownScope.genKey(name, userId, guildId) :
				CooldownScope.USER_CHANNEL.genKey(name, userId, channelId);
			case USER_CHANNEL -> cooldownScope.genKey(name, userId, channelId);
			case GUILD -> guildId > 0 ? cooldownScope.genKey(name, guildId) :
				CooldownScope.CHANNEL.genKey(name, channelId);
			case CHANNEL -> cooldownScope.genKey(name, channelId);
			case SHARD -> cooldownScope.genKey(name, event.getJDA().getShardInfo().getShardId());
			case USER_SHARD -> cooldownScope.genKey(name, userId, event.getJDA().getShardInfo().getShardId());
			case GLOBAL -> cooldownScope.genKey(name, 0);
		};
	}

	@NotNull
	private String getCooldownErrorString(Cooldown cooldown, GenericInteractionCreateEvent event, int remaining) {
		CooldownScope scope = cooldown.getScope();
		String descriptor;
		if (scope.equals(CooldownScope.USER_GUILD) && event.getGuild()==null)
			descriptor = lu.getLocalized(event.getUserLocale(), CooldownScope.USER_CHANNEL.getErrorPath());
		else if (scope.equals(CooldownScope.GUILD) && event.getGuild()==null)
			descriptor = lu.getLocalized(event.getUserLocale(), CooldownScope.CHANNEL.getErrorPath());
		else if (!scope.equals(CooldownScope.USER))
			descriptor = lu.getLocalized(event.getUserLocale(), scope.getErrorPath());
		else
			descriptor = null;

		return lu.getLocalized(event.getUserLocale(), "errors.cooldown.cooldown_button")
			.formatted(descriptor == null ? "" : descriptor, TimeFormat.RELATIVE.after(remaining));
	}


	// ---- Custom Role interactions ----

	private void buttonCustomRoleRequest(ButtonInteractionEvent event) {
		assert event.getGuild() != null && event.getMember() != null;
		long guildId = event.getGuild().getIdLong();
		long userId = event.getMember().getIdLong();

		CustomRoleSettings settings = db.customRoleSettings.getSettings(guildId);
		if (!settings.isConfigured()) {
			sendErrorLive(event, "bot.roles.custom_role.errors.no_setup");
			return;
		}
		if (db.customRoles.getByOwner(userId, guildId) != null) {
			sendErrorLive(event, "bot.roles.custom_role.errors.has_role");
			return;
		}
		if (db.customRoleRequests.getPendingByUser(userId, guildId) != null) {
			sendErrorLive(event, "bot.roles.custom_role.errors.has_pending");
			return;
		}
		if (!db.customRoleAccess.hasAccess(userId, guildId)) {
			// Fallback: active server boost
			// Auto-grant temporary access
			if (settings.isNitroAutoGrant() && event.getMember().isBoosting()) {
				Instant expires = Instant.now().plus(settings.getNitroExpireDays(), ChronoUnit.DAYS);
				try {
					db.customRoleAccess.grant(userId, guildId, 0L, expires.getEpochSecond(), true);
				} catch (SQLException e) {
					LOG.warn("Failed to grant nitro custom role access for {} @ {}", userId, guildId, e);
					sendErrorLive(event, "errors.database");
					return;
				}
			} else {
				sendErrorLive(event, "bot.roles.custom_role.errors.no_access");
				return;
			}
		}

		var locale = event.getUserLocale();
		var modal = net.dv8tion.jda.api.modals.Modal.create("cr:request",
				lu.getLocalized(locale, "bot.roles.custom_role.modal.request.title"))
			.addComponents(
				Label.of(lu.getLocalized(locale, "bot.roles.custom_role.modal.request.name"),
					TextInput.create("name", TextInputStyle.SHORT).setMaxLength(100).setRequired(true).build()),
				Label.of(lu.getLocalized(locale, "bot.roles.custom_role.modal.request.color_mode"),
					net.dv8tion.jda.api.components.radiogroup.RadioGroup.create("colorMode")
						.addOption("solid",
							lu.getLocalized(locale, "bot.roles.custom_role.modal.request.color_solid"),
							lu.getLocalized(locale, "bot.roles.custom_role.modal.request.color_solid_desc"),
							true)
						.addOption("gradient",
							lu.getLocalized(locale, "bot.roles.custom_role.modal.request.color_gradient"),
							lu.getLocalized(locale, "bot.roles.custom_role.modal.request.color_gradient_desc"))
						.setRequired(true).build()),
				Label.of(lu.getLocalized(locale, "bot.roles.custom_role.modal.request.color1"),
					TextInput.create("color1", TextInputStyle.SHORT).setMaxLength(7).setPlaceholder("#RRGGBB").setRequired(true).build()),
				Label.of(lu.getLocalized(locale, "bot.roles.custom_role.modal.request.color2"),
					TextInput.create("color2", TextInputStyle.SHORT).setMaxLength(7).setPlaceholder("#RRGGBB").setRequired(false).build()),
				Label.of(lu.getLocalized(locale, "bot.roles.custom_role.modal.request.icon"),
					TextInput.create("icon", TextInputStyle.SHORT).setMaxLength(512).setRequired(false).build())
			)
			.build();
		event.replyModal(modal).queue(null, new ErrorHandler().ignore(ErrorResponse.UNKNOWN_INTERACTION));
	}

	private void modalCustomRoleRequest(ModalInteractionEvent event) {
		assert event.getGuild() != null && event.getMember() != null;
		long guildId = event.getGuild().getIdLong();
		long userId = event.getMember().getIdLong();

		CustomRoleSettings settings = db.customRoleSettings.getSettings(guildId);
		if (!settings.isConfigured()) {
			sendError(event, "bot.roles.custom_role.errors.no_setup");
			return;
		}
		if (!db.customRoleAccess.hasAccess(userId, guildId)) {
			sendError(event, "bot.roles.custom_role.errors.no_access");
			return;
		}
		if (db.customRoles.getByOwner(userId, guildId) != null) {
			sendError(event, "bot.roles.custom_role.errors.has_role");
			return;
		}
		if (db.customRoleRequests.getPendingByUser(userId, guildId) != null) {
			sendError(event, "bot.roles.custom_role.errors.has_pending");
			return;
		}

		String name      = Optional.ofNullable(event.getValue("name")).map(ModalMapping::getAsOptionalString).map(String::strip).orElse("");
		String colorMode = Optional.ofNullable(event.getValue("colorMode")).map(ModalMapping::getAsOptionalString).map(String::strip).orElse("solid");
		String color1    = Optional.ofNullable(event.getValue("color1")).map(ModalMapping::getAsOptionalString).map(String::strip).orElse("");
		String color2    = Optional.ofNullable(event.getValue("color2")).map(ModalMapping::getAsOptionalString).map(String::strip).orElse(null);
		String icon      = Optional.ofNullable(event.getValue("icon")).map(ModalMapping::getAsOptionalString).map(String::strip).orElse(null);
		if ("solid".equals(colorMode)) color2 = null;
		if (color2 != null && color2.isBlank()) color2 = null;
		if (icon   != null && icon.isBlank())   icon   = null;

		if (!CustomRoleCmd.isValidHex(color1)) {
			sendError(event, "bot.roles.custom_role.errors.invalid_hex", "Primary: " + color1);
			return;
		}
		if (color2 != null && !CustomRoleCmd.isValidHex(color2)) {
			sendError(event, "bot.roles.custom_role.errors.invalid_hex", "Secondary: " + color2);
			return;
		}
		if (icon != null && !event.getGuild().getFeatures().contains("ROLE_ICONS")) {
			sendError(event, "bot.roles.custom_role.errors.level_required");
			return;
		}

		color1 = CustomRoleCmd.normalizeHex(color1);
		if (color2 != null) color2 = CustomRoleCmd.normalizeHex(color2);

		long requestId;
		try {
			requestId = db.customRoleRequests.create(guildId, userId, name, color1, color2, null, icon);
		} catch (SQLException ex) {
			LOG.warn("Failed to create custom role request", ex);
			sendError(event, "errors.database");
			return;
		}

		postReviewEmbed(event, event.getGuild(), settings, requestId, event.getMember(), name, color1, color2, icon, false);

		bot.getGuildLogger().botLogs.onCustomRoleRequested(event.getGuild(), userId, name, color1, color2, requestId, false);

		event.getHook().sendMessageEmbeds(bot.getEmbedUtil().getEmbed(event)
			.setColor(Constants.COLOR_SUCCESS)
			.setDescription(lu.getLocalized(event.getUserLocale(), "bot.roles.custom_role.request.submitted"))
			.build()
		).setEphemeral(true).queue();
	}

	private void buttonCustomRoleAccept(ButtonInteractionEvent event, long requestId) {
		assert event.getGuild() != null && event.getMember() != null;
		if (!isReviewer(event)) {
			sendError(event, "errors.interaction.no_access");
			return;
		}

		CustomRoleRequest request = db.customRoleRequests.getById(requestId);
		if (request == null || !request.isPending()) {
			sendError(event, "bot.roles.custom_role.errors.no_request");
			return;
		}

		createCustomRole(event, event.getGuild(), request, event.getMember().getIdLong());
	}

	private void buttonCustomRoleModify(ButtonInteractionEvent event, long requestId) {
		assert event.getGuild() != null && event.getMember() != null;
		if (!isReviewer(event)) {
			sendErrorLive(event, "errors.interaction.no_access");
			return;
		}

		CustomRoleRequest request = db.customRoleRequests.getById(requestId);
		if (request == null || !request.isPending()) {
			sendErrorLive(event, "bot.roles.custom_role.errors.no_request");
			return;
		}

		var locale = event.getUserLocale();
		boolean isGradient = request.color2 != null;
		var modal = net.dv8tion.jda.api.modals.Modal.create("cr:modify:" + requestId,
				lu.getLocalized(locale, "bot.roles.custom_role.modal.modify.title"))
			.addComponents(
				Label.of(lu.getLocalized(locale, "bot.roles.custom_role.modal.request.name"),
					TextInput.create("name", TextInputStyle.SHORT).setMaxLength(100).setValue(request.roleName).setRequired(true).build()),
				Label.of(lu.getLocalized(locale, "bot.roles.custom_role.modal.request.color_mode"),
					net.dv8tion.jda.api.components.radiogroup.RadioGroup.create("colorMode")
						.addOption("solid",
							lu.getLocalized(locale, "bot.roles.custom_role.modal.request.color_solid"),
							lu.getLocalized(locale, "bot.roles.custom_role.modal.request.color_solid_desc"),
							!isGradient)
						.addOption("gradient",
							lu.getLocalized(locale, "bot.roles.custom_role.modal.request.color_gradient"),
							lu.getLocalized(locale, "bot.roles.custom_role.modal.request.color_gradient_desc"),
							isGradient)
						.setRequired(true).build()),
				Label.of(lu.getLocalized(locale, "bot.roles.custom_role.modal.request.color1"),
					TextInput.create("color1", TextInputStyle.SHORT).setMaxLength(7).setPlaceholder("#RRGGBB").setValue(request.color1 != null ? request.color1 : "").setRequired(true).build()),
				Label.of(lu.getLocalized(locale, "bot.roles.custom_role.modal.request.color2"),
					TextInput.create("color2", TextInputStyle.SHORT).setMaxLength(7).setPlaceholder("#RRGGBB").setValue(request.color2 != null && !request.color2.isBlank() ? request.color2 : "").setRequired(false).build()),
				Label.of(lu.getLocalized(locale, "bot.roles.custom_role.modal.request.icon"),
					TextInput.create("icon", TextInputStyle.SHORT).setMaxLength(512).setValue(request.iconUrl != null && !request.iconUrl.isBlank() ? request.iconUrl : "").setRequired(false).build())
			)
			.build();
		event.replyModal(modal).queue(null, new ErrorHandler().ignore(ErrorResponse.UNKNOWN_INTERACTION));
	}

	private void modalCustomRoleModify(ModalInteractionEvent event, long requestId) {
		assert event.getGuild() != null && event.getMember() != null;
		if (!isReviewer(event)) {
			sendError(event, "errors.interaction.no_access");
			return;
		}

		CustomRoleRequest request = db.customRoleRequests.getById(requestId);
		if (request == null || !request.isPending()) {
			sendError(event, "bot.roles.custom_role.errors.no_request");
			return;
		}

		String name      = Optional.ofNullable(event.getValue("name")).map(ModalMapping::getAsOptionalString).map(String::strip).orElse(request.roleName);
		String colorMode = Optional.ofNullable(event.getValue("colorMode")).map(ModalMapping::getAsOptionalString).map(String::strip).orElse("solid");
		String color1    = Optional.ofNullable(event.getValue("color1")).map(ModalMapping::getAsOptionalString).map(String::strip).orElse("");
		String color2    = Optional.ofNullable(event.getValue("color2")).map(ModalMapping::getAsOptionalString).map(String::strip).orElse(null);
		String icon      = Optional.ofNullable(event.getValue("icon")).map(ModalMapping::getAsOptionalString).map(String::strip).orElse(null);
		if ("solid".equals(colorMode)) color2 = null;
		if (color2 != null && color2.isBlank()) color2 = null;
		if (icon   != null && icon.isBlank())   icon   = null;

		if (!CustomRoleCmd.isValidHex(color1)) {
			sendError(event, "bot.roles.custom_role.errors.invalid_hex", "Primary: " + color1);
			return;
		}
		if (color2 != null && !CustomRoleCmd.isValidHex(color2)) {
			sendError(event, "bot.roles.custom_role.errors.invalid_hex", "Secondary: " + color2);
			return;
		}

		color1 = CustomRoleCmd.normalizeHex(color1);
		if (color2 != null) color2 = CustomRoleCmd.normalizeHex(color2);

		try {
			db.customRoleRequests.updateDetails(requestId, name, color1, color2, icon);
		} catch (SQLException ex) {
			LOG.warn("Failed to update custom role request details", ex);
			sendError(event, "errors.database");
			return;
		}

		// Reload with updated details
		CustomRoleRequest updated = db.customRoleRequests.getById(requestId);
		if (updated == null) {
			sendError(event, "bot.roles.custom_role.errors.no_request");
			return;
		}
		createCustomRole(event, event.getGuild(), updated, event.getMember().getIdLong());
	}

	private void buttonCustomRoleReject(ButtonInteractionEvent event, long requestId) {
		assert event.getGuild() != null && event.getMember() != null;
		if (!isReviewer(event)) {
			sendErrorLive(event, "errors.interaction.no_access");
			return;
		}

		CustomRoleRequest request = db.customRoleRequests.getById(requestId);
		if (request == null || !request.isPending()) {
			sendErrorLive(event, "bot.roles.custom_role.errors.no_request");
			return;
		}

		var modal = net.dv8tion.jda.api.modals.Modal.create("cr:reject:" + requestId,
				lu.getLocalized(event.getUserLocale(), "bot.roles.custom_role.modal.reject.title"))
			.addComponents(
				Label.of(lu.getLocalized(event.getUserLocale(), "bot.roles.custom_role.modal.reject.reason"),
					TextInput.create("reason", TextInputStyle.PARAGRAPH).setMaxLength(500).setRequired(true).build())
			)
			.build();
		event.replyModal(modal).queue(null, new ErrorHandler().ignore(ErrorResponse.UNKNOWN_INTERACTION));
	}

	private void modalCustomRoleReject(ModalInteractionEvent event, long requestId) {
		assert event.getGuild() != null && event.getMember() != null;
		if (!isReviewer(event)) {
			sendError(event, "errors.interaction.no_access");
			return;
		}

		CustomRoleRequest request = db.customRoleRequests.getById(requestId);
		if (request == null || !request.isPending()) {
			sendError(event, "bot.roles.custom_role.errors.no_request");
			return;
		}

		String reason = Optional.ofNullable(event.getValue("reason")).map(ModalMapping::getAsOptionalString).orElse("No reason provided");

		try {
			db.customRoleRequests.reject(requestId, event.getMember().getIdLong(), reason);
		} catch (SQLException ex) {
			LOG.warn("Failed to reject custom role request", ex);
			sendError(event, "errors.database");
			return;
		}

		// Update review embed
		finalizeReviewEmbed(event, event.getGuild(), request, event.getMember().getIdLong(), false);

		// DM requester
		event.getGuild().retrieveMemberById(request.userId).queue(member ->
			member.getUser().openPrivateChannel().queue(dm ->
				dm.sendMessageEmbeds(bot.getEmbedUtil().getEmbed()
					.setColor(Constants.COLOR_FAILURE)
					.setTitle(lu.getLocalized(net.dv8tion.jda.api.interactions.DiscordLocale.ENGLISH_UK, "bot.roles.custom_role.dm.rejected.title"))
					.setDescription(lu.getLocalized(net.dv8tion.jda.api.interactions.DiscordLocale.ENGLISH_UK, "bot.roles.custom_role.dm.rejected.body")
						.formatted(request.roleName, event.getGuild().getName(), event.getMember().getEffectiveName(), reason))
					.build()
				).queue(null, new ErrorHandler().ignore(ErrorResponse.CANNOT_SEND_TO_USER))
			)
		, new ErrorHandler().ignore(ErrorResponse.UNKNOWN_MEMBER));

		event.getHook().sendMessageEmbeds(bot.getEmbedUtil().getEmbed(event)
			.setColor(Constants.COLOR_SUCCESS)
			.setDescription(lu.getGuildText(event, "bot.roles.custom_role.review.rejected_done").formatted(request.roleName))
			.build()
		).setEphemeral(true).queue();

		assert event.getMember() != null;
		bot.getGuildLogger().botLogs.onCustomRoleRejected(event.getGuild(), event.getMember().getIdLong(), request.userId, request.roleName, reason);
	}

	private void buttonCustomRoleEdit(ButtonInteractionEvent event) {
		assert event.getGuild() != null && event.getMember() != null;
		long guildId = event.getGuild().getIdLong();
		long userId = event.getMember().getIdLong();

		CustomRoleSettings settings = db.customRoleSettings.getSettings(guildId);
		if (!settings.isConfigured()) {
			sendErrorLive(event, "bot.roles.custom_role.errors.no_setup");
			return;
		}
		if (!db.customRoleAccess.hasAccess(userId, guildId)) {
			sendErrorLive(event, "bot.roles.custom_role.errors.no_access");
			return;
		}
		if (db.customRoles.getByOwner(userId, guildId) == null) {
			sendErrorLive(event, "bot.roles.custom_role.errors.no_role");
			return;
		}
		if (db.customRoleRequests.getPendingByUser(userId, guildId) != null) {
			sendErrorLive(event, "bot.roles.custom_role.errors.has_pending");
			return;
		}

		var lastApproved = db.customRoleRequests.getLatestApprovedByUser(userId, guildId);
		String prefillName   = lastApproved != null ? lastApproved.roleName : "";
		String prefillColor1 = lastApproved != null ? lastApproved.color1 : null;
		String prefillColor2 = lastApproved != null ? lastApproved.color2 : null;
		String prefillIcon   = lastApproved != null ? lastApproved.iconUrl : null;
		boolean wasGradient  = lastApproved != null && lastApproved.color2 != null;

		var locale = event.getUserLocale();
		var modal = net.dv8tion.jda.api.modals.Modal.create("cr:edit_request",
				lu.getLocalized(locale, "bot.roles.custom_role.modal.edit_request.title"))
			.addComponents(
				Label.of(lu.getLocalized(locale, "bot.roles.custom_role.modal.request.name"),
					TextInput.create("name", TextInputStyle.SHORT).setMaxLength(100).setValue(prefillName).setRequired(true).build()),
				Label.of(lu.getLocalized(locale, "bot.roles.custom_role.modal.request.color_mode"),
					net.dv8tion.jda.api.components.radiogroup.RadioGroup.create("colorMode")
						.addOption("solid",
							lu.getLocalized(locale, "bot.roles.custom_role.modal.request.color_solid"),
							lu.getLocalized(locale, "bot.roles.custom_role.modal.request.color_solid_desc"),
							!wasGradient)
						.addOption("gradient",
							lu.getLocalized(locale, "bot.roles.custom_role.modal.request.color_gradient"),
							lu.getLocalized(locale, "bot.roles.custom_role.modal.request.color_gradient_desc"),
							wasGradient)
						.setRequired(true).build()),
				Label.of(lu.getLocalized(locale, "bot.roles.custom_role.modal.request.color1"),
					TextInput.create("color1", TextInputStyle.SHORT).setMaxLength(7).setPlaceholder("#RRGGBB").setValue(prefillColor1).setRequired(true).build()),
				Label.of(lu.getLocalized(locale, "bot.roles.custom_role.modal.request.color2"),
					TextInput.create("color2", TextInputStyle.SHORT).setMaxLength(7).setPlaceholder("#RRGGBB").setValue(prefillColor2).setRequired(false).build()),
				Label.of(lu.getLocalized(locale, "bot.roles.custom_role.modal.request.icon"),
					TextInput.create("icon", TextInputStyle.SHORT).setMaxLength(512).setValue(prefillIcon).setRequired(false).build())
			)
			.build();
		event.replyModal(modal).queue(null, new ErrorHandler().ignore(ErrorResponse.UNKNOWN_INTERACTION));
	}

	private void modalCustomRoleEditRequest(ModalInteractionEvent event) {
		assert event.getGuild() != null && event.getMember() != null;
		long guildId = event.getGuild().getIdLong();
		long userId = event.getMember().getIdLong();

		CustomRoleSettings settings = db.customRoleSettings.getSettings(guildId);
		if (!settings.isConfigured()) {
			sendError(event, "bot.roles.custom_role.errors.no_setup");
			return;
		}
		if (!db.customRoleAccess.hasAccess(userId, guildId)) {
			sendError(event, "bot.roles.custom_role.errors.no_access");
			return;
		}
		if (db.customRoles.getByOwner(userId, guildId) == null) {
			sendError(event, "bot.roles.custom_role.errors.no_role");
			return;
		}
		if (db.customRoleRequests.getPendingByUser(userId, guildId) != null) {
			sendError(event, "bot.roles.custom_role.errors.has_pending");
			return;
		}

		String name      = Optional.ofNullable(event.getValue("name")).map(ModalMapping::getAsOptionalString).map(String::strip).orElse("");
		String colorMode = Optional.ofNullable(event.getValue("colorMode")).map(ModalMapping::getAsOptionalString).map(String::strip).orElse("solid");
		String color1    = Optional.ofNullable(event.getValue("color1")).map(ModalMapping::getAsOptionalString).map(String::strip).orElse("");
		String color2    = Optional.ofNullable(event.getValue("color2")).map(ModalMapping::getAsOptionalString).map(String::strip).orElse(null);
		String icon      = Optional.ofNullable(event.getValue("icon")).map(ModalMapping::getAsOptionalString).map(String::strip).orElse(null);
		if ("solid".equals(colorMode)) color2 = null;
		if (color2 != null && color2.isBlank()) color2 = null;
		if (icon   != null && icon.isBlank())   icon   = null;

		if (!CustomRoleCmd.isValidHex(color1)) {
			sendError(event, "bot.roles.custom_role.errors.invalid_hex", "Primary: " + color1);
			return;
		}
		if (color2 != null && !CustomRoleCmd.isValidHex(color2)) {
			sendError(event, "bot.roles.custom_role.errors.invalid_hex", "Secondary: " + color2);
			return;
		}
		if (icon != null && !event.getGuild().getFeatures().contains("ROLE_ICONS")) {
			sendError(event, "bot.roles.custom_role.errors.level_required");
			return;
		}

		color1 = CustomRoleCmd.normalizeHex(color1);
		if (color2 != null) color2 = CustomRoleCmd.normalizeHex(color2);

		long requestId;
		try {
			requestId = db.customRoleRequests.create(guildId, userId, name, color1, color2, null, icon, 1);
		} catch (SQLException ex) {
			LOG.warn("Failed to create custom role edit request", ex);
			sendError(event, "errors.database");
			return;
		}

		postReviewEmbed(event, event.getGuild(), settings, requestId, event.getMember(), name, color1, color2, icon, true);

		bot.getGuildLogger().botLogs.onCustomRoleRequested(event.getGuild(), userId, name, color1, color2, requestId, true);

		event.getHook().sendMessageEmbeds(bot.getEmbedUtil().getEmbed(event)
			.setColor(Constants.COLOR_SUCCESS)
			.setDescription(lu.getLocalized(event.getUserLocale(), "bot.roles.custom_role.request.edit_submitted"))
			.build()
		).setEphemeral(true).queue();
	}

	private void applyCustomRoleEdit(IReplyCallback event, net.dv8tion.jda.api.entities.Guild guild,
	                                 CustomRoleRequest request, long reviewerId) {
		String name   = request.roleName;
		String color1 = request.color1;
		String color2 = request.color2;

		int c1 = 0;
		int c2 = -1;
		try {
			if (color1 != null) c1 = Integer.parseInt(color1.replace("#", ""), 16);
			if (color2 != null) c2 = Integer.parseInt(color2.replace("#", ""), 16);
		} catch (NumberFormatException ex) {
			sendError(event, "bot.roles.custom_role.errors.invalid_hex", ex.getMessage());
			return;
		}

		boolean isGradient = color2 != null && c2 >= 0;
		if (isGradient && !guild.getFeatures().contains("ENHANCED_ROLE_COLORS")) {
			sendError(event, "bot.roles.custom_role.errors.gradient_not_supported");
			return;
		}
		if (request.iconUrl != null && !request.iconUrl.isBlank() && !guild.getFeatures().contains("ROLE_ICONS")) {
			sendError(event, "bot.roles.custom_role.errors.level_required");
			return;
		}

		Long existingRoleId = db.customRoles.getByOwner(request.userId, guild.getIdLong());
		if (existingRoleId == null) {
			sendError(event, "bot.roles.custom_role.errors.no_role");
			return;
		}
		Role existingRole = guild.getRoleById(existingRoleId);
		if (existingRole == null) {
			sendError(event, "bot.roles.custom_role.errors.no_role");
			return;
		}

		final int fc1 = c1;
		final int fc2 = c2;

		var manager = existingRole.getManager().setName(name);
		if (isGradient) {
			manager = manager.setGradientColors(fc1, fc2);
		} else {
			manager = manager.setColor(fc1);
		}

		manager.reason("Custom role edit approved by " + (event.getMember() != null ? event.getMember().getEffectiveName() : "reviewer"))
			.queue(_ -> {
				// Handle icon update
				String iconUrl = request.iconUrl;
				if (iconUrl != null && !iconUrl.isBlank()) {
					try {
						var conn = URI.create(iconUrl).toURL().openConnection();
						conn.setConnectTimeout(5000);
						conn.setReadTimeout(5000);
						byte[] bytes = conn.getInputStream().readAllBytes();
						if (bytes.length <= 256 * 1024) {
							var icon = net.dv8tion.jda.api.entities.Icon.from(bytes);
							existingRole.getManager().setIcon(icon).queue(null,
								t -> LOG.warn("Failed to update icon on custom role {}", existingRoleId, t));
						} else {
							LOG.warn("Icon too large for custom role edit {}, skipping", existingRoleId);
						}
					} catch (Exception ex) {
						LOG.warn("Failed to fetch icon for custom role edit {}", existingRoleId, ex);
					}
				}

				// DB
				try {
					db.customRoleRequests.approve(request.requestId, reviewerId);
				} catch (SQLException ex) {
					LOG.warn("Failed to record approved custom role edit in DB", ex);
				}

				// DM owner
				guild.retrieveMemberById(request.userId).queue(member ->
					member.getUser().openPrivateChannel().queue(dm ->
						dm.sendMessageEmbeds(bot.getEmbedUtil().getEmbed()
							.setColor(Constants.COLOR_SUCCESS)
							.setTitle(lu.getLocalized(DiscordLocale.ENGLISH_UK, "bot.roles.custom_role.dm.edit_approved.title"))
							.setDescription(lu.getLocalized(DiscordLocale.ENGLISH_UK, "bot.roles.custom_role.dm.edit_approved.body")
								.formatted(name, guild.getName(), event.getMember() != null ? event.getMember().getEffectiveName() : "reviewer"))
							.build()
						).queue(null, new ErrorHandler().ignore(ErrorResponse.CANNOT_SEND_TO_USER))
					)
				, t -> LOG.warn("Failed to DM member {} about custom role edit", request.userId, t));

				// Update review embed
				finalizeReviewEmbed(event, guild, request, reviewerId, true);

				// Reply to reviewer
				event.getHook().sendMessageEmbeds(bot.getEmbedUtil().getEmbed(event)
					.setColor(Constants.COLOR_SUCCESS)
					.setDescription(lu.getGuildText(event, "bot.roles.custom_role.review.edit_approved_done").formatted(existingRole.getAsMention()))
					.build()
				).setEphemeral(true).queue();

				bot.getGuildLogger().botLogs.onCustomRoleAccepted(guild, reviewerId, request.userId, existingRole.getAsMention(), true);
			},
			failure -> {
				LOG.warn("Failed to modify custom role for edit request {}", request.requestId, failure);
				sendError(event, "errors.error", failure.getMessage());
			});
	}

	private void createCustomRole(IReplyCallback event, net.dv8tion.jda.api.entities.Guild guild,
	                              CustomRoleRequest request, long reviewerId) {
		if (request.isEditRequest()) {
			applyCustomRoleEdit(event, guild, request, reviewerId);
			return;
		}

		String name   = request.roleName;
		String color1 = request.color1;
		String color2 = request.color2;

		int c1 = 0;
		int c2 = -1;
		try {
			if (color1 != null) c1 = Integer.parseInt(color1.replace("#", ""), 16);
			if (color2 != null) c2 = Integer.parseInt(color2.replace("#", ""), 16);
		} catch (NumberFormatException ex) {
			sendError(event, "bot.roles.custom_role.errors.invalid_hex", ex.getMessage());
			return;
		}

		boolean isGradient = color2 != null && c2 >= 0;
		if (isGradient && !guild.getFeatures().contains("ENHANCED_ROLE_COLORS")) {
			sendError(event, "bot.roles.custom_role.errors.gradient_not_supported");
			return;
		}
		if (request.iconUrl != null && !request.iconUrl.isBlank() && !guild.getFeatures().contains("ROLE_ICONS")) {
			sendError(event, "bot.roles.custom_role.errors.level_required");
			return;
		}

		CustomRoleSettings settings = db.customRoleSettings.getSettings(guild.getIdLong());

		var creator = guild.createRole().setName(name);
		if (isGradient) {
			creator = creator.setGradientColors(c1, c2);
		} else if (color1 != null) {
			creator = creator.setColor(c1);
		}

		creator.reason("Custom role approved by " + (event.getMember() != null ? event.getMember().getEffectiveName() : "reviewer"))
			.queue(role -> {
				// Position below configured role
				if (settings.getPositionRoleId() != null) {
					Role positionRole = guild.getRoleById(settings.getPositionRoleId());
					if (positionRole != null) {
						guild.modifyRolePositions().selectPosition(role).moveBelow(positionRole).queue(null,
							t -> LOG.warn("Failed to position custom role {} in {}", role.getIdLong(), guild.getIdLong(), t));
					}
				}

				// Handle icon — fetch from URL if present
				String iconUrl = request.iconUrl;
				if (iconUrl != null && !iconUrl.isBlank()) {
					try {
						var conn = URI.create(iconUrl).toURL().openConnection();
						conn.setConnectTimeout(5000);
						conn.setReadTimeout(5000);
						byte[] bytes = conn.getInputStream().readAllBytes();
						if (bytes.length <= 256 * 1024) {
							var icon = net.dv8tion.jda.api.entities.Icon.from(bytes);
							role.getManager().setIcon(icon).queue(null,
								t -> LOG.warn("Failed to set icon on custom role {}", role.getIdLong(), t));
						} else {
							LOG.warn("Icon too large for custom role {}, skipping", role.getIdLong());
						}
					} catch (Exception ex) {
						LOG.warn("Failed to fetch icon for custom role {}", role.getIdLong(), ex);
					}
				}

				// Assign to owner
				long ownerId = request.userId;
				guild.retrieveMemberById(ownerId).queue(member -> {
					guild.addRoleToMember(member, role)
						.reason("Custom role assigned")
						.queue(null, t -> LOG.warn("Failed to assign custom role {} to {}", role.getIdLong(), ownerId, t));

					// DB
					try {
						var accessRecord = db.customRoleAccess.getAccess(ownerId, guild.getIdLong());
						long expiresAt = accessRecord != null ? accessRecord.expiresAt : 0;
						boolean isNitro = accessRecord != null && accessRecord.isNitro;
						long renewAt = isNitro
							? java.time.Instant.now().plus(settings.getNitroRenewDays(), java.time.temporal.ChronoUnit.DAYS).getEpochSecond()
							: 0;
						db.customRoles.add(role.getIdLong(), ownerId, guild.getIdLong(), false, expiresAt, isNitro, renewAt);
						db.customRoleRequests.approve(request.requestId, reviewerId);
					} catch (SQLException ex) {
						LOG.warn("Failed to record approved custom role in DB", ex);
					}

					// DM owner
					member.getUser().openPrivateChannel().queue(dm ->
						dm.sendMessageEmbeds(bot.getEmbedUtil().getEmbed()
							.setColor(Constants.COLOR_SUCCESS)
							.setTitle(lu.getLocalized(net.dv8tion.jda.api.interactions.DiscordLocale.ENGLISH_UK, "bot.roles.custom_role.dm.approved.title"))
							.setDescription(lu.getLocalized(net.dv8tion.jda.api.interactions.DiscordLocale.ENGLISH_UK, "bot.roles.custom_role.dm.approved.body")
								.formatted(role.getName(), guild.getName(), event.getMember() != null ? event.getMember().getEffectiveName() : "reviewer"))
							.build()
						).queue(null, new ErrorHandler().ignore(ErrorResponse.CANNOT_SEND_TO_USER))
					);
				}, t -> LOG.warn("Failed to retrieve member {} for custom role assignment", ownerId, t));

				// Update review embed
				finalizeReviewEmbed(event, guild, request, reviewerId, true);

				// Reply to reviewer
				event.getHook().sendMessageEmbeds(bot.getEmbedUtil().getEmbed(event)
					.setColor(Constants.COLOR_SUCCESS)
					.setDescription(lu.getGuildText(event, "bot.roles.custom_role.review.approved_done").formatted(role.getAsMention()))
					.build()
				).setEphemeral(true).queue();

				bot.getGuildLogger().botLogs.onCustomRoleAccepted(guild, reviewerId, request.userId, role.getAsMention(), false);
			},
			failure -> {
				LOG.warn("Failed to create custom role for request {}", request.requestId, failure);
				sendError(event, "errors.error", failure.getMessage());
			});
	}

	private void postReviewEmbed(ModalInteractionEvent event, net.dv8tion.jda.api.entities.Guild guild,
	                             CustomRoleSettings settings, long requestId,
	                             net.dv8tion.jda.api.entities.Member requester,
	                             String name, String color1, @Nullable String color2,
	                             @Nullable String iconUrl, boolean isEdit) {
		var reviewChannel = Optional.ofNullable(settings.getRequestsChannelId()).map(guild::getTextChannelById).orElse(null);
		if (reviewChannel == null) return;

		net.dv8tion.jda.api.entities.Role reviewerRole = settings.getReviewerRoleId() != null
			? guild.getRoleById(settings.getReviewerRoleId()) : null;
		String ping = reviewerRole != null ? reviewerRole.getAsMention() : "";

		String titleKey = isEdit
			? "bot.roles.custom_role.review.edit_title"
			: "bot.roles.custom_role.review.title";
		var embed = bot.getEmbedUtil().getEmbed()
			.setTitle(lu.getText(event, titleKey).formatted(requestId))
			.addField(lu.getText(event, "bot.roles.custom_role.review.requested_by"), requester.getAsMention(), true)
			.addField(lu.getText(event, "bot.roles.custom_role.review.role_name"), name, true)
			.addField(lu.getText(event, "bot.roles.custom_role.review.color_primary"), color1, true);
		if (color2 != null) {
			embed.addField(lu.getText(event, "bot.roles.custom_role.review.color_secondary"), color2, true);
		}
		if (iconUrl != null) {
			embed.addField(lu.getText(event, "bot.roles.custom_role.review.icon"), iconUrl, false)
				.setImage(iconUrl);
		}
		embed.setFooter(lu.getText(event, "bot.roles.custom_role.review.footer").formatted(guild.getName()));

		List<Button> buttons = List.of(
			Button.success("cr:accept:" + requestId, lu.getText(event, "bot.roles.custom_role.review.btn_accept")),
			Button.primary("cr:modify:" + requestId, lu.getText(event, "bot.roles.custom_role.review.btn_modify")),
			Button.danger("cr:reject:" + requestId, lu.getText(event, "bot.roles.custom_role.review.btn_reject"))
		);

		reviewChannel.sendMessage(
			new net.dv8tion.jda.api.utils.messages.MessageCreateBuilder()
				.setContent(ping)
				.setEmbeds(embed.build())
				.setComponents(ActionRow.of(buttons))
				.build()
		).queue(msg -> ignoreExc(() -> db.customRoleRequests.setMessageId(requestId, msg.getIdLong())));
	}

	// Updates the posted review embed with the final decision (color, status, reviewer) and disables its buttons
	private void finalizeReviewEmbed(IReplyCallback event, net.dv8tion.jda.api.entities.Guild guild,
	                                 CustomRoleRequest request, long reviewerId, boolean approved) {
		if (request.messageId == null) return;
		CustomRoleSettings settings = db.customRoleSettings.getSettings(guild.getIdLong());
		if (settings.getRequestsChannelId() == null) return;
		TextChannel channel = guild.getTextChannelById(settings.getRequestsChannelId());
		if (channel == null) return;

		String reviewPath = "bot.roles.custom_role.review";
		channel.retrieveMessageById(request.messageId).queue(msg -> {
			var embeds = msg.getEmbeds();
			EmbedBuilder builder = embeds.isEmpty() ? bot.getEmbedUtil().getEmbed() : new EmbedBuilder(embeds.get(0));
			builder.setColor(approved ? Constants.COLOR_SUCCESS : Constants.COLOR_FAILURE)
				.addField(lu.getText(event, reviewPath+".status"),
					lu.getText(event, approved ? reviewPath+".status_approved" : reviewPath+".status_rejected"), true)
				.addField(lu.getText(event, reviewPath+".reviewer"), "<@"+reviewerId+">", true);

			msg.editMessageEmbeds(builder.build())
				.setComponents(ActionRow.of(
					Button.success("cr:accept:" + request.requestId, lu.getGuildText(event, reviewPath+".btn_accept")).asDisabled(),
					Button.primary("cr:modify:" + request.requestId, lu.getGuildText(event, reviewPath+".btn_modify")).asDisabled(),
					Button.danger("cr:reject:" + request.requestId, lu.getGuildText(event, reviewPath+".btn_reject")).asDisabled()
				))
				.queue(null, new ErrorHandler().ignore(ErrorResponse.UNKNOWN_MESSAGE));
		}, new ErrorHandler().ignore(ErrorResponse.UNKNOWN_MESSAGE));
	}

	@SuppressWarnings("BooleanMethodIsAlwaysInverted")
	private boolean isReviewer(ButtonInteractionEvent event) {
		if (event.getMember() == null || event.getGuild() == null) return false;
		if (event.getMember().hasPermission(Permission.ADMINISTRATOR)) return true;
		CustomRoleSettings settings = db.customRoleSettings.getSettings(event.getGuild().getIdLong());
		if (settings.getReviewerRoleId() == null) return false;
		return event.getMember().getRoles().stream().anyMatch(r -> r.getIdLong() == settings.getReviewerRoleId());
	}

	@SuppressWarnings("BooleanMethodIsAlwaysInverted")
	private boolean isReviewer(ModalInteractionEvent event) {
		if (event.getMember() == null || event.getGuild() == null) return false;
		if (event.getMember().hasPermission(Permission.ADMINISTRATOR)) return true;
		CustomRoleSettings settings = db.customRoleSettings.getSettings(event.getGuild().getIdLong());
		if (settings.getReviewerRoleId() == null) return false;
		return event.getMember().getRoles().stream().anyMatch(r -> r.getIdLong() == settings.getReviewerRoleId());
	}

	// ---- end Custom Role ----

	private void ignoreExc(RunnableExc runnable) {
		try {
			runnable.run();
		} catch (SQLException ignored) {}
	}

	@FunctionalInterface public interface RunnableExc { void run() throws SQLException; }
}
