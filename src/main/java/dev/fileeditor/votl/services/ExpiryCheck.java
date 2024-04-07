package dev.fileeditor.votl.services;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;

import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.User;

import dev.fileeditor.votl.App;
import dev.fileeditor.votl.utils.database.DBUtil;

public class ExpiryCheck {

	private final App bot;
	private final DBUtil db;

	public ExpiryCheck(App bot) {
		this.bot = bot;
		this.db = bot.getDBUtil();
	}
	
	public void checkUnbans() {
		List<Map<String, Object>> bans = db.ban.getExpirable();
		if (bans.isEmpty()) return;
		bans.stream().filter(ban ->
			Duration.between(Instant.parse(ban.get("timeStart").toString()), Instant.now()).compareTo(Duration.parse(ban.get("duration").toString())) >= 0
		).forEach(ban -> {
			Integer banId = Integer.parseInt(ban.get("banId").toString());
			Guild guild = bot.jda.getGuildById(ban.get("guildId").toString());
			if (guild == null || !guild.getSelfMember().hasPermission(Permission.BAN_MEMBERS)) return;
			guild.unban(User.fromId(ban.get("userId").toString())).reason("Temporary ban expired").queue(
				s -> bot.getLogListener().onAutoUnban(ban, banId, guild),
				f -> bot.getLogger().warn("Exception at unban attempt", f.getMessage())
			);
			db.ban.setInactive(banId);
		});
	}
}
