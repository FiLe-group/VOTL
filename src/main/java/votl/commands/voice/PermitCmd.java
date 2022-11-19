package votl.commands.voice;

import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumSet;
import java.util.List;
import java.util.Objects;

import votl.App;
import votl.commands.CommandBase;
import votl.objects.CmdModule;
import votl.objects.command.SlashCommandEvent;
import votl.objects.constants.CmdCategory;

import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.Mentions;
import net.dv8tion.jda.api.entities.Role;
import net.dv8tion.jda.api.entities.channel.concrete.VoiceChannel;
import net.dv8tion.jda.api.exceptions.InsufficientPermissionException;
import net.dv8tion.jda.api.interactions.DiscordLocale;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;

import com.jagrosh.jdautilities.doc.standard.CommandInfo;
@CommandInfo(
	name = "permit",
	description = "Gives the user permission to join your channel.",
	usage = "/permit <mention:user/-s role/-s>",
	requirements = "Must have created voice channel"
)
public class PermitCmd extends CommandBase {

	public PermitCmd(App bot) {
		super(bot);
		this.name = "permit";
		this.path = "bot.voice.permit";
		this.options = Collections.singletonList(
			new OptionData(OptionType.STRING, "mention", lu.getText(path+".option_description"), true)
		);
		this.botPermissions = new Permission[]{Permission.MANAGE_ROLES, Permission.MANAGE_PERMISSIONS, Permission.VIEW_CHANNEL, Permission.VOICE_CONNECT};
		this.category = CmdCategory.VOICE;
		this.module = CmdModule.VOICE;
		this.mustSetup = true;
	}

	@SuppressWarnings("null")
	@Override
	protected void execute(SlashCommandEvent event) {
		event.deferReply(true).queue();

		Member author = Objects.requireNonNull(event.getMember());
		String authorId = author.getId();

		if (!bot.getDBUtil().isVoiceChannel(authorId)) {
			editError(event, "errors.no_channel");
			return;
		}

		Mentions filMentions = event.optMentions("mention");
		if (filMentions == null) {
			editError(event, "bot.voice.permit.invalid_args");
			return;
		}

		Guild guild = Objects.requireNonNull(event.getGuild());
		DiscordLocale userLocale = event.getUserLocale();

		List<Member> members = filMentions.getMembers();
		List<Role> roles = filMentions.getRoles();
		if (members.isEmpty() & roles.isEmpty()) {
			editError(event, "bot.voice.permit.invalid_args");
			return;
		}
		if (members.contains(author) || members.contains(guild.getSelfMember())) {
			editError(event, "bot.voice.permit.not_self");
			return;
		}

		List<String> mentionStrings = new ArrayList<>();
		VoiceChannel vc = guild.getVoiceChannelById(bot.getDBUtil().channelGetChannel(authorId));

		for (Member member : members) {
			try {
				vc.getManager().putPermissionOverride(member, EnumSet.of(Permission.VOICE_CONNECT, Permission.VIEW_CHANNEL), null).queue();
				mentionStrings.add(member.getEffectiveName());
			} catch (InsufficientPermissionException ex) {
				editPermError(event, author, Permission.MANAGE_PERMISSIONS, true);
				return;
			}
		}

		for (Role role : roles) {
			if (!role.hasPermission(new Permission[]{Permission.ADMINISTRATOR, Permission.MANAGE_SERVER, Permission.MANAGE_PERMISSIONS, Permission.MANAGE_ROLES}))
				try {
					vc.getManager().putPermissionOverride(role, EnumSet.of(Permission.VOICE_CONNECT, Permission.VIEW_CHANNEL), null).queue();
					mentionStrings.add(role.getName());
				} catch (InsufficientPermissionException ex) {
					editPermError(event, author, Permission.MANAGE_PERMISSIONS, true);
					return;
				}
		}

		editHookEmbed(event,
			bot.getEmbedUtil().getEmbed(event)
				.setDescription(lu.getLocalized(userLocale, "bot.voice.permit.done", "", mentionStrings))
				.build()
		);
	}

}
