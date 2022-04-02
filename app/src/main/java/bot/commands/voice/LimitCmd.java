package bot.commands.voice;

import com.jagrosh.jdautilities.command.Command;
import com.jagrosh.jdautilities.command.CommandEvent;
import com.jagrosh.jdautilities.doc.standard.CommandInfo;

import bot.App;
import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.entities.MessageEmbed;
import net.dv8tion.jda.api.entities.VoiceChannel;

@CommandInfo(
	name = "Limit",
	description = "Sets limit for your channel.",
	usage = "{prefix}limit <Integer from 0 to 99>",
	requirements = "Must have created voice channel"
)
public class LimitCmd extends Command {
	
	private final App bot;

	protected Permission[] botPerms;

	public LimitCmd(App bot) {
		this.name = "limit";
		this.help = "bot.voice.limit.description";
		this.category = new Category("voice");
		this.botPerms = new Permission[]{Permission.MESSAGE_EMBED_LINKS, Permission.MANAGE_CHANNEL};
		this.bot = bot;
	}

	@Override
	protected void execute(CommandEvent event) {
		if (bot.getCheckUtil().lacksPermissions(event.getTextChannel(), event.getMember(), true, botPerms))
			return;

		if (!bot.getDBUtil().isGuildVoice(event.getGuild().getId())) {
			bot.getEmbedUtil().sendError(event.getEvent(), "errors.voice_not_setup");
			return;
		}

		if (bot.getDBUtil().isVoiceChannel(event.getMember().getId())) {
			String args = event.getArgs();
			if (args.isEmpty()) {
				bot.getEmbedUtil().sendError(event.getEvent(), "bot.voice.limit.invalid_args");
				return;
			}

			Integer limit;
			try {
				limit = Integer.parseInt(args);
			} catch (NumberFormatException ex) {
				bot.getEmbedUtil().sendError(event.getEvent(), "bot.voice.setlimit.invalid_type");
				return;
			}

			VoiceChannel vc = event.getGuild().getVoiceChannelById(bot.getDBUtil().channelGetChannel(event.getMember().getId()));
			try {
				vc.getManager().setUserLimit(limit).queue(
					channel -> {
						MessageEmbed embed = bot.getEmbedUtil().getEmbed(event.getMember())
							.setDescription(bot.getMsg(event.getGuild().getId(), "bot.voice.limit.done").replace("{value}", limit.toString()))
							.build();
						event.reply(embed);
					}
				);
			} catch (IllegalArgumentException ex) {
				bot.getEmbedUtil().sendError(event.getEvent(), "bot.voice.limit.invalid_range");
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
