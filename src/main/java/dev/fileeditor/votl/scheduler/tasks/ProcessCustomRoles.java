package dev.fileeditor.votl.scheduler.tasks;

import ch.qos.logback.classic.Logger;
import dev.fileeditor.votl.App;
import dev.fileeditor.votl.contracts.scheduler.Task;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Role;
import net.dv8tion.jda.api.exceptions.ErrorHandler;
import net.dv8tion.jda.api.requests.ErrorResponse;
import org.slf4j.LoggerFactory;

import java.sql.SQLException;
import java.time.Instant;
import java.time.temporal.ChronoUnit;

import static dev.fileeditor.votl.utils.CastUtil.castLong;

public class ProcessCustomRoles implements Task {

	private static final Logger LOG = (Logger) LoggerFactory.getLogger(ProcessCustomRoles.class);

	@Override
	public void handle(App bot) {
		// Pass 1 — delete expired custom roles
		bot.getDBUtil().customRoles.getExpired().forEach(data -> {
			long roleId = castLong(data.get("roleId"));
			long ownerId = castLong(data.get("ownerId"));
			long guildId = castLong(data.get("guildId"));

			Guild guild = bot.JDA.getGuildById(guildId);
			if (guild != null) {
				Role role = guild.getRoleById(roleId);
				if (role != null) {
					role.delete().reason("Custom role expired").queue(null,
						new ErrorHandler().ignore(ErrorResponse.UNKNOWN_ROLE));
				}
				// DM owner
				guild.retrieveMemberById(ownerId).queue(member ->
					member.getUser().openPrivateChannel().queue(dm ->
						dm.sendMessage(
							bot.getLocaleUtil().getLocalized(net.dv8tion.jda.api.interactions.DiscordLocale.ENGLISH_UK, "bot.roles.custom_role.dm.expired")
								.formatted(role != null ? role.getName() : "?", guild.getName())
						).queue(null, new ErrorHandler().ignore(ErrorResponse.CANNOT_SEND_TO_USER))
					)
				, new ErrorHandler().ignore(ErrorResponse.UNKNOWN_MEMBER));
			}

			try {
				bot.getDBUtil().customRoles.remove(roleId);
			} catch (SQLException ex) {
				LOG.warn("Failed to remove expired custom role {} from DB", roleId, ex);
			}
		});

		// Pass 2 — renew nitro-based roles for still-boosting members
		bot.getDBUtil().customRoles.getNitroForRenewal().forEach(data -> {
			long roleId = castLong(data.get("roleId"));
			long ownerId = castLong(data.get("ownerId"));
			long guildId = castLong(data.get("guildId"));

			Guild guild = bot.JDA.getGuildById(guildId);
			if (guild == null) return;

			var settings = bot.getDBUtil().customRoleSettings.getSettings(guildId);

			guild.retrieveMemberById(ownerId).queue(member -> {
				if (member.getTimeBoosted() != null) {
					// Still boosting — renew
					long newExpires = Instant.now().plus(settings.getNitroExpireDays(), ChronoUnit.DAYS).getEpochSecond();
					long newRenewAt = Instant.now().plus(settings.getNitroRenewDays(), ChronoUnit.DAYS).getEpochSecond();
					try {
						bot.getDBUtil().customRoles.setExpires(roleId, newExpires);
						bot.getDBUtil().customRoles.setRenewAt(roleId, newRenewAt);
						bot.getDBUtil().customRoleAccess.updateExpiry(ownerId, guildId, newExpires);
					} catch (SQLException ex) {
						LOG.warn("Failed to renew nitro custom role {} for member {}", roleId, ownerId, ex);
					}
				}
				// If not boosting, leave expires as-is; Pass 1 will catch it when it expires.
			}, new ErrorHandler().ignore(ErrorResponse.UNKNOWN_MEMBER));
		});
	}

}
