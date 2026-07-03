package dev.fileeditor.votl.commands.moderation;

import java.sql.SQLException;
import java.time.Duration;
import java.util.List;
import java.util.Optional;

import dev.fileeditor.votl.base.command.SlashCommand;
import dev.fileeditor.votl.base.command.SlashCommandEvent;
import dev.fileeditor.votl.objects.CaseType;
import dev.fileeditor.votl.objects.AccessPermission;
import dev.fileeditor.votl.objects.CmdModule;
import dev.fileeditor.votl.objects.constants.CmdCategory;
import dev.fileeditor.votl.objects.constants.Constants;
import dev.fileeditor.votl.objects.constants.Limits;
import dev.fileeditor.votl.utils.CaseProofUtil;
import dev.fileeditor.votl.utils.database.managers.CaseManager.CaseData;
import dev.fileeditor.votl.utils.database.managers.GuildSettingsManager;
import dev.fileeditor.votl.utils.exception.AttachmentParseException;
import dev.fileeditor.votl.utils.exception.FormatterException;
import dev.fileeditor.votl.utils.message.TimeUtil;

import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.MessageEmbed;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import net.dv8tion.jda.api.exceptions.ErrorHandler;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import net.dv8tion.jda.api.requests.ErrorResponse;

public class MuteCmd extends SlashCommand {
	
	public MuteCmd() {
		this.name = "mute";
		this.path = "bot.moderation.mute";
		this.options = List.of(
			new OptionData(OptionType.USER, "member", lu.getText(path+".member.help"), true),
			new OptionData(OptionType.STRING, "time", lu.getText(path+".time.help"), true)
				.setMaxLength(12),
			new OptionData(OptionType.STRING, "reason", lu.getText(path+".reason.help"))
				.setMaxLength(Limits.REASON_CHARS),
			new OptionData(OptionType.ATTACHMENT, "proof", lu.getText(path+".proof.help"))
		);
		this.botPermissions = new Permission[]{Permission.MODERATE_MEMBERS};
		this.category = CmdCategory.MODERATION;
		this.module = CmdModule.MODERATION;
		this.requiredPermission = AccessPermission.CMD_MUTE;
		addMiddlewares(
			"throttle:guild,2,20"
		);
	}

	@Override
	protected void execute(SlashCommandEvent event) {
		Guild guild = event.getGuild();
		assert guild != null;
		// Check member
		Member target = event.optMember("member");
		if (target == null) {
			editError(event, "errors.option.member");
			return;
		}
		Member mod = event.getMember();
		assert mod != null;
		if (target.getUser().isBot()
			|| target.equals(guild.getSelfMember())
			|| target.equals(mod)) {
			editError(event, "errors.option.user_self");
			return;
		}
		if (!guild.getSelfMember().canInteract(target)
			|| !mod.canInteract(target)) {
			editError(event, "errors.option.member_interact");
			return;
		}

		// Get duration
		final Duration duration;
		try {
			duration = TimeUtil.stringToDuration(event.optString("time"), false);
		} catch (FormatterException ex) {
			editError(event, ex.getPath());
			return;
		}
		if (duration.isZero()) {
			editError(event, path+".abort", "Duration must larger than 1 minute.");
			return;
		}
		if (duration.toDaysPart() > 28) {
			editError(event, path+".abort", "Maximum mute duration: 28 days.");
			return;
		}

		// Enforce duration limits
		assert event.getMember() != null;
		try {
			bot.getCheckUtil().enforceMuteLimit(event, event.getMember(), duration);
		} catch (dev.fileeditor.votl.utils.exception.CheckException ex) {
			editError(event, ex.getEditData());
			return;
		}

		// Get proof
		final CaseProofUtil.ProofData proofData;
		try {
			proofData = CaseProofUtil.getData(event);
		} catch (AttachmentParseException e) {
			editError(event, e.getPath(), e.getMessage());
			return;
		}

		String reason = bot.getModerationUtil().parseReasonMentions(event);
		CaseData oldMuteData = bot.getDBUtil().cases.getMemberActive(target.getIdLong(), guild.getIdLong(), CaseType.MUTE);

		if (target.isTimedOut() && oldMuteData != null) {
			// Case already exists, change duration
			editEmbed(event, bot.getEmbedUtil().getEmbed(Constants.COLOR_WARNING)
				.setDescription(lu.getGuildText(event, path+".already_muted", oldMuteData.getLocalId()))
				.addField(lu.getGuildText(event, "logger.moderation.mute.short_title"), lu.getGuildText(event, "logger.moderation.mute.short_info")
					.replace("{username}", target.getAsMention())
					.replace("{until}", TimeUtil.formatTime(target.getTimeOutEnd(), false))
					, false)
				.build()
			);
		} else {
			// No case -> override current timeout
			// No case and not timed out -> timeout
			target.timeoutFor(duration).reason(reason).queue(_ -> {
				// inform
				final GuildSettingsManager.DramaLevel dramaLevel = bot.getDBUtil().getGuildSettings(event.getGuild()).getDramaLevel();
				target.getUser().openPrivateChannel().queue(pm -> {
					final String text = bot.getModerationUtil().getDmText(CaseType.MUTE, guild, reason, duration, mod.getUser(), false);
					if (text == null) return;
					pm.sendMessage(text).setSuppressEmbeds(true)
						.queue(null, new ErrorHandler().handle(ErrorResponse.CANNOT_SEND_TO_USER, _ -> {
							if (dramaLevel.equals(GuildSettingsManager.DramaLevel.ONLY_BAD_DM)) {
								TextChannel dramaChannel = Optional.ofNullable(bot.getDBUtil().getGuildSettings(event.getGuild()).getDramaChannelId())
									.map(event.getJDA()::getTextChannelById)
									.orElse(null);
								if (dramaChannel != null) {
									final MessageEmbed dramaEmbed = bot.getModerationUtil().getDramaEmbed(CaseType.MUTE, event.getGuild(), target, reason, duration);
									if (dramaEmbed == null) return;
									dramaChannel.sendMessage("||%s||".formatted(target.getAsMention()))
										.addEmbeds(dramaEmbed)
										.queue();
								}
							}
						}));
				});
				if (dramaLevel.equals(GuildSettingsManager.DramaLevel.ALL)) {
					assert event.getGuild() != null;
					TextChannel dramaChannel = Optional.ofNullable(bot.getDBUtil().getGuildSettings(event.getGuild()).getDramaChannelId())
						.map(event.getJDA()::getTextChannelById)
						.orElse(null);
					if (dramaChannel != null) {
						final MessageEmbed dramaEmbed = bot.getModerationUtil().getDramaEmbed(CaseType.MUTE, event.getGuild(), target, reason, duration);
						if (dramaEmbed != null) {
							dramaChannel.sendMessageEmbeds(dramaEmbed).queue();
						}
					}
				}

				// Set previous mute case inactive, as member is not timed-out
				if (oldMuteData != null) {
					try {
						bot.getDBUtil().cases.setInactive(oldMuteData.getRowId());
					} catch (SQLException e) {
						editErrorDatabase(event, e, "Failed to set previous mute case inactive.");
						return;
					}
				}
				// add info to db
				CaseData newMuteData;
				try {
					newMuteData = bot.getDBUtil().cases.add(
						CaseType.MUTE, target.getIdLong(), target.getUser().getName(),
						mod.getIdLong(), mod.getUser().getName(),
						guild.getIdLong(), reason, duration
					);
				} catch (Exception ex) {
					editErrorDatabase(event, ex, "Failed to create new case.");
					return;
				}
				// log mute
				bot.getGuildLogger().mod.onNewCase(guild, target.getUser(), newMuteData, proofData).thenAccept(logUrl -> {
					// Add log url to db
					bot.getDBUtil().cases.setLogUrl(newMuteData.getRowId(), logUrl);
					// send embed
					editEmbed(event, bot.getModerationUtil().actionEmbed(lu.getLocale(event), newMuteData.getLocalIdInt(),
						path+".success", target.getUser(), mod.getUser(), reason, duration, logUrl)
					);
				});
			},
			failure -> editErrorOther(event, failure.getMessage()));
		}
	}

}
