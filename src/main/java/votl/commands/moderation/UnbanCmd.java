package votl.commands.moderation;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import votl.App;
import votl.commands.CommandBase;
import votl.objects.CmdAccessLevel;
import votl.objects.CmdModule;
import votl.objects.command.SlashCommandEvent;
import votl.objects.constants.CmdCategory;
import votl.objects.constants.Constants;

import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;

public class UnbanCmd extends CommandBase {
	
	public UnbanCmd(App bot) {
		super(bot);
		this.name = "unban";
		this.path = "bot.moderation.unban";
		List<OptionData> options = new ArrayList<>();
		options.add(new OptionData(OptionType.USER, "user", lu.getText(path+".option_user"), true));
		options.add(new OptionData(OptionType.STRING, "reason", lu.getText(path+".option_reason")).setMaxLength(400));
		this.options = options;
		this.botPermissions = new Permission[]{Permission.BAN_MEMBERS};
		this.category = CmdCategory.MODERATION;
		this.module = CmdModule.MODERATION;
		this.accessLevel = CmdAccessLevel.MOD;
		this.mustSetup = true;
	}

	@Override
	protected void execute(SlashCommandEvent event) {
		Guild guild = Objects.requireNonNull(event.getGuild());

		User tu = event.optUser("user");
		String sReason = event.optString("reason");

		if (tu == null) {
			createError(event, path+".not_found");
			return;
		}
		if (event.getUser().equals(tu) || event.getJDA().getSelfUser().equals(tu)) {
			createError(event, path+".not_self");
			return;
		}

		guild.retrieveBan(tu).queue(ban -> {
			// perform unban
			guild.unban(tu).reason(sReason).queue();
			String reason = sReason == null ? lu.getText(event, path+".no_reason") : sReason;
			/* We will not interact with DB and search for an active ban associated with user
			   as it is complex and require checks - find last ban, check if active, if not check other bans...
			   also may interfier with other cases and value isActive ~may~ be unpredictable */
			// log unban
			bot.getLogListener().onUnban(event, event.getMember(), ban, reason);
			// sync unban (show promt)

			// send promt asking user for unban sync (if available)
			createReplyEmbed(event, bot.getEmbedUtil().getEmbed(event)
				.setColor(Constants.COLOR_SUCCESS)
				.setDescription(lu.getText(event, path+".unban_success")
					.replace("{user_tag}", tu.getAsTag())
					.replace("{reason}", reason))
				.build()
			);
		},
		failure -> {
			createError(event, path+".no_ban");
		});
	}
}
