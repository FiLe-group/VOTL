package bot.commands.voice;

import com.jagrosh.jdautilities.command.SlashCommand;
import com.jagrosh.jdautilities.command.SlashCommandEvent;
import com.jagrosh.jdautilities.doc.standard.CommandInfo;

import bot.App;
import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.entities.VoiceChannel;
import net.dv8tion.jda.api.exceptions.InsufficientPermissionException;
import net.dv8tion.jda.api.utils.messages.MessageCreateData;
import net.dv8tion.jda.api.utils.messages.MessageEditData;

@CommandInfo(
	name = "unlock",
	description = "Unocks your channel for everyone (ex. disallowed users).",
	usage = "/unlock",
	requirements = "Must have created voice channel"
)
public class UnlockCmd extends SlashCommand {
	
	private final App bot;

	protected Permission[] botPerms;

	public UnlockCmd(App bot) {
		this.name = "unlock";
		this.help = bot.getMsg("bot.voice.unlock.description");
		this.category = new Category("voice");
		this.botPerms = new Permission[]{Permission.MANAGE_ROLES, Permission.MANAGE_PERMISSIONS}; // Permission.MESSAGE_EMBED_LINKS
		this.bot = bot;
	}

	@Override
	protected void execute(SlashCommandEvent event) {

		event.deferReply(true).queue(
			hook -> {
				MessageEditData reply = getReply(event);

				hook.editOriginal(reply).queue();
			}
		);

	}

	private MessageEditData getReply(SlashCommandEvent event) {
		MessageCreateData permission = bot.getCheckUtil().lacksPermissions(event.getTextChannel(), event.getMember(), true, botPerms);
		if (permission != null)
			return MessageEditData.fromCreateData(permission);

		if (!bot.getDBUtil().isGuild(event.getGuild().getId())) {
			return MessageEditData.fromCreateData(bot.getEmbedUtil().getError(event, "errors.guild_not_setup"));
		}

		if (bot.getDBUtil().isVoiceChannel(event.getMember().getId())) {
			VoiceChannel vc = event.getGuild().getVoiceChannelById(bot.getDBUtil().channelGetChannel(event.getMember().getId()));
			try {
				vc.upsertPermissionOverride(event.getGuild().getPublicRole()).clear(Permission.VOICE_CONNECT).queue();
			} catch (InsufficientPermissionException ex) {
				return MessageEditData.fromCreateData(bot.getEmbedUtil().getPermError(event.getTextChannel(), event.getMember(), ex.getPermission(), true));
			}

			return MessageEditData.fromEmbeds(
				bot.getEmbedUtil().getEmbed(event.getMember())
					.setDescription(bot.getMsg(event.getGuild().getId(), "bot.voice.unlock.done"))
					.build()
			);
		} else {
			return MessageEditData.fromCreateData(bot.getEmbedUtil().getError(event, "bot.voice.unlock.no_channel"));
		}
	}
}
