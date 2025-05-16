package dev.fileeditor.votl.contracts.blacklist;

import java.time.Instant;

public interface PunishmentLevel {
	Instant generateTime();
}
