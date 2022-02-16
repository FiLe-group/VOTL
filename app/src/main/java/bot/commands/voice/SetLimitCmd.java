package bot.commands.voice;

import com.jagrosh.jdautilities.command.Command;
import com.jagrosh.jdautilities.command.CommandEvent;
import com.jagrosh.jdautilities.doc.standard.CommandInfo;

import bot.App;
import net.dv8tion.jda.api.Permission;

@CommandInfo(
	name = "SetLimit",
	description = "Sets default user limit for server's voice channels",
	usage = "{prefix}setlimit <Integer from 0 to 99>",
	requirements = "Have 'Manage server' permission"
)
public class SetLimitCmd extends Command {
	
	private final App bot;

	protected Permission[] userPerms;
	protected Permission[] botPerms;

	public SetLimitCmd(App bot) {
		this.name = "setlimit";
		this.help = "Sets default user limit for server's voice channels";
		this.category = new Category("voice");
		this.userPerms = new Permission[]{Permission.MANAGE_SERVER};
		this.botPerms = new Permission[]{Permission.MESSAGE_EMBED_LINKS};
		this.bot = bot;
	}

	@Override
	protected void execute(CommandEvent event) {
		for (Permission perm : userPerms) {
			if (!event.getMember().hasPermission(perm)) {
				bot.getEmbedUtil().sendPermError(event.getTextChannel(), event.getMember(), perm, false);
				return;
			}
		}

		for (Permission perm : botPerms) {
			if (!event.getSelfMember().hasPermission(event.getTextChannel(), perm)) {
				bot.getEmbedUtil().sendPermError(event.getTextChannel(), event.getMember(), perm, true);
				return;
			}
		}

		if (!bot.getDBUtil().isGuild(event.getGuild().getId())) {
			bot.getEmbedUtil().sendError(event.getEvent(), "errors.voice_not_setup");
			return;
		}

		String args = event.getArgs();
		if (args.length() == 0) {
			bot.getEmbedUtil().sendError(event.getEvent(), "bot.voice.setlimit.no_args");
			return;
		}
		Integer limit = null;
		try {
			limit = Integer.parseInt(args);
		} catch (NumberFormatException ex) {
			bot.getEmbedUtil().sendError(event.getEvent(), "bot.voice.setlimit.not_integer");
			return;
		}
		if (limit == null || limit<0 || limit>99) {
			bot.getEmbedUtil().sendError(event.getEvent(), "bot.voice.setlimit.not_range");
			return;
		}

		bot.getDBUtil().guildVoiceSetLimit(event.getGuild().getId(), limit);
		event.reply(bot.getMsg(event.getGuild().getId(), "bot.voice.setlimit.done").replace("{value}", limit.toString()));
	}
}
