package bot.commands.voice;

import com.jagrosh.jdautilities.command.Command;
import com.jagrosh.jdautilities.command.CommandEvent;
import com.jagrosh.jdautilities.doc.standard.CommandInfo;

import bot.App;
import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.entities.MessageEmbed;

@CommandInfo(
	name = "SetLimit",
	description = "Sets default user limit for server's voice channels.",
	usage = "{prefix}setlimit <Integer from 0 to 99>",
	requirements = "Have 'Manage server' permission"
)
public class SetLimitCmd extends Command {
	
	private final App bot;

	protected Permission[] userPerms;
	protected Permission[] botPerms;

	public SetLimitCmd(App bot) {
		this.name = "setlimit";
		this.help = "bot.voice.setlimit.description";
		this.category = new Category("voice");
		this.userPerms = new Permission[]{Permission.MANAGE_SERVER};
		this.botPerms = new Permission[]{Permission.MESSAGE_EMBED_LINKS};
		this.bot = bot;
	}

	@Override
	protected void execute(CommandEvent event) {
		if (bot.getCheckUtil().lacksPermissions(event.getTextChannel(), event.getMember(), true, botPerms) || 
				bot.getCheckUtil().lacksPermissions(event.getTextChannel(), event.getMember(), userPerms))
			return;

		if (!bot.getDBUtil().isGuildVoice(event.getGuild().getId())) {
			bot.getEmbedUtil().sendError(event.getEvent(), "errors.voice_not_setup");
			return;
		}

		String args = event.getArgs();
		if (args.isEmpty()) {
			bot.getEmbedUtil().sendError(event.getEvent(), "bot.voice.setlimit.invalid_args");
			return;
		}
		Integer limit = null;
		try {
			limit = Integer.parseInt(args);
		} catch (NumberFormatException ex) {
			bot.getEmbedUtil().sendError(event.getEvent(), "bot.voice.setlimit.invalid_type");
			return;
		}
		if (limit == null || limit<0 || limit>99) {
			bot.getEmbedUtil().sendError(event.getEvent(), "bot.voice.setlimit.invalid_range");
			return;
		}

		bot.getDBUtil().guildVoiceSetLimit(event.getGuild().getId(), limit);

		MessageEmbed embed = bot.getEmbedUtil().getEmbed(event.getMember())
			.setDescription(bot.getMsg(event.getGuild().getId(), "bot.voice.setlimit.done").replace("{value}", limit.toString()))
			.build();
		event.reply(embed);
	}
}
