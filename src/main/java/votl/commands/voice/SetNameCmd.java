package votl.commands.voice;

import java.util.Collections;
import java.util.Objects;

import votl.App;
import votl.commands.CommandBase;
import votl.objects.CmdAccessLevel;
import votl.objects.CmdModule;
import votl.objects.command.SlashCommandEvent;
import votl.objects.constants.CmdCategory;

import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;

public class SetNameCmd extends CommandBase {
	
	public SetNameCmd(App bot) {
		super(bot);
		this.name = "setname";
		this.path = "bot.voice.setname";
		this.options = Collections.singletonList(
			new OptionData(OptionType.STRING, "name", lu.getText(path+".option_description"), true)
		);
		this.botPermissions = new Permission[]{Permission.MANAGE_SERVER};
		this.category = CmdCategory.VOICE;
		this.module = CmdModule.VOICE;
		this.accessLevel = CmdAccessLevel.ADMIN;
		this.mustSetup = true;
	}

	@Override
	protected void execute(SlashCommandEvent event) {
		String filName = event.optString("name", "Default name"); // REDO

		if (filName.isEmpty() || filName.length() > 100) {
			createError(event, "bot.voice.setname.invalid_range");
			return;
		}

		String guildId = Objects.requireNonNull(event.getGuild()).getId();

		bot.getDBUtil().guildVoice.setName(guildId, filName);

		createReplyEmbed(event,
			bot.getEmbedUtil().getEmbed(event)
				.setDescription(lu.getText(event, "bot.voice.setname.done").replace("{value}", filName))
				.build()
		);
	}

}
