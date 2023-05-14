package votl.commands.verification;

import java.util.Collections;

import votl.App;
import votl.commands.CommandBase;
import votl.objects.CmdAccessLevel;
import votl.objects.CmdModule;
import votl.objects.command.SlashCommandEvent;
import votl.objects.constants.CmdCategory;
import votl.objects.constants.Constants;

import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Role;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;

public class VerifyRoleCmd extends CommandBase {
	
	public VerifyRoleCmd(App bot) { 
		super(bot);
		this.name = "verifyrole";
		this.path = "bot.verification.verifyrole";
		this.options = Collections.singletonList(
			new OptionData(OptionType.ROLE, "role", lu.getText(path+".option_role"), true)
		);
		this.module = CmdModule.VERIFICATION;
		this.category = CmdCategory.VERIFICATION;
		this.accessLevel = CmdAccessLevel.ADMIN;
		this.mustSetup = true;
	}

	@Override
	protected void execute(SlashCommandEvent event) {
		Guild guild = event.getGuild();
		Role role = event.optRole("role");
		if (role == null || role.isPublicRole() || role.isManaged() || !guild.getSelfMember().canInteract(role)) {
			createError(event, path+".no_role");
			return;
		}

		if (!bot.getDBUtil().verify.exists(guild.getId())) {
			bot.getDBUtil().verify.add(guild.getId());
		}

		bot.getDBUtil().verify.setVerifyRole(guild.getId(), role.getId());

		createReplyEmbed(event, bot.getEmbedUtil().getEmbed(event)
			.setDescription(lu.getText(event, path+".done").replace("{role}", role.getAsMention()))
			.setColor(Constants.COLOR_SUCCESS)
			.build());
	}

}
