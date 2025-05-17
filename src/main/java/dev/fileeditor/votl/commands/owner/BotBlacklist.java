package dev.fileeditor.votl.commands.owner;

import dev.fileeditor.votl.base.command.SlashCommand;
import dev.fileeditor.votl.base.command.SlashCommandEvent;
import dev.fileeditor.votl.blacklist.Scope;
import dev.fileeditor.votl.objects.constants.CmdCategory;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;

import java.sql.SQLException;
import java.time.OffsetDateTime;
import java.util.List;

public class BotBlacklist extends SlashCommand {
	public BotBlacklist() {
		this.name = "bot_blacklist";
		this.path = "bot.owner.bot_blacklist";
		this.category = CmdCategory.OWNER;
		this.ownerCommand = true;
		this.options = List.of(
			new OptionData(OptionType.BOOLEAN, "add", lu.getText(path+".add.help"), true),
			new OptionData(OptionType.STRING, "id", lu.getText(path+".id.help"), true),
			new OptionData(OptionType.INTEGER, "type", lu.getText(path+".type.help"))
				.addChoice("User", 0)
				.addChoice("Guild", 1),
			new OptionData(OptionType.STRING, "reason", lu.getText(path+".reason.help"))
		);
	}

	@Override
	protected void execute(SlashCommandEvent event) {
		long id = event.optLong("id");

		try {
			if (event.optBoolean("add")) {
				Scope scope = Scope.fromId(event.optInteger("type", 0));
				String reason = event.optString("reason");
				bot.getDBUtil().blacklist.add(id, scope, OffsetDateTime.now().plusYears(10), reason);
				event.reply("Added %s `%s` to blacklist.\n> %s".formatted(scope.getName(), id, reason==null?"-None-":reason)).queue();
			} else {
				bot.getDBUtil().blacklist.remove(id);
				event.reply("Removed ID `%s` from blacklist.".formatted(id)).queue();
			}
		} catch (SQLException ex) {
			editErrorDatabase(event, ex, "Blacklist sync error.");
		}
	}
}
