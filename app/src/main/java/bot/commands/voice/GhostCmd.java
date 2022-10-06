package bot.commands.voice;

import java.util.Objects;

import com.jagrosh.jdautilities.command.SlashCommand;
import com.jagrosh.jdautilities.command.SlashCommandEvent;
import com.jagrosh.jdautilities.doc.standard.CommandInfo;

import bot.App;
import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.channel.concrete.VoiceChannel;
import net.dv8tion.jda.api.exceptions.InsufficientPermissionException;
import net.dv8tion.jda.api.interactions.InteractionHook;
import net.dv8tion.jda.api.utils.messages.MessageCreateData;
import net.dv8tion.jda.api.utils.messages.MessageEditData;

@CommandInfo(
	name = "ghost",
	description = "Hides voice channel from @everyone",
	usage = "/ghost",
	requirements = "Must have created voice channel"
)
public class GhostCmd extends SlashCommand {
	
	private final App bot;

	protected Permission[] botPerms;

	public GhostCmd(App bot) {
		this.name = "ghost";
		this.help = bot.getMsg("bot.voice.ghost.help");
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

		MessageCreateData permission = bot.getCheckUtil().lacksPermissions(event.getTextChannel(), member, true, botPerms);
			if (permission != null) {
				hook.editOriginal(MessageEditData.fromCreateData(permission)).queue();
				return;
			}

		Guild guild = Objects.requireNonNull(event.getGuild());
		String guildId = guild.getId();

		if (!bot.getDBUtil().isGuild(guildId)) {
			hook.editOriginal(MessageEditData.fromCreateData(bot.getEmbedUtil().getError(event, "errors.guild_not_setup"))).queue();
			return;
		}

		if (bot.getDBUtil().isVoiceChannel(member.getId())) {
			VoiceChannel vc = guild.getVoiceChannelById(bot.getDBUtil().channelGetChannel(member.getId()));
			try {
				vc.upsertPermissionOverride(guild.getPublicRole()).deny(Permission.VIEW_CHANNEL).queue();
			} catch (InsufficientPermissionException ex) {
				hook.editOriginal(MessageEditData.fromCreateData(bot.getEmbedUtil().getPermError(event.getTextChannel(), member, ex.getPermission(), true))).queue();
				return;
			}

			hook.editOriginalEmbeds(
				bot.getEmbedUtil().getEmbed(member)
					.setDescription(bot.getMsg(guildId, "bot.voice.ghost.done"))
					.build()
			).queue();
		} else {
			hook.editOriginal(MessageEditData.fromCreateData(bot.getEmbedUtil().getError(event, "errors.no_channel"))).queue();
			return;
		}
	}
}
