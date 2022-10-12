package bot.commands.voice;

import java.util.EnumSet;
import java.util.Objects;

import com.jagrosh.jdautilities.command.SlashCommand;
import com.jagrosh.jdautilities.command.SlashCommandEvent;
import com.jagrosh.jdautilities.doc.standard.CommandInfo;

import bot.App;
import bot.objects.CmdAccessLevel;
import bot.objects.constants.CmdCategory;
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

	private static final boolean mustSetup = true;
	private static final String MODULE = "voice";
	private static final CmdAccessLevel ACCESS_LEVEL = CmdAccessLevel.ALL;

	protected static Permission[] userPerms = new Permission[0];
	protected static Permission[] botPerms = new Permission[0];

	public ClaimCmd(App bot) {
		this.name = "claim";
		this.help = bot.getMsg("bot.voice.claim.help");
		this.category = CmdCategory.VOICE;
		ClaimCmd.botPerms = new Permission[]{Permission.MANAGE_ROLES, Permission.MANAGE_PERMISSIONS}; // Permission.MESSAGE_EMBED_LINKS
		this.bot = bot;
	}

	@Override
	protected void execute(SlashCommandEvent event) {

		event.deferReply(true).queue(
			hook -> {
				try {
					// check access
					bot.getCheckUtil().hasAccess(event, ACCESS_LEVEL)
					// check module enabled
						.moduleEnabled(event, MODULE)
					// check user perms
						.hasPermissions(event.getTextChannel(), event.getMember(), userPerms)
					// check bots perms
						.hasPermissions(event.getTextChannel(), event.getMember(), true, botPerms);
					// check setup
					if (mustSetup) {
						bot.getCheckUtil().guildExists(event);
					}
				} catch (CheckException ex) {
					hook.editOriginal(ex.getEditData()).queue();
					return;
				}
				
				sendReply(event, hook);
			}
		);

	}

	@SuppressWarnings("null")
	private void sendReply(SlashCommandEvent event, InteractionHook hook) {

		Member author = Objects.requireNonNull(event.getMember());

		if (!author.getVoiceState().inAudioChannel()) {
			hook.editOriginal(bot.getEmbedUtil().getError(event, "bot.voice.claim.not_in_voice")).queue();
			return;
		}

		AudioChannel ac = author.getVoiceState().getChannel();
		if (!bot.getDBUtil().isVoiceChannelExists(ac.getId())) {
			hook.editOriginal(bot.getEmbedUtil().getError(event, "bot.voice.claim.not_custom")).queue();
			return;
		}

		Guild guild = Objects.requireNonNull(event.getGuild());
		String guildId = guild.getId();

		VoiceChannel vc = guild.getVoiceChannelById(ac.getId());
		Member owner = guild.getMemberById(bot.getDBUtil().channelGetUser(vc.getId()));
		for (Member vcMember : vc.getMembers()) {
			if (vcMember == owner) {
				hook.editOriginal(bot.getMsg(guildId, "bot.voice.claim.has_owner")).queue();
				return;
			}
		}

		try {
			vc.getManager().removePermissionOverride(owner).queue();
			vc.getManager().putPermissionOverride(author, EnumSet.of(Permission.MANAGE_CHANNEL), null).queue();
		} catch (InsufficientPermissionException ex) {
			hook.editOriginal(bot.getEmbedUtil().getPermError(event.getTextChannel(), author, ex.getPermission(), true)).queue();
			return;
		}
		bot.getDBUtil().channelSetUser(author.getId(), vc.getId());
		
		hook.editOriginalEmbeds(
			bot.getEmbedUtil().getEmbed(author)
				.setDescription(bot.getMsg(guildId, "bot.voice.claim.done").replace("{channel}", vc.getAsMention()))
				.build()
		).queue();
	}
}
