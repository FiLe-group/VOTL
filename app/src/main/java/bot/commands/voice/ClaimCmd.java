package bot.commands.voice;

import java.util.EnumSet;

import com.jagrosh.jdautilities.command.SlashCommand;
import com.jagrosh.jdautilities.command.SlashCommandEvent;
import com.jagrosh.jdautilities.doc.standard.CommandInfo;

import bot.App;
import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.entities.AudioChannel;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.VoiceChannel;
import net.dv8tion.jda.api.exceptions.InsufficientPermissionException;
import net.dv8tion.jda.api.utils.messages.MessageCreateData;
import net.dv8tion.jda.api.utils.messages.MessageEditData;

@CommandInfo(
	name = "Claim",
	description = "Claim ownership of channel once owner has left.",
	usage = "/claim",
	requirements = "Must be in un-owned custom voice channel"
)
public class ClaimCmd extends SlashCommand {
	
	private final App bot;
	
	protected Permission[] botPerms;

	public ClaimCmd(App bot) {
		this.name = "claim";
		this.help = bot.getMsg("bot.voice.claim.description");
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

		if (event.getMember().getVoiceState().inAudioChannel()) {
			AudioChannel ac = event.getMember().getVoiceState().getChannel();
			if (bot.getDBUtil().isVoiceChannelExists(ac.getId())) {
				VoiceChannel vc = event.getGuild().getVoiceChannelById(ac.getId());
				Member owner = event.getGuild().getMemberById(bot.getDBUtil().channelGetUser(vc.getId()));
				for (Member member : vc.getMembers()) {
					if (member == owner) {
						return MessageEditData.fromContent(bot.getMsg(event.getGuild().getId(), "bot.voice.claim.has_owner"));
					}
				}
				try {
					vc.getManager().removePermissionOverride(event.getGuild().getMemberById(bot.getDBUtil().channelGetUser(vc.getId()))).queue();
					vc.getManager().putMemberPermissionOverride(event.getMember().getIdLong(), EnumSet.of(Permission.MANAGE_CHANNEL), null).queue();
				} catch (InsufficientPermissionException ex) {
					return MessageEditData.fromCreateData(bot.getEmbedUtil().getPermError(event.getTextChannel(), event.getMember(), ex.getPermission(), true));
				}
				bot.getDBUtil().channelSetUser(event.getMember().getId(), vc.getId());
				
				return MessageEditData.fromEmbeds(
					bot.getEmbedUtil().getEmbed(event.getMember())
						.setDescription(bot.getMsg(event.getGuild().getId(), "bot.voice.claim.done").replace("{channel}", vc.getAsMention()))
						.build()
				);
			} else {
				return MessageEditData.fromCreateData(bot.getEmbedUtil().getError(event, "bot.voice.claim.not_custom"));
			}
		} else {
			return MessageEditData.fromCreateData(bot.getEmbedUtil().getError(event, "bot.voice.claim.not_in_voice"));
		}
	}
}
