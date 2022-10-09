package bot.commands.voice;

import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumSet;
import java.util.List;
import java.util.Objects;

import com.jagrosh.jdautilities.command.SlashCommand;
import com.jagrosh.jdautilities.command.SlashCommandEvent;
import com.jagrosh.jdautilities.doc.standard.CommandInfo;

import bot.App;
import bot.utils.exception.CheckException;
import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.Mentions;
import net.dv8tion.jda.api.entities.Role;
import net.dv8tion.jda.api.entities.channel.concrete.VoiceChannel;
import net.dv8tion.jda.api.exceptions.InsufficientPermissionException;
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
	
	private final App bot;
	private static final String MODULE = "voice";

	protected Permission[] botPerms;

	public PermitCmd(App bot) {
		this.name = "permit";
		this.help = bot.getMsg("bot.voice.permit.help");
		this.category = new Category("voice");
		this.botPerms = new Permission[]{Permission.MANAGE_ROLES, Permission.MANAGE_PERMISSIONS, Permission.VIEW_CHANNEL, Permission.VOICE_CONNECT};
		this.options = Collections.singletonList(
			new OptionData(OptionType.STRING, "mention", bot.getMsg("bot.voice.permit.option_description"))
				.setRequired(true)
		);
		this.bot = bot;
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

			List<Member> members = filMentions.getMembers();
			List<Role> roles = filMentions.getRoles();
			if (members.isEmpty() & roles.isEmpty()) {
				hook.editOriginal(bot.getEmbedUtil().getError(event, "bot.voice.permit.invalid_args")).queue();
			}
			if (members.contains(member) || members.contains(guild.getSelfMember())) {
				hook.editOriginal(bot.getEmbedUtil().getError(event, "bot.voice.permit.not_self")).queue();
			}

			List<String> mentionStrings = new ArrayList<>();

			for (Member xMember : members) {
				try {
					vc.getManager().putMemberPermissionOverride(xMember.getIdLong(), EnumSet.of(Permission.VOICE_CONNECT, Permission.VIEW_CHANNEL), null).queue();
					mentionStrings.add(xMember.getEffectiveName());
				} catch (InsufficientPermissionException ex) {
					hook.editOriginal(bot.getEmbedUtil().getPermError(event.getTextChannel(), member, Permission.MANAGE_PERMISSIONS, true));
					return;
				}
			}

			for (Role role : roles) {
				if (!role.hasPermission(new Permission[]{Permission.ADMINISTRATOR, Permission.MANAGE_SERVER, Permission.MANAGE_PERMISSIONS, Permission.MANAGE_ROLES}))
					try {
						vc.getManager().putRolePermissionOverride(role.getIdLong(), EnumSet.of(Permission.VOICE_CONNECT, Permission.VIEW_CHANNEL), null).queue();
						mentionStrings.add(role.getName());
					} catch (InsufficientPermissionException ex) {
						hook.editOriginal(bot.getEmbedUtil().getPermError(event.getTextChannel(), member, Permission.MANAGE_PERMISSIONS, true)).queue();
						return;
					}
			}

			hook.editOriginalEmbeds(
				bot.getEmbedUtil().getEmbed(member)
					.setDescription(bot.getMsg(guildId, "bot.voice.permit.done", "", mentionStrings))
					.build()
			).queue();
		} else {
			hook.editOriginal(bot.getEmbedUtil().getError(event, "errors.no_channel")).queue();
		}
	}
}
