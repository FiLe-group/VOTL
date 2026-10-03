package dev.fileeditor.votl.scheduler.tasks;

import ch.qos.logback.classic.Logger;
import dev.fileeditor.votl.App;
import dev.fileeditor.votl.contracts.scheduler.Task;
import net.dv8tion.jda.api.entities.Role;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.exceptions.HierarchyException;
import net.dv8tion.jda.api.exceptions.InsufficientPermissionException;
import org.slf4j.LoggerFactory;

import java.sql.SQLException;

import static dev.fileeditor.votl.utils.CastUtil.castLong;

public class RemoveTempRoles implements Task {

	private static final Logger LOG = (Logger) LoggerFactory.getLogger(RemoveTempRoles.class);

	@Override
	public void handle(App bot) {
		bot.getDBUtil().tempRoles.expiredRoles().forEach(data -> {
			long roleId = castLong(data.get("roleId"));
			Role role = bot.JDA.getRoleById(roleId);
			if (role == null) {
				bot.getDBUtil().tempRoles.removeRole(roleId);
				return;
			}

			final long userId = castLong(data.get("userId"));
			if (bot.getDBUtil().tempRoles.shouldDelete(roleId, userId) && isSafeToDelete(bot, role, userId)) {
				try {
					role.delete()
						.reason("Role expired")
						.queue(null, t -> failed(role, null, t));
				} catch (InsufficientPermissionException | HierarchyException ex) {
					failed(role, null, ex);
				}
				bot.getDBUtil().tempRoles.removeRole(roleId);
			} else {
				try {
					role.getGuild().removeRoleFromMember(User.fromId(userId), role)
						.reason("Role expired")
						.queue(null, t -> failed(role, userId, t));
					bot.getDBUtil().tempRoles.remove(roleId, userId);
				} catch (InsufficientPermissionException | HierarchyException ex) {
					failed(role, userId, ex);
				} catch (SQLException ignored) {}
			}
			// Log
			bot.getGuildLogger().role.onTempRoleAutoRemoved(role.getGuild(), userId, role);
		});
	}

	// Deleting the role takes it from everyone - only allowed when nobody but this member holds it or is due to.
	// Otherwise, the role is just removed from this member, as for any other temporary role.
	private boolean isSafeToDelete(App bot, Role role, long userId) {
		boolean heldByOthers = role.getGuild().getMembersWithRoles(role).stream().anyMatch(m -> m.getIdLong() != userId);
		if (heldByOthers || bot.getDBUtil().tempRoles.countOthers(role.getIdLong(), userId) > 0) {
			LOG.warn("Temporary role '{}' marked for deletion is held by other members, removing it from '{}' only", role.getIdLong(), userId);
			return false;
		}
		return true;
	}

	private <T extends Throwable> void failed(Role role, Long userId, T exception) {
		if (userId == null) {
			LOG.warn("Failed to delete temporary role '{}'", role.getIdLong(), exception);
		} else {
			LOG.warn("Failed to remove temporary role '{}' from '{}'", role.getIdLong(), userId, exception);
		}
	}

}
