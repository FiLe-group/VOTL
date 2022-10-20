package bot.commands.voice;

import java.util.Objects;

import com.jagrosh.jdautilities.command.SlashCommand;
import com.jagrosh.jdautilities.command.SlashCommandEvent;
import com.jagrosh.jdautilities.doc.standard.CommandInfo;

import bot.App;
import bot.objects.CmdAccessLevel;
import bot.objects.constants.CmdCategory;
import bot.utils.exception.CheckException;
import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.channel.concrete.VoiceChannel;
import net.dv8tion.jda.api.exceptions.InsufficientPermissionException;
import net.dv8tion.jda.api.interactions.InteractionHook;
import net.dv8tion.jda.api.utils.messages.MessageEditData;

@CommandInfo(
	name = "lock",
	description = "Locks voice channel for @everyone (ex. allowed one).",
	usage = "/lock",
	requirements = "Must have created voice channel"
)
public class LockCmd extends SlashCommand {
	
	private final App bot;
	
	private static final boolean mustSetup = true;
	private static final String MODULE = "voice";
	private static final CmdAccessLevel ACCESS_LEVEL = CmdAccessLevel.ALL;

	protected static Permission[] userPerms = new Permission[0];
	protected static Permission[] botPerms = new Permission[0];

	public LockCmd(App bot) {
		this.name = "lock";
		this.help = bot.getMsg("bot.voice.lock.help");
		this.category = CmdCategory.VOICE;
		LockCmd.botPerms = new Permission[]{Permission.MANAGE_ROLES, Permission.MANAGE_PERMISSIONS, Permission.VOICE_CONNECT};
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
						.hasPermissions(event, userPerms)
					// check bots perms
						.hasPermissions(event, true, botPerms);
					// check setup
					if (mustSetup) {
						bot.getCheckUtil().guildExists(event, mustSetup);
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

		Member member = Objects.requireNonNull(event.getMember());
		String memberId = member.getId();

		if (!bot.getDBUtil().isVoiceChannel(memberId)) {
			hook.editOriginal(bot.getEmbedUtil().getError(event, "errors.no_channel")).queue();
			return;
		}

		Guild guild = Objects.requireNonNull(event.getGuild());
		String guildId = guild.getId();

		VoiceChannel vc = guild.getVoiceChannelById(bot.getDBUtil().channelGetChannel(memberId));
		try {
			vc.upsertPermissionOverride(guild.getPublicRole()).deny(Permission.VOICE_CONNECT).queue();
		} catch (InsufficientPermissionException ex) {
			hook.editOriginal(bot.getEmbedUtil().getPermError(event, ex.getPermission(), true)).queue();
			return;
		}

		hook.editOriginal(MessageEditData.fromEmbeds(
			bot.getEmbedUtil().getEmbed(event)
				.setDescription(bot.getMsg(guildId, "bot.voice.lock.done"))
				.build()
		)).queue();
	}
}
