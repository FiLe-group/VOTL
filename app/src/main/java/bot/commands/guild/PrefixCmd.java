package bot.commands.guild;

import com.jagrosh.jdautilities.command.Command;
import com.jagrosh.jdautilities.command.CommandEvent;
import com.jagrosh.jdautilities.doc.standard.CommandInfo;

import bot.App;
import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.entities.MessageEmbed;

@CommandInfo
(
	name = {"prefix"},
	description = "Set new prefix for guild.",
	usage = "{prefix}prefix <new prefix, max 4 char>",
	requirements = "Have 'Manage server' permission"
)
public class PrefixCmd extends Command {
	
	private final App bot;

	protected Permission[] userPerms;
	protected Permission[] botPerms;

	public PrefixCmd(App bot) {
		this.name = "prefix";
		this.help = "Set new prefix for guild";
		this.category = new Category("guild");
		this.userPerms = new Permission[]{Permission.MANAGE_SERVER};
		this.botPerms = new Permission[]{Permission.MESSAGE_EMBED_LINKS, Permission.MANAGE_CHANNEL};
		this.bot = bot;
	}

	@Override
	protected void execute(CommandEvent event) {
		if (bot.getCheckUtil().lacksPermissions(event.getTextChannel(), event.getMember(), true, botPerms) || 
				bot.getCheckUtil().lacksPermissions(event.getTextChannel(), event.getMember(), userPerms))
			return;

		if (!bot.getDBUtil().isGuild(event.getGuild().getId())) {
			bot.getEmbedUtil().sendError(event.getEvent(), "errors.guild_not_setup");
			return;
		}

		String args = event.getArgs();
		if (args.length() == 0) {
			bot.getEmbedUtil().sendError(event.getEvent(), "bot.guild.prefix.no_args");
			return;
		} else if (args.length() > 4 || args.contains(" ")) {
			bot.getEmbedUtil().sendError(event.getEvent(), "bot.guild.prefix.no_range");
			return;
		} 

		String prefix = args.toLowerCase();

		bot.getDBUtil().guildSetPrefix(event.getGuild().getId(), prefix);

		MessageEmbed embed = bot.getEmbedUtil().getEmbed(event.getMember())
			.setColor(bot.getMessageUtil().getColor("rgb:0,200,30"))
			.setDescription(bot.getMsg(event.getGuild().getId(), "bot.guild.prefix.done"))
			.build();
		event.reply(embed);
	}
}
