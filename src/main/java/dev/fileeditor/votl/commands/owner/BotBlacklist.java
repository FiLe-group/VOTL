package dev.fileeditor.votl.commands.owner;

import dev.fileeditor.votl.base.command.SlashCommand;
import dev.fileeditor.votl.base.command.SlashCommandEvent;
import dev.fileeditor.votl.objects.constants.CmdCategory;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;

import java.util.List;

public class BotBlacklist extends SlashCommand {

	public BotBlacklist() {
		this.name = "bot_blacklist";
		this.path = "bot.owner.bot_blacklist";
		this.category = CmdCategory.OWNER;
		this.ownerCommand = true;
		this.options = List.of(
			new OptionData(OptionType.BOOLEAN, "add", lu.getText(path+".add.help"), true),
			new OptionData(OptionType.STRING, "id", lu.getText(path+".id.help"), true)
		);
	}

	@Override
	protected void execute(SlashCommandEvent event) {
		long id = event.optLong("id");

		if (event.optBoolean("add")) {
			bot.getDBUtil().botBlacklist.add(id);
			event.reply("Added ID `%s` to blacklist.".formatted(id)).queue();
		} else {
			bot.getDBUtil().botBlacklist.remove(id);
			event.reply("Removed ID `%s` from blacklist.".formatted(id)).queue();
		}
	}

}
