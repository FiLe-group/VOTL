package votl.commands.verification;

import java.util.Collections;

import votl.App;
import votl.commands.CommandBase;
import votl.objects.CmdAccessLevel;
import votl.objects.CmdModule;
import votl.objects.command.SlashCommandEvent;
import votl.objects.constants.CmdCategory;

import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.Role;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;

public class VerifyCmd extends CommandBase {
	
	public VerifyCmd(App bot) {
		super(bot);
		this.name = "verify";
		this.path = "bot.verification.verify";
		this.options = Collections.singletonList(
			new OptionData(OptionType.USER, "user", lu.getText(path+".option_user"), true)
		);
		this.botPermissions = new Permission[]{Permission.MANAGE_ROLES};
		this.module = CmdModule.VERIFICATION;
		this.category = CmdCategory.VERIFICATION;
		this.accessLevel = CmdAccessLevel.MOD;
		this.mustSetup = true;
	}

	@Override
	protected void execute(SlashCommandEvent event) {
		Member member = event.optMember("user");
		Guild guild = event.getGuild();
		if (member == null || member.getUser().isBot() || !guild.getSelfMember().canInteract(member)) {
			createError(event, path+".no_user");
		}

		String roleId = bot.getDBUtil().verify.getVerifyRole(guild.getId());
		if (roleId == null) {
			createError(event, path+".not_setup");
			return;
		}
		Role role = guild.getRoleById(roleId);
		if (role == null) {
			createError(event, "errors.unknown", "Role not found by ID: "+roleId);
			return;
		}

		if (member.getRoles().contains(role)) {
			createError(event, "bot.verification.user_verified");
			return;
		}

		guild.addRoleToMember(member, role).reason("Manual verification by "+event.getUser().getAsTag()).queue(
			success -> {
				bot.getLogListener().onVerified(member, guild);
				bot.getDBUtil().verify.removeUser(guild.getId(), member.getId());
				createReplyEmbed(event, bot.getEmbedUtil().getEmbed(event).setDescription(lu.getText(event, path+".done")).build());
			},
			failure -> {
				createError(event, "bot.verification.failed_role");
				bot.getLogger().info("Was unable to add verify role to user in "+guild.getName()+"("+guild.getId()+")", failure);
			});
	}

}
