package dev.fileeditor.votl.menus;

import net.dv8tion.jda.api.entities.User;

import dev.fileeditor.votl.base.command.UserContextMenu;
import dev.fileeditor.votl.base.command.UserContextMenuEvent;
import dev.fileeditor.votl.commands.moderation.ModLogsCmd;
import dev.fileeditor.votl.objects.CmdAccessLevel;
import dev.fileeditor.votl.objects.CmdModule;
import dev.fileeditor.votl.utils.database.managers.CaseManager;

import java.util.List;

public class ModlogsMenu extends UserContextMenu {
	public ModlogsMenu() {
		this.name = "modlogs";
		this.path = "menus.modlogs";
		this.module = CmdModule.MODERATION;
		this.accessLevel = CmdAccessLevel.MOD;
		addMiddlewares(
			"throttle:user,1,10"
		);
		this.ephemeral = true;
	}

	@Override
	protected void execute(UserContextMenuEvent event) {
		User user = event.getTarget();

		assert event.getGuild() != null;
		long guildId = event.getGuild().getIdLong();
		long userId = user.getIdLong();
		final List<CaseManager.CaseData> cases = bot.getDBUtil().cases.getGuildUser(guildId, userId, 1);
		if (cases.isEmpty()) {
			event.getHook().editOriginalEmbeds(bot.getEmbedUtil()
				.getEmbed()
				.setDescription(lu.getGuildText(event, "bot.moderation.modlogs.empty"))
				.build()
			).queue();
			return;
		}
		int pages = (int) Math.ceil(bot.getDBUtil().cases.countCases(guildId, userId)/10.0);

		event.getHook().editOriginalEmbeds(
			ModLogsCmd.buildEmbed(lu, event, user, cases, 1, pages)
				.setDescription(lu.getGuildText(event, path+".full"))
				.build()
		).queue();
	}
}
