package dev.fileeditor.votl.scheduler.tasks;

import ch.qos.logback.classic.Logger;
import dev.fileeditor.votl.App;
import dev.fileeditor.votl.contracts.scheduler.Task;
import dev.fileeditor.votl.utils.database.managers.LevelManager;
import dev.fileeditor.votl.utils.level.PlayerObject;
import org.slf4j.LoggerFactory;

import java.sql.SQLException;
import java.util.Iterator;

public class DrainDbQueue implements Task {

	private static final Logger LOG = (Logger) LoggerFactory.getLogger(DrainDbQueue.class);

	@Override
	public void handle(App bot) {
		if (bot.getLevelUtil().getUpdateQueue().isEmpty()) {
			return;
		}

		Iterator<PlayerObject> it = bot.getLevelUtil().getUpdateQueue().iterator();
		int updated = 0;
		while (it.hasNext()) {
			PlayerObject player = it.next();
			LevelManager.PlayerData playerData = bot.getDBUtil().levels.getPlayer(player);
			if (playerData == null) continue;

			try {
				bot.getDBUtil().levels.updatePlayer(player, playerData);
			} catch (SQLException e) {
				LOG.error("Failed to update player: {}", player);
				continue;
			}

			it.remove();
			updated++;
		}
		if (updated>0) {
			LOG.debug("Updated data for {} players", updated);
		}
	}
}
