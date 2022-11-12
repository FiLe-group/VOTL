package com.github.fileeditor97.votl.commands.voice;

import java.util.EnumSet;
import java.util.Objects;

import com.jagrosh.jdautilities.doc.standard.CommandInfo;

import com.github.fileeditor97.votl.App;
import com.github.fileeditor97.votl.objects.CmdModule;
import com.github.fileeditor97.votl.objects.command.SlashCommand;
import com.github.fileeditor97.votl.objects.command.SlashCommandEvent;
import com.github.fileeditor97.votl.objects.constants.CmdCategory;
import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.entities.channel.middleman.AudioChannel;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.channel.concrete.VoiceChannel;
import net.dv8tion.jda.api.exceptions.InsufficientPermissionException;
import net.dv8tion.jda.api.interactions.DiscordLocale;
import net.dv8tion.jda.api.interactions.InteractionHook;

@CommandInfo(
	name = "Claim",
	description = "Claim ownership of channel once owner has left.",
	usage = "/claim",
	requirements = "Must be in un-owned custom voice channel"
)
public class ClaimCmd extends SlashCommand {

	public ClaimCmd(App bot) {
		this.name = "claim";
		this.helpPath = "bot.voice.claim.help";
		this.botPermissions = new Permission[]{Permission.MANAGE_ROLES, Permission.MANAGE_PERMISSIONS};
		this.bot = bot;
		this.category = CmdCategory.VOICE;
		this.module = CmdModule.VOICE;
		this.mustSetup = true;
	}

	@Override
	protected void execute(SlashCommandEvent event) {

		event.deferReply(true).queue(
			hook -> {
				sendReply(event, hook);
			}
		);

	}

	@SuppressWarnings("null")
	private void sendReply(SlashCommandEvent event, InteractionHook hook) {

		Member author = Objects.requireNonNull(event.getMember());

		if (!author.getVoiceState().inAudioChannel()) {
			hook.editOriginal(bot.getEmbedUtil().getError(event, "bot.voice.claim.not_in_voice")).queue();
			return;
		}

		AudioChannel ac = author.getVoiceState().getChannel();
		if (!bot.getDBUtil().isVoiceChannelExists(ac.getId())) {
			hook.editOriginal(bot.getEmbedUtil().getError(event, "bot.voice.claim.not_custom")).queue();
			return;
		}

		Guild guild = Objects.requireNonNull(event.getGuild());
		DiscordLocale userLocale = event.getUserLocale();

		VoiceChannel vc = guild.getVoiceChannelById(ac.getId());
		guild.retrieveMemberById(bot.getDBUtil().channelGetUser(vc.getId())).queue(
			owner -> {
				for (Member vcMember : vc.getMembers()) {
					if (vcMember == owner) {
						hook.editOriginal(lu.getLocalized(userLocale, "bot.voice.claim.has_owner")).queue();
						return;
					}
				}

				try {
					vc.getManager().removePermissionOverride(owner).queue();
					vc.getManager().putPermissionOverride(author, EnumSet.of(Permission.MANAGE_CHANNEL), null).queue();
				} catch (InsufficientPermissionException ex) {
					hook.editOriginal(bot.getEmbedUtil().getPermError(event, author, ex.getPermission(), true)).queue();
					return;
				}
				bot.getDBUtil().channelSetUser(author.getId(), vc.getId());
				
				hook.editOriginalEmbeds(
					bot.getEmbedUtil().getEmbed(event)
						.setDescription(lu.getLocalized(userLocale, "bot.voice.claim.done").replace("{channel}", vc.getAsMention()))
						.build()
				).queue();
			}, failure -> {
				hook.editOriginal(bot.getEmbedUtil().getError(event, "errors.unknown", failure.getMessage())).queue();
			}
		);

		
	}
}
