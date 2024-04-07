package dev.fileeditor.votl.commands.voice;

import java.util.EnumSet;
import java.util.Objects;

import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.channel.concrete.VoiceChannel;
import net.dv8tion.jda.api.entities.channel.middleman.AudioChannel;
import net.dv8tion.jda.api.exceptions.InsufficientPermissionException;

import dev.fileeditor.votl.App;
import dev.fileeditor.votl.commands.CommandBase;
import dev.fileeditor.votl.objects.CmdModule;
import dev.fileeditor.votl.objects.command.SlashCommandEvent;
import dev.fileeditor.votl.objects.constants.CmdCategory;

public class ClaimCmd extends CommandBase {

	public ClaimCmd(App bot) {
		super(bot);
		this.name = "claim";
		this.path = "bot.voice.claim";
		this.botPermissions = new Permission[]{Permission.MANAGE_ROLES, Permission.MANAGE_PERMISSIONS};
		this.category = CmdCategory.VOICE;
		this.module = CmdModule.VOICE;
		this.mustSetup = true;
	}

	@Override
	protected void execute(SlashCommandEvent event) {
		event.deferReply(true).queue();

		Member author = Objects.requireNonNull(event.getMember());

		if (!author.getVoiceState().inAudioChannel()) {
			editError(event, path+".not_in_voice");
			return;
		}

		AudioChannel ac = author.getVoiceState().getChannel();
		if (!bot.getDBUtil().voice.existsChannel(ac.getId())) {
			editError(event, path+".not_custom");
			return;
		}

		Guild guild = Objects.requireNonNull(event.getGuild());

		VoiceChannel vc = guild.getVoiceChannelById(ac.getId());
		guild.retrieveMemberById(bot.getDBUtil().voice.getUser(vc.getId())).queue(
			owner -> {
				for (Member vcMember : vc.getMembers()) {
					if (vcMember == owner) {
						editHook(event, lu.getText(event, path+".has_owner"));
						return;
					}
				}

				try {
					vc.getManager().removePermissionOverride(owner).queue();
					vc.getManager().putPermissionOverride(author, EnumSet.of(Permission.MANAGE_CHANNEL), null).queue();
				} catch (InsufficientPermissionException ex) {
					editPermError(event, author, ex.getPermission(), true);
					return;
				}
				bot.getDBUtil().voice.setUser(author.getId(), vc.getId());
				
				editHookEmbed(event, 
					bot.getEmbedUtil().getEmbed(event)
						.setDescription(lu.getText(event, path+".done").replace("{channel}", vc.getAsMention()))
						.build()
				);
			}, failure -> {
				editError(event, "errors.unknown", failure.getMessage());
			}
		);
	}

}
