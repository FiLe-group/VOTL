package bot.commands.voice;

import java.util.Objects;

import javax.annotation.Nonnull;

import com.jagrosh.jdautilities.command.SlashCommand;
import com.jagrosh.jdautilities.command.SlashCommandEvent;
import com.jagrosh.jdautilities.doc.standard.CommandInfo;

import bot.App;
import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.channel.concrete.VoiceChannel;
import net.dv8tion.jda.api.exceptions.InsufficientPermissionException;
import net.dv8tion.jda.api.utils.messages.MessageCreateData;
import net.dv8tion.jda.api.utils.messages.MessageEditData;

@CommandInfo(
	name = "lock",
	description = "Locks voice channel for @everyone (ex. allowed one).",
	usage = "/lock",
	requirements = "Must have created voice channel"
)
public class LockCmd extends SlashCommand {
	
	private final App bot;

	protected Permission[] botPerms;

	public LockCmd(App bot) {
		this.name = "lock";
		this.help = bot.getMsg("bot.voice.lock.help");
		this.category = new Category("voice");
		this.botPerms = new Permission[]{Permission.MANAGE_ROLES, Permission.MANAGE_PERMISSIONS, Permission.VOICE_CONNECT};
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

		if (bot.getDBUtil().isVoiceChannel(member.getId())) {
			VoiceChannel vc = guild.getVoiceChannelById(bot.getDBUtil().channelGetChannel(member.getId()));
			try {
				vc.upsertPermissionOverride(guild.getPublicRole()).deny(Permission.VOICE_CONNECT).queue();
			} catch (InsufficientPermissionException ex) {
				return MessageEditData.fromCreateData(bot.getEmbedUtil().getPermError(event.getTextChannel(), member, ex.getPermission(), true));
			}

			return MessageEditData.fromEmbeds(
				bot.getEmbedUtil().getEmbed(member)
					.setDescription(bot.getMsg(guildId, "bot.voice.lock.done"))
					.build()
			);
		} else {
			return MessageEditData.fromCreateData(bot.getEmbedUtil().getError(event, "errors.no_channel"));
		}
	}
}
