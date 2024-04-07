package dev.fileeditor.votl.commands.voice;

import java.util.Objects;

import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.channel.concrete.VoiceChannel;
import net.dv8tion.jda.api.exceptions.InsufficientPermissionException;

import dev.fileeditor.votl.App;
import dev.fileeditor.votl.commands.CommandBase;
import dev.fileeditor.votl.objects.CmdModule;
import dev.fileeditor.votl.objects.command.SlashCommandEvent;
import dev.fileeditor.votl.objects.constants.CmdCategory;

public class GhostCmd extends CommandBase {

	public GhostCmd(App bot) {
		super(bot);
		this.name = "ghost";
		this.path = "bot.voice.ghost";
		this.botPermissions = new Permission[]{Permission.MANAGE_ROLES, Permission.MANAGE_PERMISSIONS, Permission.VIEW_CHANNEL};
		this.category = CmdCategory.VOICE;
		this.module = CmdModule.VOICE;
		this.mustSetup = true;
	}

	@Override
	protected void execute(SlashCommandEvent event) {
		Member member = Objects.requireNonNull(event.getMember());

		if (!bot.getDBUtil().voice.existsUser(member.getId())) {
			createError(event, "errors.no_channel");
			return;
		}

		Guild guild = Objects.requireNonNull(event.getGuild());

		VoiceChannel vc = guild.getVoiceChannelById(bot.getDBUtil().voice.getChannel(member.getId()));
		try {
			vc.upsertPermissionOverride(guild.getPublicRole()).deny(Permission.VIEW_CHANNEL).queue();
		} catch (InsufficientPermissionException ex) {
			createPermError(event, member, ex.getPermission(), true);
			return;
		}

		createReplyEmbed(event, 
			bot.getEmbedUtil().getEmbed(event)
				.setDescription(lu.getText(event, path+".done"))
				.build()
		);
	}

}
