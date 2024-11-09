package dev.fileeditor.votl.commands.strike;

import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.TimeUnit;

import dev.fileeditor.votl.base.command.CooldownScope;
import dev.fileeditor.votl.base.command.SlashCommandEvent;
import dev.fileeditor.votl.commands.CommandBase;
import dev.fileeditor.votl.objects.CaseType;
import dev.fileeditor.votl.objects.CmdAccessLevel;
import dev.fileeditor.votl.objects.CmdModule;
import dev.fileeditor.votl.objects.PunishAction;
import dev.fileeditor.votl.objects.constants.CmdCategory;
import dev.fileeditor.votl.objects.constants.Constants;
import dev.fileeditor.votl.utils.CaseProofUtil;
import dev.fileeditor.votl.utils.database.managers.CaseManager.CaseData;
import dev.fileeditor.votl.utils.exception.AttachmentParseException;
import dev.fileeditor.votl.utils.message.TimeUtil;

import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.MessageEmbed;
import net.dv8tion.jda.api.entities.Role;
import net.dv8tion.jda.api.entities.MessageEmbed.Field;
import net.dv8tion.jda.api.exceptions.ErrorHandler;
import net.dv8tion.jda.api.interactions.DiscordLocale;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.Command.Choice;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import net.dv8tion.jda.api.interactions.components.buttons.Button;
import net.dv8tion.jda.api.requests.ErrorResponse;
import net.dv8tion.jda.api.utils.TimeFormat;
import net.dv8tion.jda.internal.utils.tuple.Pair;

public class StrikeCmd extends CommandBase {
	
	public StrikeCmd() {
		this.name = "strike";
		this.path = "bot.moderation.strike";
		this.options = List.of(
			new OptionData(OptionType.USER, "user", lu.getText(path+".user.help"), true),
			new OptionData(OptionType.INTEGER, "severity", lu.getText(path+".severity.help"), true).addChoices(List.of(
				new Choice(lu.getText(path+".severity.minor"), 1).setNameLocalizations(lu.getLocaleMap(path+".severity.minor")),
				new Choice(lu.getText(path+".severity.severe"), 2).setNameLocalizations(lu.getLocaleMap(path+".severity.severe")),
				new Choice(lu.getText(path+".severity.extreme"), 3).setNameLocalizations(lu.getLocaleMap(path+".severity.extreme"))
			)),
			new OptionData(OptionType.STRING, "reason", lu.getText(path+".reason.help"), true).setMaxLength(400),
			new OptionData(OptionType.ATTACHMENT, "proof", lu.getText(path+".proof.help"))
		);
		this.category = CmdCategory.MODERATION;
		this.module = CmdModule.STRIKES;
		this.accessLevel = CmdAccessLevel.MOD;
		this.cooldown = 5;
		this.cooldownScope = CooldownScope.GUILD;
	}

	@Override
	protected void execute(SlashCommandEvent event) {
		event.deferReply().queue();

		Member tm = event.optMember("user");
		if (tm == null) {
			editError(event, path+".not_found");
			return;
		}
		if (event.getUser().equals(tm.getUser()) || tm.getUser().isBot()) {
			editError(event, path+".not_self");
			return;
		}

		// Check if target has strike cooldown
		Guild guild = Objects.requireNonNull(event.getGuild());
		int strikeCooldown = bot.getDBUtil().getGuildSettings(guild).getStrikeCooldown();
		if (strikeCooldown > 0) {
			Instant lastAddition = bot.getDBUtil().strikes.getLastAddition(guild.getIdLong(), tm.getIdLong());
			if (lastAddition != null && lastAddition.isAfter(Instant.now().minus(strikeCooldown, ChronoUnit.MINUTES))) {
				// Cooldown active
				editHookEmbed(event, bot.getEmbedUtil().getEmbed(Constants.COLOR_FAILURE)
					.setDescription(lu.getText(event, path+".cooldown").formatted(TimeFormat.RELATIVE.format(lastAddition.plus(strikeCooldown, ChronoUnit.MINUTES))))
					.build()
				);
				return;
			}
		}

		// Get proof
		final CaseProofUtil.ProofData proofData;
		try {
			proofData = CaseProofUtil.getData(event);
		} catch (AttachmentParseException e) {
			editError(event, e.getPath(), e.getMessage());
			return;
		}

		String reason = event.optString("reason");
		Integer strikeAmount = event.optInteger("severity", 1);
		CaseType type = CaseType.byType(20 + strikeAmount);

		Member mod = event.getMember();
		tm.getUser().openPrivateChannel().queue(pm -> {
			Button button = Button.secondary("strikes:"+guild.getId(), lu.getLocalized(guild.getLocale(), "logger_embed.pm.button_strikes"));
			MessageEmbed embed = bot.getModerationUtil().getDmEmbed(type, guild, reason, null, mod.getUser(), false);
			if (embed == null) return;
			pm.sendMessageEmbeds(embed).addActionRow(button).queue(null, new ErrorHandler().ignore(ErrorResponse.CANNOT_SEND_TO_USER));
		});

		// add info to db
		bot.getDBUtil().cases.add(type, tm.getIdLong(), tm.getUser().getName(), mod.getIdLong(), mod.getUser().getName(),
			guild.getIdLong(), reason, Instant.now(), null);
		CaseData caseData = bot.getDBUtil().cases.getMemberLast(tm.getIdLong(), guild.getIdLong());
		// add strikes
		Field action = executeStrike(guild.getLocale(), guild, tm, strikeAmount, caseData.getCaseId());
		// log
		bot.getLogger().mod.onNewCase(guild, tm.getUser(), caseData, proofData);
		// send reply
		EmbedBuilder builder = bot.getEmbedUtil().getEmbed(Constants.COLOR_SUCCESS)
			.setDescription(lu.getGuildText(event, path+".success")
				.formatted(lu.getGuildText(event, type.getPath())))
			.addField(lu.getGuildText(event, "logger.user"), "%s (%s)".formatted(tm.getUser().getName(), tm.getAsMention()), true)
			.addField(lu.getGuildText(event, "logger.reason"), reason, true)
			.addField(lu.getGuildText(event, "logger.moderation.mod"), "%s (%s)".formatted(mod.getUser().getName(), mod.getAsMention()), false);
		if (action != null) builder.addField(action);

		editHookEmbed(event, builder.build());
	}

	private Field executeStrike(DiscordLocale locale, Guild guild, Member target, Integer addAmount, Integer caseId) {
		// Add strike(-s) to DB
		bot.getDBUtil().strikes.addStrikes(guild.getIdLong(), target.getIdLong(),
			Instant.now().plus(bot.getDBUtil().getGuildSettings(guild).getStrikeExpires(), ChronoUnit.DAYS),
			addAmount, caseId+"-"+addAmount);
		// Get strike new strike amount
		Integer strikes = bot.getDBUtil().strikes.getStrikeCount(guild.getIdLong(), target.getIdLong());
		// Check if strikes is null (how?)
		if (strikes == null) return null;
		// Get actions for strike amount
		Pair<Integer, String> punishActions = bot.getDBUtil().autopunish.getTopAction(guild.getIdLong(), strikes);
		if (punishActions == null) return null;

		List<PunishAction> actions = PunishAction.decodeActions(punishActions.getLeft());
		if (actions.isEmpty()) return null;
		String data = punishActions.getRight();

		// Check if user can interact and target is not automod exception or higher
		if (!guild.getSelfMember().canInteract(target)) return new Field(
			lu.getLocalized(locale, path+".autopunish_error"),
			lu.getLocalized(locale, path+".autopunish_higher"),
			false
		);
		if (bot.getCheckUtil().getAccessLevel(target).isHigherThan(CmdAccessLevel.ALL)) return new Field(
			lu.getLocalized(locale, path+".autopunish_error"),
			lu.getLocalized(locale, path+".autopunish_exception"),
			false
		);

		// Execute
		StringBuilder builder = new StringBuilder();
		if (actions.contains(PunishAction.KICK)) {
			String reason = lu.getLocalized(locale, path+".autopunish_reason").formatted(strikes);
			// Send PM to user
			target.getUser().openPrivateChannel().queue(pm -> {
				MessageEmbed embed = bot.getModerationUtil().getDmEmbed(CaseType.KICK, guild, reason, null, null, false);
				if (embed == null) return;
				pm.sendMessageEmbeds(embed).queue(null, new ErrorHandler().ignore(ErrorResponse.CANNOT_SEND_TO_USER));
			});

			guild.kick(target).reason(reason).queueAfter(3, TimeUnit.SECONDS, done -> {
				// add case to DB
				bot.getDBUtil().cases.add(CaseType.KICK, target.getIdLong(), target.getUser().getName(), 0, "Autopunish",
					guild.getIdLong(), reason, Instant.now(), null);
				CaseData caseData = bot.getDBUtil().cases.getMemberLast(target.getIdLong(), guild.getIdLong());
				// log case
				bot.getLogger().mod.onNewCase(guild, target.getUser(), caseData);
			},
			failure -> bot.getAppLogger().error("Strike punishment execution, Kick member", failure));
			builder.append(lu.getLocalized(locale, PunishAction.KICK.getPath()))
				.append("\n");
		}
		if (actions.contains(PunishAction.BAN)) {
			Duration duration = null;
			try {
				duration = Duration.ofSeconds(Long.parseLong(PunishAction.BAN.getMatchedValue(data)));
			} catch (NumberFormatException ignored) {}
			if (duration != null && !duration.isZero()) {
				String reason = lu.getLocalized(locale, path+".autopunish_reason").formatted(strikes);
				Duration durationCopy = duration;
				// Send PM to user
				target.getUser().openPrivateChannel().queue(pm -> {
					MessageEmbed embed = bot.getModerationUtil().getDmEmbed(CaseType.BAN, guild, reason, durationCopy, null, true);
					if (embed == null) return;
					pm.sendMessageEmbeds(embed).queue(null, new ErrorHandler().ignore(ErrorResponse.CANNOT_SEND_TO_USER));
				});

				guild.ban(target, 0, TimeUnit.SECONDS).reason(lu.getLocalized(locale, path+".autopunish_reason").formatted(strikes)).queue(done -> {
					// add case to DB
					bot.getDBUtil().cases.add(CaseType.BAN, target.getIdLong(), target.getUser().getName(), 0, "Autopunish",
						guild.getIdLong(), reason, Instant.now(), durationCopy);
					CaseData caseData = bot.getDBUtil().cases.getMemberLast(target.getIdLong(), guild.getIdLong());
					// log case
					bot.getLogger().mod.onNewCase(guild, target.getUser(), caseData);
				},
				failure -> bot.getAppLogger().error("Strike punishment execution, Ban member", failure));
				builder.append(lu.getLocalized(locale, PunishAction.BAN.getPath())).append(" ")
					.append(lu.getLocalized(locale, path + ".for")).append(" ")
					.append(TimeUtil.durationToLocalizedString(lu, locale, duration))
					.append("\n");
			}
		}
		if (actions.contains(PunishAction.REMOVE_ROLE)) {
			Long roleId = null;
			try {
				roleId = Long.valueOf(PunishAction.REMOVE_ROLE.getMatchedValue(data));
			} catch (NumberFormatException ignored) {}
			if (roleId != null) {
				Role role = guild.getRoleById(roleId);
				if (role != null && guild.getSelfMember().canInteract(role)) {
					// Apply action, result will be in logs
					guild.removeRoleFromMember(target, role).reason(lu.getLocalized(locale, path+".autopunish_reason").formatted(strikes))
						.queueAfter(5, TimeUnit.SECONDS, done -> {
						// log action
						bot.getLogger().role.onRoleRemoved(guild, bot.JDA.getSelfUser(), target.getUser(), role);
					},
					failure -> bot.getAppLogger().error("Strike punishment execution, Remove role", failure));
					builder.append(lu.getLocalized(locale, PunishAction.REMOVE_ROLE.getPath())).append(" ")
						.append(role.getName())
						.append("\n");
				}
			}
		}
		if (actions.contains(PunishAction.ADD_ROLE)) {
			Long roleId = null;
			try {
				roleId = Long.valueOf(PunishAction.ADD_ROLE.getMatchedValue(data));
			} catch (NumberFormatException ignored) {}
			if (roleId != null) {
				Role role = guild.getRoleById(roleId);
				if (role != null && guild.getSelfMember().canInteract(role)) {
					// Apply action, result will be in logs
					guild.addRoleToMember(target, role).reason(lu.getLocalized(locale, path+".autopunish_reason").formatted(strikes))
						.queueAfter(5, TimeUnit.SECONDS, done -> {
						// log action
						bot.getLogger().role.onRoleAdded(guild, bot.JDA.getSelfUser(), target.getUser(), role);
					},
					failure -> bot.getAppLogger().error("Strike punishment execution, Add role", failure));
					builder.append(lu.getLocalized(locale, PunishAction.ADD_ROLE.getPath())).append(" ")
						.append(role.getName())
						.append("\n");
				}
			}
		}
		if (actions.contains(PunishAction.MUTE)) {
			Duration duration = null;
			try {
				duration = Duration.ofSeconds(Long.parseLong(PunishAction.MUTE.getMatchedValue(data)));
			} catch (NumberFormatException ignored) {}
			if (duration != null && !duration.isZero()) {
				String reason = lu.getLocalized(locale, path+".autopunish_reason").formatted(strikes);
				// Send PM to user
				target.getUser().openPrivateChannel().queue(pm -> {
					MessageEmbed embed = bot.getModerationUtil().getDmEmbed(CaseType.MUTE, guild, reason, null, null, false);
					if (embed == null) return;
					pm.sendMessageEmbeds(embed).queue(null, new ErrorHandler().ignore(ErrorResponse.CANNOT_SEND_TO_USER));
				});

				Duration durationCopy = duration;
				guild.timeoutFor(target, duration).reason(lu.getLocalized(locale, path+".autopunish_reason").formatted(strikes)).queue(done -> {
					// add case to DB
					bot.getDBUtil().cases.add(CaseType.MUTE, target.getIdLong(), target.getUser().getName(), 0, "Autopunish",
						guild.getIdLong(), reason, Instant.now(), durationCopy);
					CaseData caseData = bot.getDBUtil().cases.getMemberLast(target.getIdLong(), guild.getIdLong());
					// log case
					bot.getLogger().mod.onNewCase(guild, target.getUser(), caseData);
				},
				failure -> bot.getAppLogger().error("Strike punishment execution, Mute member", failure));
				builder.append(lu.getLocalized(locale, PunishAction.MUTE.getPath())).append(" ")
					.append(lu.getLocalized(locale, path + ".for")).append(" ")
					.append(TimeUtil.durationToLocalizedString(lu, locale, duration))
					.append("\n");
			}
		}

		if (builder.isEmpty()) return null;
		
		return new Field(
			lu.getLocalized(locale, path+".autopunish_title").formatted(strikes),
			builder.toString(),
			false
		);
	}

}
