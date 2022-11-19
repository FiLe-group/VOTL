package votl.commands.voice;

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
import net.dv8tion.jda.api.exceptions.InsufficientPermissionException;
import net.dv8tion.jda.api.interactions.DiscordLocale;

import com.jagrosh.jdautilities.doc.standard.CommandInfo;

@CommandInfo(
	name = "unghost",
	description = "Unhide voice channel from @everyone",
	usage = "/unghost",
	requirements = "Must have created voice channel"
)
public class UnghostCmd extends CommandBase {

	public UnghostCmd(App bot) {
		super(bot);
		this.name = "unghost";
		this.path = "bot.voice.unghost";
		this.botPermissions = new Permission[]{Permission.MANAGE_ROLES, Permission.MANAGE_PERMISSIONS, Permission.VIEW_CHANNEL};
		this.category = CmdCategory.VOICE;
		this.module = CmdModule.VOICE;
		this.mustSetup = true;
	}

	@SuppressWarnings("null")
	@Override
	protected void execute(SlashCommandEvent event) {
		Member member = Objects.requireNonNull(event.getMember());
		String memberId = member.getId();

		if (!bot.getDBUtil().isVoiceChannel(memberId)) {
			createError(event, "errors.no_channel");
			return;
		}

		Guild guild = Objects.requireNonNull(event.getGuild());
		DiscordLocale userLocale = event.getUserLocale();

		VoiceChannel vc = guild.getVoiceChannelById(bot.getDBUtil().channelGetChannel(memberId));
		try {
			vc.upsertPermissionOverride(guild.getPublicRole()).clear(Permission.VIEW_CHANNEL).queue();
		} catch (InsufficientPermissionException ex) {
			createPermError(event, member, ex.getPermission(), true);
			return;
		}

		createReplyEmbed(event,
			bot.getEmbedUtil().getEmbed(event)
				.setDescription(lu.getLocalized(userLocale, "bot.voice.unghost.done"))
				.build()
		);
	}

}
