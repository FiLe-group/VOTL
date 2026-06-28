package dev.fileeditor.votl.commands.owner;

import dev.fileeditor.votl.base.command.SlashCommand;
import dev.fileeditor.votl.base.command.SlashCommandEvent;
import dev.fileeditor.votl.objects.AccessPermission;
import dev.fileeditor.votl.objects.constants.CmdCategory;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;

import java.util.List;
import java.util.stream.Collectors;

public class CheckAccessCmd extends SlashCommand {
	public CheckAccessCmd() {
		this.name = "checkaccess";
		this.path = "bot.owner.checkaccess";
		this.category = CmdCategory.OWNER;
		this.requiredPermission = AccessPermission.DEV;
		this.options = List.of(
			new OptionData(OptionType.STRING, "server", lu.getText(path+".server.help"), true),
			new OptionData(OptionType.USER, "user", lu.getText(path+".user.help"), true)
		);
		this.ephemeral = true;
	}

	@Override
	protected void execute(SlashCommandEvent event) {
		Guild guild = event.getJDA().getGuildById(event.optString("server", "0"));
		if (guild == null) {
			editMsg(event, "ERROR: Selected server not found.");
			return;
		}

		User user = event.optUser("user");
		if (user == null) {
			editMsg(event, "ERROR: User not found.");
			return;
		}

		guild.retrieveMember(user).queue(member -> {
			String tier;
			if (bot.getCheckUtil().hasAccess(member, AccessPermission.DEV))
				tier = "DEV";
			else if (bot.getCheckUtil().hasAccess(member, AccessPermission.OWNER))
				tier = "OWNER";
			else if (bot.getCheckUtil().hasAccess(member, AccessPermission.ADMIN))
				tier = "ADMIN";
			else {
				var perms = bot.getCheckUtil().resolve(member);
				if (perms.permissions().isEmpty())
					tier = "NO PERMISSIONS";
				else
					tier = perms.permissions().stream().map(Enum::name).collect(Collectors.joining(", "));
			}
			editMsg(event, "%s(%s) - %s".formatted(member.getAsMention(), member.getEffectiveName(), tier));
		}, failure -> editError(event, failure.getMessage()));
	}
}