package com.github.fileeditor97.votl.commands.voice;

import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumSet;
import java.util.List;
import java.util.Objects;

import com.jagrosh.jdautilities.doc.standard.CommandInfo;

import com.github.fileeditor97.votl.App;
import com.github.fileeditor97.votl.objects.CmdModule;
import com.github.fileeditor97.votl.objects.command.SlashCommand;
import com.github.fileeditor97.votl.objects.command.SlashCommandEvent;
import com.github.fileeditor97.votl.objects.constants.CmdCategory;
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
	name = "reject",
	description = "Withdraw the user permission to join your channel.",
	usage = "/reject <mention:user/-s role/-s>",
	requirements = "Must have created voice channel"
)
public class RejectCmd extends SlashCommand {

	public RejectCmd(App bot) {
		this.name = "reject";
		this.helpPath = "bot.voice.reject.help";
		this.options = Collections.singletonList(
			new OptionData(OptionType.STRING, "mention", bot.getLocaleUtil().getText("bot.voice.reject.option_description"))
				.setRequired(true)
		);
		this.botPermissions = new Permission[]{Permission.MANAGE_ROLES, Permission.MANAGE_PERMISSIONS, Permission.VOICE_MOVE_OTHERS};
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

		VoiceChannel vc = guild.getVoiceChannelById(bot.getDBUtil().channelGetChannel(authorId));

		List<Member> members = filMentions.getMembers();
		List<Role> roles = filMentions.getRoles();
		if (members.isEmpty() & roles.isEmpty()) {
			hook.editOriginal(bot.getEmbedUtil().getError(event, "bot.voice.reject.invalid_args")).queue();
			return;
		}
		if (members.contains(author) || members.contains(guild.getSelfMember())) {
			hook.editOriginal(bot.getEmbedUtil().getError(event, "bot.voice.reject.not_self")).queue();
			return;
		}

		List<String> mentionStrings = new ArrayList<>();

		for (Member member : members) {
			try {
				vc.getManager().putPermissionOverride(member, null, EnumSet.of(Permission.VOICE_CONNECT)).queue();
				mentionStrings.add(member.getEffectiveName());
			} catch (InsufficientPermissionException ex) {
				hook.editOriginal(bot.getEmbedUtil().getPermError(event, author, Permission.MANAGE_PERMISSIONS, true)).queue();
				return;
			}
			if (vc.getMembers().contains(member)) {
				guild.kickVoiceMember(member).queue();
			}
		}

		for (Role role : roles) {
			if (!role.hasPermission(new Permission[]{Permission.ADMINISTRATOR, Permission.MANAGE_SERVER, Permission.MANAGE_PERMISSIONS, Permission.MANAGE_ROLES}))
				try {
					vc.getManager().putPermissionOverride(role, null, EnumSet.of(Permission.VOICE_CONNECT)).queue();
					mentionStrings.add(role.getName());
				} catch (InsufficientPermissionException ex) {
					hook.editOriginal(bot.getEmbedUtil().getPermError(event, author, Permission.MANAGE_PERMISSIONS, true)).queue();
					return;
				}
		}

		hook.editOriginalEmbeds(
			bot.getEmbedUtil().getEmbed(event)
				.setDescription(lu.getLocalized(userLocale, "bot.voice.reject.done", "", mentionStrings))
				.build()
		).queue();
	}
}
