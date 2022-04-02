package bot.commands.voice;

import com.jagrosh.jdautilities.command.Command;
import com.jagrosh.jdautilities.command.CommandEvent;
import com.jagrosh.jdautilities.doc.standard.CommandInfo;

import bot.App;
import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.entities.MessageEmbed;
import net.dv8tion.jda.api.entities.VoiceChannel;

@CommandInfo(
	name = "Name",
	description = "Sets name for your channel.",
	usage = "{prefix}name <String name>",
	requirements = "Must have created voice channel"
)
public class NameCmd extends Command {
	
	private final App bot;

	protected Permission[] botPerms;

	public NameCmd(App bot) {
		this.name = "name";
		this.help = "bot.voice.name.description";
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
			String name = event.getArgs();
			if (name.isEmpty()) {
				bot.getEmbedUtil().sendError(event.getEvent(), "bot.voice.name.invalid_args");
				return;
			}

			VoiceChannel vc = event.getGuild().getVoiceChannelById(bot.getDBUtil().channelGetChannel(event.getMember().getId()));
			try {
				vc.getManager().setName(name).queue(
					channel -> {
						MessageEmbed embed = bot.getEmbedUtil().getEmbed(event.getMember())
							.setDescription(bot.getMsg(event.getGuild().getId(), "bot.voice.name.done").replace("{value}", name))
							.build();
						event.reply(embed);
					}
				);
			} catch (IllegalArgumentException ex) {
				bot.getEmbedUtil().sendError(event.getEvent(), "bot.voice.name.invalid_range");
				return;
			}
			if (!bot.getDBUtil().isUser(event.getMember().getId())) {
				bot.getDBUtil().userAdd(event.getMember().getId());
			}
			bot.getDBUtil().userSetName(event.getMember().getId(), name);
		} else {
			event.reply(bot.getMsg(event.getGuild().getId(), "bot.voice.name.no_channel"));
		}
	}
}
