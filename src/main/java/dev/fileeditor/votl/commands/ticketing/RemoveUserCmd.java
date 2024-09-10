package dev.fileeditor.votl.commands.ticketing;

import java.util.Collections;
import java.util.List;

import dev.fileeditor.votl.base.command.SlashCommandEvent;
import dev.fileeditor.votl.commands.CommandBase;
import dev.fileeditor.votl.objects.CmdAccessLevel;
import dev.fileeditor.votl.objects.CmdModule;
import dev.fileeditor.votl.objects.constants.CmdCategory;
import dev.fileeditor.votl.objects.constants.Constants;

import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.entities.channel.ChannelType;
import net.dv8tion.jda.api.exceptions.PermissionException;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;

public class RemoveUserCmd extends CommandBase {
	
	public RemoveUserCmd() {
		this.name = "remove";
		this.path = "bot.ticketing.remove";
		this.options = List.of(
			new OptionData(OptionType.USER, "user", lu.getText(path+".user.help"), true)
		);
		this.module = CmdModule.TICKETING;
		this.category = CmdCategory.TICKETING;
		this.accessLevel = CmdAccessLevel.HELPER;
	}

	@Override
	protected void execute(SlashCommandEvent event) {
		long channelId = event.getChannel().getIdLong();
		Long authorId = bot.getDBUtil().tickets.getUserId(channelId);
		if (authorId == null) {
			// If this channel is not a ticket
			createError(event, path+".not_ticket");
			return;
		}
		if (bot.getDBUtil().tickets.isClosed(channelId)) {
			// Ticket is closed
			event.getChannel().delete().queue();
			return;
		}
		User user = event.optUser("user");
		if (user.equals(event.getUser()) || user.equals(bot.JDA.getSelfUser()) || authorId.equals(user.getIdLong())) {
			createError(event, path+".not_self");
			return;
		}
		event.deferReply().queue();

		if (event.getChannelType().equals(ChannelType.GUILD_PRIVATE_THREAD)) {
			// Thread
			event.getChannel().asThreadChannel().removeThreadMember(user).queue(done -> {
				event.getHook().editOriginalEmbeds(bot.getEmbedUtil().getEmbed(Constants.COLOR_SUCCESS)
					.setDescription(lu.getText(event, path+".done").replace("{user}", user.getAsMention()))
					.build()
				).setAllowedMentions(Collections.emptyList()).queue();
			},
				failure -> editError(event, path+".failed", failure.getMessage()));
		} else {
			// TextChannel
			try {
				event.getChannel().asTextChannel().getManager()
					.removePermissionOverride(user.getIdLong())
					.queue(done -> event.getHook().editOriginalEmbeds(bot.getEmbedUtil().getEmbed(Constants.COLOR_SUCCESS)
						.setDescription(lu.getText(event, path+".done").replace("{user}", user.getAsMention()))
						.build()
					).setAllowedMentions(Collections.emptyList()).queue()
				);
			} catch (PermissionException ex) {
				editError(event, path+".failed", ex.getMessage());
			}
		}
	}

}
