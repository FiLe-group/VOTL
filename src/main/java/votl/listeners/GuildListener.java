package votl.listeners;

import javax.annotation.Nonnull;

import votl.App;

import net.dv8tion.jda.api.events.guild.GuildJoinEvent;
import net.dv8tion.jda.api.events.guild.GuildLeaveEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;

public class GuildListener extends ListenerAdapter {

	private final App bot;

	public GuildListener(App bot) {
		this.bot = bot;
	}

	@Override
	public void onGuildJoin(@Nonnull GuildJoinEvent event) {
		String guildId = event.getGuild().getId();
		// check if not exists in DB and adds it
		if (bot.getDBUtil().guild.add(guildId)) {
			bot.getLogger().info("Automatically added guild '"+event.getGuild().getName()+"'("+guildId+") to db");
		}
		bot.getLogger().info("Joined guild '"+event.getGuild().getName()+"'("+guildId+")");
	}

	@Override
	public void onGuildLeave(@Nonnull GuildLeaveEvent event) {
		String guildId = event.getGuild().getId();
		if (bot.getDBUtil().guild.exists(guildId) || bot.getDBUtil().guildVoice.exists(guildId)) {
			//deletes from db guild and guildSettings
			bot.getDBUtil().guild.remove(guildId);
			bot.getLogger().info("Automatically removed guild '"+event.getGuild().getName()+"'("+guildId+") from db");
		}
		bot.getLogger().info("Left guild '"+event.getGuild().getName()+"'("+guildId+")");
	}
}
