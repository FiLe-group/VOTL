package bot.commands.voice;

import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumSet;
import java.util.List;
import java.util.Objects;

import com.jagrosh.jdautilities.doc.standard.CommandInfo;

import bot.App;
import bot.objects.CmdModule;
import bot.objects.command.SlashCommand;
import bot.objects.command.SlashCommandEvent;
import bot.objects.constants.CmdCategory;
import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.Mentions;
import net.dv8tion.jda.api.entities.Role;
import net.dv8tion.jda.api.entities.channel.concrete.VoiceChannel;
import net.dv8tion.jda.api.exceptions.InsufficientPermissionException;
import net.dv8tion.jda.api.interactions.DiscordLocale;
import net.dv8tion.jda.api.interactions.InteractionHook;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;

@CommandInfo(
	name = "permit",
	description = "Gives the user permission to join your channel.",
	usage = "/permit <mention:user/-s role/-s>",
	requirements = "Must have created voice channel"
)
public class PermitCmd extends SlashCommand {

	public PermitCmd(App bot) {
		this.name = "permit";
		this.helpPath = "bot.voice.permit.help";
		this.options = Collections.singletonList(
			new OptionData(OptionType.STRING, "mention", bot.getLocaleUtil().getText("bot.voice.permit.option_description"))
				.setRequired(true)
		);
		this.botPermissions = new Permission[]{Permission.MANAGE_ROLES, Permission.MANAGE_PERMISSIONS, Permission.VIEW_CHANNEL, Permission.VOICE_CONNECT};
		this.bot = bot;
		this.category = CmdCategory.VOICE;
		this.module = CmdModule.VOICE;
		this.mustSetup = true;
	}

	@Override
	protected void execute(SlashCommandEvent event) {

		event.deferReply(true).queue(
			hook -> {
				Mentions filMentions = event.getOption("mention", OptionMapping::getMentions);
				sendReply(event, hook, filMentions);
			}
		);

	}

	@SuppressWarnings("null")
	private void sendReply(SlashCommandEvent event, InteractionHook hook, Mentions filMentions) {

		Member author = Objects.requireNonNull(event.getMember());
		String authorId = author.getId();

		if (!bot.getDBUtil().isVoiceChannel(authorId)) {
			hook.editOriginal(bot.getEmbedUtil().getError(event, "errors.no_channel")).queue();
			return;
		}

		Guild guild = Objects.requireNonNull(event.getGuild());
		DiscordLocale userLocale = event.getUserLocale();

		List<Member> members = filMentions.getMembers();
		List<Role> roles = filMentions.getRoles();
		if (members.isEmpty() & roles.isEmpty()) {
			hook.editOriginal(bot.getEmbedUtil().getError(event, "bot.voice.permit.invalid_args")).queue();
			return;
		}
		if (members.contains(author) || members.contains(guild.getSelfMember())) {
			hook.editOriginal(bot.getEmbedUtil().getError(event, "bot.voice.permit.not_self")).queue();
			return;
		}

		List<String> mentionStrings = new ArrayList<>();
		VoiceChannel vc = guild.getVoiceChannelById(bot.getDBUtil().channelGetChannel(authorId));

		for (Member member : members) {
			try {
				vc.getManager().putPermissionOverride(member, EnumSet.of(Permission.VOICE_CONNECT, Permission.VIEW_CHANNEL), null).queue();
				mentionStrings.add(member.getEffectiveName());
			} catch (InsufficientPermissionException ex) {
				hook.editOriginal(bot.getEmbedUtil().getPermError(event, author, Permission.MANAGE_PERMISSIONS, true)).queue();
				return;
			}
		}

		for (Role role : roles) {
			if (!role.hasPermission(new Permission[]{Permission.ADMINISTRATOR, Permission.MANAGE_SERVER, Permission.MANAGE_PERMISSIONS, Permission.MANAGE_ROLES}))
				try {
					vc.getManager().putPermissionOverride(role, EnumSet.of(Permission.VOICE_CONNECT, Permission.VIEW_CHANNEL), null).queue();
					mentionStrings.add(role.getName());
				} catch (InsufficientPermissionException ex) {
					hook.editOriginal(bot.getEmbedUtil().getPermError(event, author, Permission.MANAGE_PERMISSIONS, true)).queue();
					return;
				}
		}

		hook.editOriginalEmbeds(
			bot.getEmbedUtil().getEmbed(event)
				.setDescription(lu.getLocalized(userLocale, "bot.voice.permit.done", "", mentionStrings))
				.build()
		).queue();
	}
}
