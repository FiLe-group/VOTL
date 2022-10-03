package bot.commands.voice;

import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumSet;
import java.util.List;

import com.jagrosh.jdautilities.command.SlashCommand;
import com.jagrosh.jdautilities.command.SlashCommandEvent;
import com.jagrosh.jdautilities.doc.standard.CommandInfo;

import bot.App;
import net.dv8tion.jda.api.Permission;
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
	name = "reject",
	description = "Withdraw the user permission to join your channel.",
	usage = "/reject <mention:user/-s role/-s>",
	requirements = "Must have created voice channel"
)
public class RejectCmd extends SlashCommand {
	
	private final App bot;

	protected Permission[] botPerms;

	public RejectCmd(App bot) {
		this.name = "reject";
		this.help = bot.getMsg("bot.voice.reject.description");
		this.category = new Category("voice");
		this.botPerms = new Permission[]{Permission.MANAGE_ROLES, Permission.MANAGE_PERMISSIONS, Permission.VOICE_MOVE_OTHERS};
		this.options = Collections.singletonList(
			new OptionData(OptionType.STRING, "mention", bot.getMsg("bot.voice.reject.option_description"))
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

	private MessageEditData getReply(SlashCommandEvent event, Mentions filMentions) {
		MessageCreateData permission = bot.getCheckUtil().lacksPermissions(event.getTextChannel(), event.getMember(), true, botPerms);
		if (permission != null)
			return MessageEditData.fromCreateData(permission);

		if (!bot.getDBUtil().isGuild(event.getGuild().getId())) {
			return MessageEditData.fromCreateData(bot.getEmbedUtil().getError(event, "errors.guild_not_setup"));
		}

		if (bot.getDBUtil().isVoiceChannel(event.getMember().getId())) {
			VoiceChannel vc = event.getGuild().getVoiceChannelById(bot.getDBUtil().channelGetChannel(event.getMember().getId()));

			List<Member> members = filMentions.getMembers();
			List<Role> roles = filMentions.getRoles();
			if (members.isEmpty() & roles.isEmpty()) {
				return MessageEditData.fromCreateData(bot.getEmbedUtil().getError(event, "bot.voice.reject.invalid_args"));
			}
			if (members.contains(event.getMember())) {
				return MessageEditData.fromCreateData(bot.getEmbedUtil().getError(event, "bot.voice.reject.not_self"));
			}

			List<String> mentionStrings = new ArrayList<>();

			for (Member member : members) {
				try {
					vc.getManager().putMemberPermissionOverride(member.getIdLong(), EnumSet.of(Permission.VOICE_CONNECT), null).queue();
					mentionStrings.add(member.getEffectiveName());
				} catch (InsufficientPermissionException ex) {
					return MessageEditData.fromCreateData(bot.getEmbedUtil().getPermError(event.getTextChannel(), event.getMember(), Permission.MANAGE_PERMISSIONS, true));
				}
				if (vc.getMembers().contains(member)) {
					event.getGuild().kickVoiceMember(member).queue();
				}
			}

			for (Role role : roles) {
				if (!role.hasPermission(new Permission[]{Permission.ADMINISTRATOR, Permission.MANAGE_SERVER, Permission.MANAGE_PERMISSIONS, Permission.MANAGE_ROLES}))
					try {
						vc.getManager().putRolePermissionOverride(role.getIdLong(), EnumSet.of(Permission.VOICE_CONNECT), null).queue();
						mentionStrings.add(role.getName());
					} catch (InsufficientPermissionException ex) {
						return MessageEditData.fromCreateData(bot.getEmbedUtil().getPermError(event.getTextChannel(), event.getMember(), Permission.MANAGE_PERMISSIONS, true));
					}
			}

			return MessageEditData.fromEmbeds(
				bot.getEmbedUtil().getEmbed(event.getMember())
					.setDescription(bot.getMsg(event.getGuild().getId(), "bot.voice.reject.done", "", mentionStrings))
					.build()
			);
		} else {
			return MessageEditData.fromContent(bot.getMsg(event.getGuild().getId(), "bot.voice.reject.no_channel"));
		}
	}
}
