package bot.commands.voice;

import java.util.EnumSet;

import com.jagrosh.jdautilities.command.Command;
import com.jagrosh.jdautilities.command.CommandEvent;
import com.jagrosh.jdautilities.doc.standard.CommandInfo;

import bot.App;
import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.entities.AudioChannel;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.MessageEmbed;
import net.dv8tion.jda.api.entities.VoiceChannel;
import net.dv8tion.jda.api.exceptions.InsufficientPermissionException;

@CommandInfo(
	name = "Claim",
	description = "Claim ownership of channel once owner has left.",
	usage = "{prefix}claim",
	requirements = "Must be in un-owned custom voice channel"
)
public class ClaimCmd extends Command {
	
	private final App bot;
	
	protected Permission[] botPerms;

	public ClaimCmd(App bot) {
		this.name = "claim";
		this.help = "bot.voice.claim.description";
		this.category = new Category("voice");
		this.botPerms = new Permission[]{Permission.MESSAGE_EMBED_LINKS, Permission.MANAGE_ROLES, Permission.MANAGE_PERMISSIONS};
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

		if (event.getMember().getVoiceState().inAudioChannel()) {
			AudioChannel ac = event.getMember().getVoiceState().getChannel();
			if (bot.getDBUtil().isVoiceChannelExists(ac.getId())) {
				VoiceChannel vc = event.getGuild().getVoiceChannelById(ac.getId());
				Member owner = event.getGuild().getMemberById(bot.getDBUtil().channelGetUser(vc.getId()));
				for (Member member : vc.getMembers()) {
					if (member == owner) {
						event.reply(bot.getMsg(event.getGuild().getId(), "bot.voice.claim.has_owner"));
						return;
					}
				}
				try {
					vc.getManager().removePermissionOverride(event.getGuild().getMemberById(bot.getDBUtil().channelGetUser(vc.getId()))).queue();
					vc.getManager().putMemberPermissionOverride(event.getMember().getIdLong(), EnumSet.of(Permission.MANAGE_CHANNEL), null).queue();
				} catch (InsufficientPermissionException ex) {
					bot.getEmbedUtil().sendPermError(event.getTextChannel(), event.getMember(), Permission.MANAGE_PERMISSIONS, true);
					return;
				}
				bot.getDBUtil().channelSetUser(event.getMember().getId(), vc.getId());
				
				MessageEmbed embed = bot.getEmbedUtil().getEmbed(event.getMember())
					.setDescription(bot.getMsg(event.getGuild().getId(), "bot.voice.claim.done").replace("{channel}", vc.getAsMention()))
					.build();
				event.reply(embed);
			} else {
				event.reply(bot.getMsg(event.getGuild().getId(), "bot.voice.claim.not_custom"));
			}
		} else {
			event.reply(bot.getMsg(event.getGuild().getId(), "bot.voice.claim.not_in_voice"));
		}
	}
}
