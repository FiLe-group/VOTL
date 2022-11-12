package com.github.fileeditor97.votl.commands.voice;

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
import net.dv8tion.jda.api.entities.channel.concrete.VoiceChannel;
import net.dv8tion.jda.api.exceptions.InsufficientPermissionException;
import net.dv8tion.jda.api.interactions.DiscordLocale;
import net.dv8tion.jda.api.interactions.InteractionHook;

@CommandInfo(
	name = "unghost",
	description = "Unhide voice channel from @everyone",
	usage = "/unghost",
	requirements = "Must have created voice channel"
)
public class UnghostCmd extends SlashCommand {

	public UnghostCmd(App bot) {
		this.name = "unghost";
		this.helpPath = "bot.voice.unghost.help";
		this.botPermissions = new Permission[]{Permission.MANAGE_ROLES, Permission.MANAGE_PERMISSIONS, Permission.VIEW_CHANNEL};
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

		Member member = Objects.requireNonNull(event.getMember());
		String memberId = member.getId();

		if (!bot.getDBUtil().isVoiceChannel(memberId)) {
			hook.editOriginal(bot.getEmbedUtil().getError(event, "errors.no_channel")).queue();
			return;
		}

		Guild guild = Objects.requireNonNull(event.getGuild());
		DiscordLocale userLocale = event.getUserLocale();

		VoiceChannel vc = guild.getVoiceChannelById(bot.getDBUtil().channelGetChannel(memberId));
		try {
			vc.upsertPermissionOverride(guild.getPublicRole()).clear(Permission.VIEW_CHANNEL).queue();
		} catch (InsufficientPermissionException ex) {
			hook.editOriginal(bot.getEmbedUtil().getPermError(event, member, ex.getPermission(), true)).queue();
			return;
		}

		hook.editOriginalEmbeds(
			bot.getEmbedUtil().getEmbed(event)
				.setDescription(lu.getLocalized(userLocale, "bot.voice.unghost.done"))
				.build()
		).queue();
	}
}
