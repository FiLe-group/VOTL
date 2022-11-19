package votl.commands.voice;

import java.util.EnumSet;
import java.util.Objects;

import votl.App;
import votl.commands.CommandBase;
import votl.objects.CmdModule;
import votl.objects.command.SlashCommandEvent;
import votl.objects.constants.CmdCategory;

import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.channel.concrete.VoiceChannel;
import net.dv8tion.jda.api.entities.channel.middleman.AudioChannel;
import net.dv8tion.jda.api.exceptions.InsufficientPermissionException;
import net.dv8tion.jda.api.interactions.DiscordLocale;

import com.jagrosh.jdautilities.doc.standard.CommandInfo;

@CommandInfo(
	name = "Claim",
	description = "Claim ownership of channel once owner has left.",
	usage = "/claim",
	requirements = "Must be in un-owned custom voice channel"
)
public class ClaimCmd extends CommandBase {

	public ClaimCmd(App bot) {
		super(bot);
		this.name = "claim";
		this.path = "bot.voice.claim";
		this.botPermissions = new Permission[]{Permission.MANAGE_ROLES, Permission.MANAGE_PERMISSIONS};
		this.category = CmdCategory.VOICE;
		this.module = CmdModule.VOICE;
		this.mustSetup = true;
	}

	@SuppressWarnings("null")
	@Override
	protected void execute(SlashCommandEvent event) {
		event.deferReply(true).queue();

		Member author = Objects.requireNonNull(event.getMember());

		if (!author.getVoiceState().inAudioChannel()) {
			editError(event, "bot.voice.claim.not_in_voice");
			return;
		}

		AudioChannel ac = author.getVoiceState().getChannel();
		if (!bot.getDBUtil().isVoiceChannelExists(ac.getId())) {
			editError(event, "bot.voice.claim.not_custom");
			return;
		}

		Guild guild = Objects.requireNonNull(event.getGuild());
		DiscordLocale userLocale = event.getUserLocale();

		VoiceChannel vc = guild.getVoiceChannelById(ac.getId());
		guild.retrieveMemberById(bot.getDBUtil().channelGetUser(vc.getId())).queue(
			owner -> {
				for (Member vcMember : vc.getMembers()) {
					if (vcMember == owner) {
						editHook(event, lu.getLocalized(userLocale, "bot.voice.claim.has_owner"));
						return;
					}
				}

				try {
					vc.getManager().removePermissionOverride(owner).queue();
					vc.getManager().putPermissionOverride(author, EnumSet.of(Permission.MANAGE_CHANNEL), null).queue();
				} catch (InsufficientPermissionException ex) {
					editPermError(event, author, ex.getPermission(), true);
					return;
				}
				bot.getDBUtil().channelSetUser(author.getId(), vc.getId());
				
				editHookEmbed(event, 
					bot.getEmbedUtil().getEmbed(event)
						.setDescription(lu.getLocalized(userLocale, "bot.voice.claim.done").replace("{channel}", vc.getAsMention()))
						.build()
				);
			}, failure -> {
				editError(event, "errors.unknown", failure.getMessage());
			}
		);
	}

}
