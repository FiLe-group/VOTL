package bot.commands.voice;

import com.jagrosh.jdautilities.command.Command;
import com.jagrosh.jdautilities.command.CommandEvent;
import com.jagrosh.jdautilities.doc.standard.CommandInfo;

import bot.App;
import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.entities.VoiceChannel;
import net.dv8tion.jda.api.exceptions.InsufficientPermissionException;

@CommandInfo(
	name = "unlock",
	description = "Unocks your channel for everyone (ex. disallowed users)",
	usage = "{prefix}unlock",
	requirements = "Must have created voice channel"
)
public class UnlockCmd extends Command {
	
	private final App bot;

	protected Permission[] botPerms;

	public UnlockCmd(App bot) {
		this.name = "unlock";
		this.help = "Unocks your channel for everyone (ex. disallowed users)";
		this.category = new Category("voice");
		this.botPerms = new Permission[]{Permission.MESSAGE_EMBED_LINKS, Permission.MANAGE_ROLES, Permission.MANAGE_PERMISSIONS};
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
			bot.getEmbedUtil().sendError(event.getTextChannel(), event.getMember(), "errors.voice_not_setup");
			return;
		}

		if (bot.getDBUtil().isVoiceChannel(event.getMember().getId())) {
			VoiceChannel vc = event.getGuild().getVoiceChannelById(bot.getDBUtil().channelGetChannel(event.getMember().getId()));
			try {
				vc.upsertPermissionOverride(event.getGuild().getPublicRole()).clear(Permission.VOICE_CONNECT).queue();
			} catch (InsufficientPermissionException ex) {
				bot.getEmbedUtil().sendPermError(event.getTextChannel(), event.getMember(), Permission.MANAGE_PERMISSIONS, true);
				return;
			}
			event.reply(bot.getMsg(event.getGuild().getId(), "bot.voice.unlock.done"));
		} else {
			event.reply(bot.getMsg(event.getGuild().getId(), "bot.voice.unlock.no_channel"));
		}
	}
}
