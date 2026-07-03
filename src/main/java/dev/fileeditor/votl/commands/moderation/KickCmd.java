package dev.fileeditor.votl.commands.moderation;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

import dev.fileeditor.votl.base.command.SlashCommand;
import dev.fileeditor.votl.base.command.SlashCommandEvent;
import dev.fileeditor.votl.objects.CaseType;
import dev.fileeditor.votl.objects.AccessPermission;
import dev.fileeditor.votl.objects.CmdModule;
import dev.fileeditor.votl.objects.constants.CmdCategory;
import dev.fileeditor.votl.objects.constants.Limits;
import dev.fileeditor.votl.utils.CaseProofUtil;
import dev.fileeditor.votl.utils.database.managers.CaseManager.CaseData;

import dev.fileeditor.votl.utils.database.managers.GuildSettingsManager;
import dev.fileeditor.votl.utils.exception.AttachmentParseException;
import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.components.actionrow.ActionRow;
import net.dv8tion.jda.api.components.buttons.Button;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.MessageEmbed;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import net.dv8tion.jda.api.entities.emoji.Emoji;
import net.dv8tion.jda.api.exceptions.ErrorHandler;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import net.dv8tion.jda.api.requests.ErrorResponse;

public class KickCmd extends SlashCommand {

	public KickCmd () {
		this.name = "kick";
		this.path = "bot.moderation.kick";
		this.options = List.of(
			new OptionData(OptionType.USER, "member", lu.getText(path+".member.help"), true),
			new OptionData(OptionType.STRING, "reason", lu.getText(path+".reason.help"))
				.setMaxLength(Limits.REASON_CHARS),
			new OptionData(OptionType.ATTACHMENT, "proof", lu.getText(path+".proof.help")),
			new OptionData(OptionType.BOOLEAN, "dm", lu.getText(path+".dm.help"))
		);
		this.botPermissions = new Permission[]{Permission.KICK_MEMBERS};
		this.category = CmdCategory.MODERATION;
		this.module = CmdModule.MODERATION;
		this.requiredPermission = AccessPermission.CMD_KICK;
		addMiddlewares(
			"throttle:guild,2,20"
		);
	}

	@Override
	protected void execute(SlashCommandEvent event) {
		Guild guild = event.getGuild();
		assert guild != null;

		Member target = event.optMember("member");
		if (target == null) {
			editError(event, "errors.option.member");
			return;
		}
		Member mod = event.getMember();
		assert mod != null;
		if (target.getUser().isBot()
			|| target.equals(event.getGuild().getSelfMember())
			|| target.equals(mod)) {
			editError(event, "errors.option.user_self");
			return;
		}
		if (!event.getGuild().getSelfMember().canInteract(target)
			|| !mod.canInteract(target)) {
			editError(event, "errors.option.member_interact");
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
		// inform user
		final GuildSettingsManager.DramaLevel dramaLevel = bot.getDBUtil().getGuildSettings(event.getGuild()).getDramaLevel();
		if (event.optBoolean("dm", true)) {
			target.getUser().openPrivateChannel().queue(pm -> {
				final String text = bot.getModerationUtil().getDmText(CaseType.KICK, guild, reason, null, mod.getUser(), false);
				if (text == null) return;
				pm.sendMessage(text).setSuppressEmbeds(true)
					.queue(null, new ErrorHandler().handle(ErrorResponse.CANNOT_SEND_TO_USER, _ -> {
						if (dramaLevel.equals(GuildSettingsManager.DramaLevel.ONLY_BAD_DM)) {
							TextChannel dramaChannel = Optional.ofNullable(bot.getDBUtil().getGuildSettings(event.getGuild()).getDramaChannelId())
								.map(event.getJDA()::getTextChannelById)
								.orElse(null);
							if (dramaChannel != null) {
								final MessageEmbed dramaEmbed = bot.getModerationUtil().getDramaEmbed(CaseType.KICK, event.getGuild(), target, reason, null);
								if (dramaEmbed == null) return;
								dramaChannel.sendMessage("||%s||".formatted(target.getAsMention()))
									.addEmbeds(dramaEmbed)
									.queue();
							}
						}
					}));
			});
		}
		if (dramaLevel.equals(GuildSettingsManager.DramaLevel.ALL)) {
			assert event.getGuild() != null;
			TextChannel dramaChannel = Optional.ofNullable(bot.getDBUtil().getGuildSettings(event.getGuild()).getDramaChannelId())
				.map(event.getJDA()::getTextChannelById)
				.orElse(null);
			if (dramaChannel != null) {
				final MessageEmbed dramaEmbed = bot.getModerationUtil().getDramaEmbed(CaseType.KICK, event.getGuild(), target, reason, null);
				if (dramaEmbed != null) {
					dramaChannel.sendMessageEmbeds(dramaEmbed).queue();
				}
			}
		}

		target.kick().reason(reason).queueAfter(2, TimeUnit.SECONDS, _ -> {
			// add info to db
			CaseData kickData;
			try {
				kickData = bot.getDBUtil().cases.add(
					CaseType.KICK, target.getIdLong(), target.getUser().getName(),
					mod.getIdLong(), mod.getUser().getName(),
					guild.getIdLong(), reason, null
				);
			} catch (Exception ex) {
				editErrorDatabase(event, ex, "Failed to create new case.");
				return;
			}
			// log kick
			bot.getGuildLogger().mod.onNewCase(guild, target.getUser(), kickData, proofData).thenAccept(logUrl -> {
				// Add log url to db
				bot.getDBUtil().cases.setLogUrl(kickData.getRowId(), logUrl);
				// reply and ask for kick sync
				event.getHook().editOriginalEmbeds(
					bot.getModerationUtil().actionEmbed(lu.getLocale(event), kickData.getLocalIdInt(),
						path+".success", target.getUser(), mod.getUser(), reason, logUrl)
				).setComponents(ActionRow.of(
					Button.primary("sync_kick:"+target.getId(), "Sync kick").withEmoji(Emoji.fromUnicode("🆑"))
				)).queue();
			});
		},
		failure -> editErrorOther(event, failure.getMessage()));
	}
}
