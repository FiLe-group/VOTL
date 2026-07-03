package dev.fileeditor.votl.commands.moderation;

import java.util.List;

import dev.fileeditor.votl.base.command.SlashCommand;
import dev.fileeditor.votl.base.command.SlashCommandEvent;
import dev.fileeditor.votl.objects.CaseType;
import dev.fileeditor.votl.objects.AccessPermission;
import dev.fileeditor.votl.objects.CmdModule;
import dev.fileeditor.votl.objects.constants.CmdCategory;
import dev.fileeditor.votl.objects.constants.Limits;
import dev.fileeditor.votl.utils.database.managers.CaseManager.CaseData;

import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;

public class UnmuteCmd extends SlashCommand {
	
	public UnmuteCmd() {
		this.name = "unmute";
		this.path = "bot.moderation.unmute";
		this.options = List.of(
			new OptionData(OptionType.USER, "member", lu.getText(path+".member.help"), true),
			new OptionData(OptionType.STRING, "reason", lu.getText(path+".reason.help"))
				.setMaxLength(Limits.REASON_CHARS)
		);
		this.botPermissions = new Permission[]{Permission.MODERATE_MEMBERS};
		this.category = CmdCategory.MODERATION;
		this.module = CmdModule.MODERATION;
		this.requiredPermission = AccessPermission.CMD_UNMUTE;
		addMiddlewares(
			"throttle:guild,1,10"
		);
	}

	@Override
	protected void execute(SlashCommandEvent event) {
		Guild guild = event.getGuild();
		assert guild != null;
		// Check member
		Member target = event.optMember("user");
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

		CaseData muteData = bot.getDBUtil().cases.getMemberActive(target.getIdLong(), guild.getIdLong(), CaseType.MUTE);
		if (muteData != null) {
			ignoreExc(() -> bot.getDBUtil().cases.setInactive(muteData.getRowId()));
		}

		if (target.isTimedOut()) {
			String reason = bot.getModerationUtil().parseReasonMentions(event);
			target.removeTimeout().reason(reason).queue(_ -> {
				// add info to db
				CaseData unmuteData;
				try {
					unmuteData = bot.getDBUtil().cases.add(
						CaseType.UNMUTE, target.getIdLong(), target.getUser().getName(),
						mod.getIdLong(), mod.getUser().getName(),
						guild.getIdLong(), reason, null
					);
				} catch (Exception ex) {
					editErrorDatabase(event, ex, "Failed to create new case.");
					return;
				}
				// log unmute
				bot.getGuildLogger().mod.onNewCase(guild, target.getUser(), unmuteData, muteData != null ? muteData.getReason() : null).thenAccept(logUrl -> {
					// reply
					editEmbed(event, bot.getModerationUtil().actionEmbed(lu.getLocale(event), unmuteData.getLocalIdInt(),
						path+".success", target.getUser(), mod.getUser(), reason, logUrl)
					);
				});
			},
			failed -> editError(event, path+".abort", failed.getMessage()));
		} else {
			editError(event, path+".not_muted");
		}
	}

}
