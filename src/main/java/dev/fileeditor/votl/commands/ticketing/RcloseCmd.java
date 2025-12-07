package dev.fileeditor.votl.commands.ticketing;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.stream.Stream;

import dev.fileeditor.votl.base.command.SlashCommand;
import dev.fileeditor.votl.base.command.SlashCommandEvent;
import dev.fileeditor.votl.objects.CmdAccessLevel;
import dev.fileeditor.votl.objects.CmdModule;
import dev.fileeditor.votl.objects.constants.CmdCategory;

import dev.fileeditor.votl.objects.constants.Limits;
import dev.fileeditor.votl.utils.message.TimeUtil;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.components.actionrow.ActionRow;
import net.dv8tion.jda.api.components.buttons.Button;
import net.dv8tion.jda.api.entities.*;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;

public class RcloseCmd extends SlashCommand {
	
	public RcloseCmd() {
		this.name = "rclose";
		this.path = "bot.ticketing.rclose";
		this.options = List.of(
			new OptionData(OptionType.STRING, "reason", lu.getText(path+".reason.help"))
				.setMaxLength(Limits.REASON_CHARS)
		);
		this.module = CmdModule.TICKETING;
		this.category = CmdCategory.TICKETING;
		this.accessLevel = CmdAccessLevel.HELPER;
	}

	@SuppressWarnings("FieldCanBeLocal")
	private final int CLOSE_AFTER_DELAY = 12; // hours

	@Override
	protected void execute(SlashCommandEvent event) {
		assert event.getGuild() != null & event.getMember() != null;
		long channelId = event.getChannel().getIdLong();
		Long authorId = bot.getDBUtil().tickets.getUserId(channelId);

		if (authorId == null) {
			// If this channel is not a ticket
			editError(event, path+".not_ticket");
			return;
		}
		if (bot.getDBUtil().tickets.isClosed(channelId)) {
			// Ticket is closed
			event.getChannel().delete().queue();
			return;
		}

		if (bot.getDBUtil().tickets.getTimeClosing(channelId) > 0) {
			// If request already exists (if there is no cancel button - GG)
			editError(event, path+".already_requested");
			return;
		}

		// Check access
		switch (bot.getDBUtil().getTicketSettings(event.getGuild()).getAllowClose()) {
			case EVERYONE -> {}
			case HELPER -> {
				// Check if user has Helper+ access
				if (!bot.getCheckUtil().hasAccess(event.getMember(), CmdAccessLevel.HELPER)) {
					// No access - reject
					editError(event, "errors.interaction.no_access", "Helper+ access");
					return;
				}
			}
			case SUPPORT -> {
				// Check if user is ticket support or has Admin+ access
				int tagId = bot.getDBUtil().tickets.getTag(channelId);
				if (tagId==0) {
					// Role request ticket
					List<Long> supportRoleIds = bot.getDBUtil().getTicketSettings(event.getGuild()).getRoleSupportIds();
					if (supportRoleIds.isEmpty()) supportRoleIds = bot.getDBUtil().access.getRoles(event.getGuild().getIdLong(), CmdAccessLevel.MOD);
					// Check
					if (denyCloseSupport(supportRoleIds, event.getMember())) {
						editError(event, "errors.interaction.no_access", "'Support' for this ticket or Admin+ access");
						return;
					}
				} else {
					// Standard ticket
					final List<Long> supportRoleIds = Stream.of(bot.getDBUtil().ticketTags.getSupportRolesString(tagId).split(";"))
						.map(Long::parseLong)
						.toList();
					// Check
					if (denyCloseSupport(supportRoleIds, event.getMember())) {
						editError(event, "errors.interaction.no_access", "'Support' for this ticket or Admin+ access");
						return;
					}
				}
			}
		}
		
		Guild guild = event.getGuild();
		UserSnowflake user = User.fromId(bot.getDBUtil().tickets.getUserId(channelId));
		Instant closeTime = Instant.now().plus(CLOSE_AFTER_DELAY, ChronoUnit.HOURS);

		MessageEmbed embed = new EmbedBuilder()
			.setColor(bot.getDBUtil().getGuildSettings(guild).getColor())
			.setDescription(lu.getGuildText(event, "bot.ticketing.listener.close_request")
				.replace("{user}", user.getAsMention())
				.replace("{time}", TimeUtil.formatTime(closeTime, false)))
			.build();

		Button close = Button.primary("ticket:close", lu.getGuildText(event, "ticket.close"));
		Button cancel = Button.secondary("ticket:cancel", lu.getGuildText(event, "ticket.cancel"));
		
		event.getHook().editOriginal("||%s||".formatted(user.getAsMention())).setEmbeds(embed).setComponents(ActionRow.of(close, cancel)).queue();
		bot.getDBUtil().tickets.setRequestStatus(
			channelId, closeTime.getEpochSecond(),
			event.optString("reason", lu.getGuildText(event, "bot.ticketing.listener.closed_support"))
		);
	}

	private boolean denyCloseSupport(List<Long> supportRoleIds, Member member) {
		if (supportRoleIds.isEmpty()) return false; // No data to check against
		final List<Role> roles = member.getRoles(); // Check if user has any support role
		if (!roles.isEmpty() && roles.stream().anyMatch(r -> supportRoleIds.contains(r.getIdLong()))) return false;
		return !bot.getCheckUtil().hasAccess(member, CmdAccessLevel.ADMIN); // if user has Admin access
	}

}
