package bot.commands.voice;

import java.util.Objects;

import com.jagrosh.jdautilities.command.SlashCommand;
import com.jagrosh.jdautilities.command.SlashCommandEvent;
import com.jagrosh.jdautilities.doc.standard.CommandInfo;

import bot.App;
import bot.utils.exception.CheckException;
import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.channel.concrete.VoiceChannel;
import net.dv8tion.jda.api.exceptions.InsufficientPermissionException;
import net.dv8tion.jda.api.interactions.InteractionHook;

@CommandInfo(
	name = "unghost",
	description = "Unhide voice channel from @everyone",
	usage = "/unghost",
	requirements = "Must have created voice channel"
)
public class UnghostCmd extends SlashCommand {
	
	private final App bot;
	private static final String MODULE = "voice";

	protected Permission[] botPerms;

	public UnghostCmd(App bot) {
		this.name = "unghost";
		this.help = bot.getMsg("bot.voice.unghost.help");
		this.category = new Category("voice");
		this.botPerms = new Permission[]{Permission.MANAGE_ROLES, Permission.MANAGE_PERMISSIONS, Permission.VIEW_CHANNEL};
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
			bot.getCheckUtil().moduleEnabled(event, guildId, MODULE)
				.hasPermissions(event.getTextChannel(), member, true, botPerms)
				.guildExists(event, guildId);
		} catch (CheckException ex) {
			hook.editOriginal(ex.getEditData()).queue();
			return;
		}

		if (bot.getDBUtil().isVoiceChannel(member.getId())) {
			VoiceChannel vc = guild.getVoiceChannelById(bot.getDBUtil().channelGetChannel(member.getId()));
			try {
				vc.upsertPermissionOverride(guild.getPublicRole()).clear(Permission.VIEW_CHANNEL).queue();
			} catch (InsufficientPermissionException ex) {
				hook.editOriginal(bot.getEmbedUtil().getPermError(event.getTextChannel(), member, ex.getPermission(), true)).queue();
				return;
			}

			hook.editOriginalEmbeds(
				bot.getEmbedUtil().getEmbed(member)
					.setDescription(bot.getMsg(guildId, "bot.voice.unghost.done"))
					.build()
			).queue();
		} else {
			hook.editOriginal(bot.getEmbedUtil().getError(event, "errors.no_channel")).queue();
		}
	}
}
