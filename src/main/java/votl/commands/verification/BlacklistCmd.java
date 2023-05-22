package votl.commands.verification;

import java.util.ArrayList;
import java.util.List;

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

public class BlacklistCmd extends CommandBase {
	
	public BlacklistCmd(App bot) {
		super(bot);
		this.name = "blacklist";
		this.path = "bot.verification.blacklist";
		List<OptionData> options = new ArrayList<>();
		options.add(new OptionData(OptionType.USER, "user", lu.getText(path+".option_user"), true));
		options.add(new OptionData(OptionType.STRING, "reason", lu.getText(path+".option_reason")).setMaxLength(200));
		this.options = options;
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
		if (member == null || !guild.getSelfMember().canInteract(member)) {
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

		String reason = event.optString("reason", lu.getLocalized(event.getGuildLocale(), path+".no_reason"));

		if (member.getRoles().contains(role)) {
			guild.removeRoleFromMember(member, role).reason("Blacklisted by "+event.getUser().getAsTag()+" | "+reason).queue(
				success -> {
					createReplyEmbed(event, bot.getEmbedUtil().getEmbed(event).setDescription(lu.getText(event, path+".done_role")).build());
				},
				failure -> {
					createError(event, "bot.verification.failed_role");
					bot.getLogger().info("Was unable to remove verify role to user in "+guild.getName()+"("+guild.getId()+")", failure);
				});
			return;
		}

		
		if (bot.getDBUtil().verify.addUser(guild.getId(), member.getId())) {
			bot.getLogListener().onBlacklist(member, guild, reason);
			createReplyEmbed(event, bot.getEmbedUtil().getEmbed(event).setDescription(lu.getText(event, path+".done_added")).build());
		} else {
			createError(event, path+".failed_to_add");
		}
		

	}

}
