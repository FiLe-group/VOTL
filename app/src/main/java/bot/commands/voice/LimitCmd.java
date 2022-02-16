package bot.commands.voice;

import com.jagrosh.jdautilities.command.Command;
import com.jagrosh.jdautilities.command.CommandEvent;
import com.jagrosh.jdautilities.doc.standard.CommandInfo;

import bot.App;
import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.entities.VoiceChannel;

@CommandInfo(
	name = "Limit",
	description = "Sets limit for your channel",
	usage = "{prefix}limit <Integer from 0 to 99>",
	requirements = "Must have created voice channel"
)
public class LimitCmd extends Command {
	
	private final App bot;

	protected Permission[] botPerms;

	public LimitCmd(App bot) {
		this.name = "limit";
		this.help = "Sets limit for your channel";
		this.category = new Category("voice");
		this.botPerms = new Permission[]{Permission.MESSAGE_EMBED_LINKS, Permission.MANAGE_CHANNEL};
		this.bot = bot;
	}

	@Override
	protected void execute(CommandEvent event) {
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

		if (bot.getDBUtil().isVoiceChannel(event.getMember().getId())) {
			String args = event.getArgs();
			if (args.length() == 0) {
				bot.getEmbedUtil().sendError(event.getEvent(), "bot.voice.limit.no_args");
				return;
			}

			Integer limit;
			try {
				limit = Integer.parseInt(args);
			} catch (NumberFormatException ex) {
				bot.getEmbedUtil().sendError(event.getEvent(), "bot.voice.setlimit.not_integer");
				return;
			}

			VoiceChannel vc = event.getGuild().getVoiceChannelById(bot.getDBUtil().channelGetChannel(event.getMember().getId()));
			try {
				vc.getManager().setUserLimit(limit).queue(
					channel -> {
						event.reply(bot.getMsg(event.getGuild().getId(), "bot.voice.limit.done").replace("{value}", limit.toString()));
					}
				);
			} catch (IllegalArgumentException ex) {
				bot.getEmbedUtil().sendError(event.getEvent(), "bot.voice.limit.not_range");
				return;
			}
			if (!bot.getDBUtil().isUser(event.getMember().getId())) {
				bot.getDBUtil().userAdd(event.getMember().getId());
			}
			bot.getDBUtil().userSetLimit(event.getMember().getId(), limit);
		} else {
			event.reply(bot.getMsg(event.getGuild().getId(), "bot.voice.limit.no_channel"));
		}
	}
}
