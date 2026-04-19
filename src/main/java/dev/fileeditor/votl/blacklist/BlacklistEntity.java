package dev.fileeditor.votl.blacklist;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.time.OffsetDateTime;

public class BlacklistEntity {

	@NotNull private final Scope scope;
	private final long id;
	@Nullable private final OffsetDateTime expiresIn;
	@Nullable private final String reason;
	private final boolean dnt; // Do Not Track

	BlacklistEntity(@NotNull Scope scope, long id, @Nullable String reason, @Nullable OffsetDateTime expiresIn, boolean dnt) {
		this.scope = scope;
		this.id = id;
		this.expiresIn = expiresIn;
		this.reason = reason;
		this.dnt = dnt;
	}

	public BlacklistEntity(Scope scope, long id, @Nullable String reason) {
		this(scope, id, reason, null, false);
	}

	@NotNull
	public Scope getScope() {
		return scope;
	}

	public long getId() {
		return id;
	}

	public boolean isBlacklisted() {
		return expiresIn == null || OffsetDateTime.now().isBefore(expiresIn);
	}

	@Nullable
	public String getReason() {
		return reason;
	}

	public boolean isDnt() {
		return dnt;
	}

}
