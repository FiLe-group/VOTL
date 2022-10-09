package bot.commands.voice;

import java.util.EnumSet;
import java.util.Objects;

import com.jagrosh.jdautilities.command.SlashCommand;
import com.jagrosh.jdautilities.command.SlashCommandEvent;
import com.jagrosh.jdautilities.doc.standard.CommandInfo;

import bot.App;
import bot.utils.exception.CheckException;
import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.entities.channel.middleman.AudioChannel;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.channel.concrete.VoiceChannel;
import net.dv8tion.jda.api.exceptions.InsufficientPermissionException;
import net.dv8tion.jda.api.interactions.InteractionHook;

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
		this.help = bot.getMsg("bot.voice.claim.help");
		this.category = new Category("voice");
		this.botPerms = new Permission[]{Permission.MANAGE_ROLES, Permission.MANAGE_PERMISSIONS}; // Permission.MESSAGE_EMBED_LINKS
		this.bot = bot;
	}

	@Override
	protected void execute(SlashCommandEvent event) {

		event.deferReply(true).queue(
			hook -> {
				sendReply(event, hook);
			}
		);

	}

	@SuppressWarnings("null")
	private void sendReply(SlashCommandEvent event, InteractionHook hook) {

		Member member = Objects.requireNonNull(event.getMember());
		Guild guild = Objects.requireNonNull(event.getGuild());
		String guildId = guild.getId();

		try {
			bot.getCheckUtil().hasPermissions(event.getTextChannel(), member, true, botPerms)
				.isGuild(event, guildId);
		} catch (CheckException ex) {
			hook.editOriginal(ex.getEditData()).queue();
			return;
		}

		if (member.getVoiceState().inAudioChannel()) {
			AudioChannel ac = member.getVoiceState().getChannel();
			if (bot.getDBUtil().isVoiceChannelExists(ac.getId())) {
				VoiceChannel vc = guild.getVoiceChannelById(ac.getId());
				Member owner = guild.getMemberById(bot.getDBUtil().channelGetUser(vc.getId()));
				for (Member vcMember : vc.getMembers()) {
					if (vcMember == owner) {
						hook.editOriginal(bot.getMsg(guildId, "bot.voice.claim.has_owner"));
						return;
					}
				}
				try {
					vc.getManager().removePermissionOverride(owner.getIdLong()).queue();
					vc.getManager().putMemberPermissionOverride(member.getIdLong(), EnumSet.of(Permission.MANAGE_CHANNEL), null).queue();
				} catch (InsufficientPermissionException ex) {
					hook.editOriginal(bot.getEmbedUtil().getPermError(event.getTextChannel(), member, ex.getPermission(), true)).queue();
					return;
				}
				bot.getDBUtil().channelSetUser(member.getId(), vc.getId());
				
				hook.editOriginalEmbeds(
					bot.getEmbedUtil().getEmbed(member)
						.setDescription(bot.getMsg(guildId, "bot.voice.claim.done").replace("{channel}", vc.getAsMention()))
						.build()
				).queue();
			} else {
				hook.editOriginal(bot.getEmbedUtil().getError(event, "bot.voice.claim.not_custom")).queue();
			}
		} else {
			hook.editOriginal(bot.getEmbedUtil().getError(event, "bot.voice.claim.not_in_voice")).queue();
		}
	}
}
