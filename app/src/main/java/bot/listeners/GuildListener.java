package bot.listeners;

import bot.App;
import net.dv8tion.jda.api.events.guild.GuildJoinEvent;
import net.dv8tion.jda.api.events.guild.GuildLeaveEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;

public class GuildListener extends ListenerAdapter {

	private final App bot;

	public GuildListener(App bot) {
		this.bot = bot;
	}

	@Override
	public void onGuildJoin(GuildJoinEvent event) {
		if (!bot.getDBUtil().isGuild(event.getGuild().getId())) {
			// adds guild to DB
			bot.getDBUtil().guildAdd(event.getGuild().getId());
			bot.getLogger().info("Added guild '"+event.getGuild().getName()+"'("+event.getGuild().getId()+") to db");
		}
		bot.getLogger().info("Joined guild '"+event.getGuild().getName()+"'("+event.getGuild().getId()+")");
	}

	@Override
	public void onGuildLeave(GuildLeaveEvent event) {
		if (bot.getDBUtil().isGuild(event.getGuild().getId())) {
			//deletes from db guild and guildSettings
			bot.getDBUtil().guildRemove(event.getGuild().getId());
			bot.getLogger().info("Removed guild '"+event.getGuild().getName()+"'("+event.getGuild().getId()+") from db");
		}
		bot.getLogger().info("Left guild '"+event.getGuild().getName()+"'("+event.getGuild().getId()+")");
	}
}
