package dev.fileeditor.votl.contracts.blacklist;

import java.time.OffsetDateTime;

public interface PunishmentLevel {
	OffsetDateTime generateTime();
}
