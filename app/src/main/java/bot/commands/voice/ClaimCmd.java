package bot.commands.voice;

import java.util.EnumSet;
import java.util.Objects;

import javax.annotation.Nonnull;

import com.jagrosh.jdautilities.command.SlashCommand;
import com.jagrosh.jdautilities.command.SlashCommandEvent;
import com.jagrosh.jdautilities.doc.standard.CommandInfo;

import bot.App;
import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.entities.channel.middleman.AudioChannel;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.channel.concrete.VoiceChannel;
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

	@SuppressWarnings("null")
	@Nonnull
	private MessageEditData getReply(SlashCommandEvent event) {

		Member member = Objects.requireNonNull(event.getMember());

		MessageCreateData permission = bot.getCheckUtil().lacksPermissions(event.getTextChannel(), member, true, botPerms);
		if (permission != null)
			return MessageEditData.fromCreateData(permission);

		Guild guild = Objects.requireNonNull(event.getGuild());
		String guildId = guild.getId();

		if (!bot.getDBUtil().isGuild(guildId)) {
			return MessageEditData.fromCreateData(bot.getEmbedUtil().getError(event, "errors.guild_not_setup"));
		}

		if (member.getVoiceState().inAudioChannel()) {
			AudioChannel ac = member.getVoiceState().getChannel();
			if (bot.getDBUtil().isVoiceChannelExists(ac.getId())) {
				VoiceChannel vc = guild.getVoiceChannelById(ac.getId());
				Member owner = guild.getMemberById(bot.getDBUtil().channelGetUser(vc.getId()));
				for (Member vcMember : vc.getMembers()) {
					if (vcMember == owner) {
						return MessageEditData.fromContent(bot.getMsg(guildId, "bot.voice.claim.has_owner"));
					}
				}
				try {
					vc.getManager().removePermissionOverride(owner.getIdLong()).queue();
					vc.getManager().putMemberPermissionOverride(member.getIdLong(), EnumSet.of(Permission.MANAGE_CHANNEL), null).queue();
				} catch (InsufficientPermissionException ex) {
					return MessageEditData.fromCreateData(bot.getEmbedUtil().getPermError(event.getTextChannel(), member, ex.getPermission(), true));
				}
				bot.getDBUtil().channelSetUser(member.getId(), vc.getId());
				
				return MessageEditData.fromEmbeds(
					bot.getEmbedUtil().getEmbed(member)
						.setDescription(bot.getMsg(guildId, "bot.voice.claim.done").replace("{channel}", vc.getAsMention()))
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
