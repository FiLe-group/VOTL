package dev.fileeditor.votl.commands.voice;

import java.util.Collections;
import java.util.Objects;

import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;

import dev.fileeditor.votl.App;
import dev.fileeditor.votl.commands.CommandBase;
import dev.fileeditor.votl.objects.CmdAccessLevel;
import dev.fileeditor.votl.objects.CmdModule;
import dev.fileeditor.votl.objects.command.SlashCommandEvent;
import dev.fileeditor.votl.objects.constants.CmdCategory;

public class SetNameCmd extends CommandBase {
	
	public SetNameCmd(App bot) {
		super(bot);
		this.name = "setname";
		this.path = "bot.voice.setname";
		this.options = Collections.singletonList(
			new OptionData(OptionType.STRING, "name", lu.getText(path+".option_name"), true)
				.setMaxLength(100)
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

		if (filName.isEmpty()) {
			createError(event, path+".invalid_range");
			return;
		}

		String guildId = Objects.requireNonNull(event.getGuild()).getId();

		bot.getDBUtil().guildVoice.setName(guildId, filName);

		createReplyEmbed(event,
			bot.getEmbedUtil().getEmbed(event)
				.setDescription(lu.getText(event, path+".done").replace("{value}", filName))
				.build()
		);
	}

}
