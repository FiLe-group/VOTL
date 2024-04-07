package dev.fileeditor.votl.commands.voice;

import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumSet;
import java.util.List;
import java.util.Objects;

import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.Mentions;
import net.dv8tion.jda.api.entities.Role;
import net.dv8tion.jda.api.entities.channel.concrete.VoiceChannel;
import net.dv8tion.jda.api.exceptions.InsufficientPermissionException;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;

import dev.fileeditor.votl.App;
import dev.fileeditor.votl.commands.CommandBase;
import dev.fileeditor.votl.objects.CmdModule;
import dev.fileeditor.votl.objects.command.SlashCommandEvent;
import dev.fileeditor.votl.objects.constants.CmdCategory;

public class PermitCmd extends CommandBase {

	public PermitCmd(App bot) {
		super(bot);
		this.name = "permit";
		this.path = "bot.voice.permit";
		this.options = Collections.singletonList(
			new OptionData(OptionType.STRING, "mention", lu.getText(path+".option_mention"), true)
		);
		this.botPermissions = new Permission[]{Permission.MANAGE_ROLES, Permission.MANAGE_PERMISSIONS, Permission.VIEW_CHANNEL, Permission.VOICE_CONNECT};
		this.category = CmdCategory.VOICE;
		this.module = CmdModule.VOICE;
		this.mustSetup = true;
	}

	@Override
	protected void execute(SlashCommandEvent event) {
		event.deferReply(true).queue();

		Member author = Objects.requireNonNull(event.getMember());
		String authorId = author.getId();

		if (!bot.getDBUtil().voice.existsUser(authorId)) {
			editError(event, "errors.no_channel");
			return;
		}

		Mentions filMentions = event.optMentions("mention");
		if (filMentions == null) {
			editError(event, path+".invalid_args");
			return;
		}

		Guild guild = Objects.requireNonNull(event.getGuild());

		List<Member> members = filMentions.getMembers();
		List<Role> roles = filMentions.getRoles();
		if (members.isEmpty() & roles.isEmpty()) {
			editError(event, path+".invalid_args");
			return;
		}
		if (members.contains(author) || members.contains(guild.getSelfMember())) {
			editError(event, path+".not_self");
			return;
		}

		List<String> mentionStrings = new ArrayList<>();
		VoiceChannel vc = guild.getVoiceChannelById(bot.getDBUtil().voice.getChannel(authorId));

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
				.setDescription(lu.getUserText(event, path+".done", mentionStrings))
				.build()
		);
	}

}
