package bot.commands.voice;

import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumSet;
import java.util.List;
import java.util.Objects;

import javax.annotation.Nonnull;

import com.jagrosh.jdautilities.command.SlashCommand;
import com.jagrosh.jdautilities.command.SlashCommandEvent;
import com.jagrosh.jdautilities.doc.standard.CommandInfo;

import bot.App;
import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.Mentions;
import net.dv8tion.jda.api.entities.Role;
import net.dv8tion.jda.api.entities.channel.concrete.VoiceChannel;
import net.dv8tion.jda.api.exceptions.InsufficientPermissionException;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import net.dv8tion.jda.api.utils.messages.MessageCreateData;
import net.dv8tion.jda.api.utils.messages.MessageEditData;

@CommandInfo(
	name = "permit",
	description = "Gives the user permission to join your channel.",
	usage = "/permit <mention:user/-s role/-s>",
	requirements = "Must have created voice channel"
)
public class PermitCmd extends SlashCommand {
	
	private final App bot;

	protected Permission[] botPerms;

	public PermitCmd(App bot) {
		this.name = "permit";
		this.help = bot.getMsg("bot.voice.permit.description");
		this.category = new Category("voice");
		this.botPerms = new Permission[]{Permission.MANAGE_ROLES, Permission.MANAGE_PERMISSIONS};
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
				MessageEditData reply = getReply(event, filMentions);

				hook.editOriginal(reply).queue();
			}
		);

	}

	@SuppressWarnings("null")
	@Nonnull
	private MessageEditData getReply(SlashCommandEvent event, Mentions filMentions) {

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

			List<Member> members = filMentions.getMembers();
			List<Role> roles = filMentions.getRoles();
			if (members.isEmpty() & roles.isEmpty()) {
				return MessageEditData.fromCreateData(bot.getEmbedUtil().getError(event, "bot.voice.permit.invalid_args"));
			}
			if (members.contains(member)) {
				return MessageEditData.fromCreateData(bot.getEmbedUtil().getError(event, "bot.voice.permit.not_self"));
			}

			List<String> mentionStrings = new ArrayList<>();

			for (Member xMember : members) {
				try {
					vc.getManager().putMemberPermissionOverride(xMember.getIdLong(), EnumSet.of(Permission.VOICE_CONNECT), null).queue();
					mentionStrings.add(xMember.getEffectiveName());
				} catch (InsufficientPermissionException ex) {
					return MessageEditData.fromCreateData(bot.getEmbedUtil().getPermError(event.getTextChannel(), member, Permission.MANAGE_PERMISSIONS, true));
				}
			}

			for (Role role : roles) {
				if (!role.hasPermission(new Permission[]{Permission.ADMINISTRATOR, Permission.MANAGE_SERVER, Permission.MANAGE_PERMISSIONS, Permission.MANAGE_ROLES}))
					try {
						vc.getManager().putRolePermissionOverride(role.getIdLong(), EnumSet.of(Permission.VOICE_CONNECT), null).queue();
						mentionStrings.add(role.getName());
					} catch (InsufficientPermissionException ex) {
						return MessageEditData.fromCreateData(bot.getEmbedUtil().getPermError(event.getTextChannel(), member, Permission.MANAGE_PERMISSIONS, true));
					}
			}

			return MessageEditData.fromEmbeds(
				bot.getEmbedUtil().getEmbed(member)
					.setDescription(bot.getMsg(guildId, "bot.voice.permit.done", "", mentionStrings))
					.build()
			);
		} else {
			return MessageEditData.fromContent(bot.getMsg(guildId, "bot.voice.permit.no_channel"));
		}
	}
}
