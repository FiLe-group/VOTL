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

public class SetLimitCmd extends CommandBase {

	public SetLimitCmd(App bot) {
		super(bot);
		this.name = "setlimit";
		this.path = "bot.voice.setlimit";
		this.options = Collections.singletonList(
			new OptionData(OptionType.INTEGER, "limit", lu.getText(path+".option_limit"), true)
				.setRequiredRange(0, 99)
		);
		this.botPermissions = new Permission[]{Permission.MANAGE_SERVER};
		this.category = CmdCategory.VOICE;
		this.module = CmdModule.VOICE;
		this.accessLevel = CmdAccessLevel.ADMIN;
		this.mustSetup = true;
	}

	@Override
	protected void execute(SlashCommandEvent event) {
		Integer filLimit = event.optInteger("limit");

		String guildId = Objects.requireNonNull(event.getGuild()).getId();

		bot.getDBUtil().guildVoice.setLimit(guildId, filLimit);

		createReplyEmbed(event,
			bot.getEmbedUtil().getEmbed(event)
				.setDescription(lu.getText(event, path+".done").replace("{value}", filLimit.toString()))
				.build()
		);
	}

}
