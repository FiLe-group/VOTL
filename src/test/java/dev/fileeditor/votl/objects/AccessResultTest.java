package dev.fileeditor.votl.objects;

import dev.fileeditor.votl.BaseTest;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.EnumSet;

import static org.junit.jupiter.api.Assertions.*;

public class AccessResultTest extends BaseTest {

	// ---- AccessPermission ----

	@Test
	void allPermissionsHaveUniqueBits() {
		// each bit is 1L << ordinal(); ordinals are unique by definition,
		// but we also guard against overflow (long has 64 bits)
		assertTrue(AccessPermission.values().length <= 63,
			"AccessPermission has more than 63 values — toBit() would overflow a long");
		for (AccessPermission p : AccessPermission.values()) {
			assertTrue(p.toBit() > 0, "toBit() must be positive for " + p);
		}
	}

	@Test
	void tierPermissionsPresent() {
		// Regression: ADMIN, OWNER, DEV must exist in the enum (used by HasAccess middleware)
		assertDoesNotThrow(() -> AccessPermission.valueOf("ADMIN"));
		assertDoesNotThrow(() -> AccessPermission.valueOf("OWNER"));
		assertDoesNotThrow(() -> AccessPermission.valueOf("DEV"));
	}

	// ---- AccessResult constants ----

	@Test
	void emptyHasNoPermissions() {
		for (AccessPermission p : AccessPermission.values()) {
			assertFalse(AccessResult.EMPTY.has(p), "EMPTY should not have " + p);
		}
	}

	@Test
	void fullHasAllPermissions() {
		for (AccessPermission p : AccessPermission.values()) {
			assertTrue(AccessResult.FULL.has(p), "FULL should have " + p);
		}
	}

	@Test
	void adminDefaultExcludesSyncPermissions() {
		assertFalse(AccessResult.ADMIN_DEFAULT.has(AccessPermission.DEV));
		assertFalse(AccessResult.ADMIN_DEFAULT.has(AccessPermission.OWNER));
		assertFalse(AccessResult.ADMIN_DEFAULT.has(AccessPermission.SYNC_GROUP_MANAGER));
		assertFalse(AccessResult.ADMIN_DEFAULT.has(AccessPermission.BLACKLIST_MANAGE));
	}

	@Test
	void adminDefaultIncludesCommandAndSpecialPermissions() {
		assertAll(
			() -> assertTrue(AccessResult.ADMIN_DEFAULT.has(AccessPermission.CMD_BAN)),
			() -> assertTrue(AccessResult.ADMIN_DEFAULT.has(AccessPermission.CMD_KICK)),
			() -> assertTrue(AccessResult.ADMIN_DEFAULT.has(AccessPermission.CMD_MUTE)),
			() -> assertTrue(AccessResult.ADMIN_DEFAULT.has(AccessPermission.CMD_UNMUTE)),
			() -> assertTrue(AccessResult.ADMIN_DEFAULT.has(AccessPermission.TICKET_SUPPORT)),
			() -> assertTrue(AccessResult.ADMIN_DEFAULT.has(AccessPermission.LIMIT_OVERRIDE)),
			() -> assertTrue(AccessResult.ADMIN_DEFAULT.has(AccessPermission.AUTO_KICK_EXEMPT))
		);
	}

	@Test
	void adminDefaultContainsTierBitsAsArtifact() {
		assertTrue(AccessResult.ADMIN_DEFAULT.has(AccessPermission.ADMIN));
		assertFalse(AccessResult.ADMIN_DEFAULT.has(AccessPermission.OWNER));
		assertFalse(AccessResult.ADMIN_DEFAULT.has(AccessPermission.DEV));
	}

	// ---- AccessResult.merge ----

	@Test
	void mergeUnionPermissions() {
		AccessResult a = new AccessResult(EnumSet.of(AccessPermission.CMD_BAN), AccessLimits.UNLIMITED);
		AccessResult b = new AccessResult(EnumSet.of(AccessPermission.CMD_KICK), AccessLimits.UNLIMITED);
		AccessResult merged = a.merge(b);
		assertTrue(merged.has(AccessPermission.CMD_BAN));
		assertTrue(merged.has(AccessPermission.CMD_KICK));
		assertFalse(merged.has(AccessPermission.CMD_MUTE));
	}

	@Test
	void mergeEmptyWithFull() {
		AccessResult merged = AccessResult.EMPTY.merge(AccessResult.FULL);
		for (AccessPermission p : AccessPermission.values()) {
			assertTrue(merged.has(p), "EMPTY merged with FULL should have " + p);
		}
	}

	@Test
	void mergeIsCommutative() {
		AccessResult a = new AccessResult(EnumSet.of(AccessPermission.CMD_BAN, AccessPermission.CMD_KICK), AccessLimits.UNLIMITED);
		AccessResult b = new AccessResult(EnumSet.of(AccessPermission.CMD_MUTE), AccessLimits.UNLIMITED);
		assertEquals(a.merge(b).permissions(), b.merge(a).permissions());
	}

	@Test
	void mergeIdempotent() {
		AccessResult a = new AccessResult(EnumSet.of(AccessPermission.CMD_STRIKE), AccessLimits.UNLIMITED);
		assertEquals(a.merge(a).permissions(), a.permissions());
	}

	// ---- AccessLimits.merge ----

	@Test
	void unlimitedMergeWithUnlimited() {
		AccessLimits merged = AccessLimits.UNLIMITED.merge(AccessLimits.UNLIMITED);
		assertNull(merged.maxBanDuration());
		assertNull(merged.maxMuteDuration());
	}

	@Test
	void groupWithNoLimitDoesNotCancelOtherGroupsLimit() {
		// Regression: a group with no limits (null = "no constraint") must not cancel a
		// finite limit from another group the member belongs to.
		// exempt group: no limits configured
		AccessLimits exempt = new AccessLimits(null, null);
		// moderator group: 7-day ban, 24-hour mute limit
		AccessLimits moderator = new AccessLimits(Duration.ofDays(7), Duration.ofHours(24));
		AccessLimits merged = exempt.merge(moderator);
		assertEquals(Duration.ofDays(7), merged.maxBanDuration());
		assertEquals(Duration.ofHours(24), merged.maxMuteDuration());
	}

	@Test
	void nullLimitTreatedAsNoConstraint() {
		// null means the group contributes no opinion; the finite side always wins
		AccessLimits limited = new AccessLimits(Duration.ofDays(7), Duration.ofHours(24));
		assertEquals(Duration.ofDays(7),   AccessLimits.UNLIMITED.merge(limited).maxBanDuration());
		assertEquals(Duration.ofDays(7),   limited.merge(AccessLimits.UNLIMITED).maxBanDuration());
		assertEquals(Duration.ofHours(24), AccessLimits.UNLIMITED.merge(limited).maxMuteDuration());
		assertEquals(Duration.ofHours(24), limited.merge(AccessLimits.UNLIMITED).maxMuteDuration());
	}

	@Test
	void mergeLimitsTakesHigherValue() {
		AccessLimits short_ = new AccessLimits(Duration.ofDays(1), Duration.ofHours(1));
		AccessLimits long_  = new AccessLimits(Duration.ofDays(30), Duration.ofHours(24));
		AccessLimits merged = short_.merge(long_);
		assertEquals(Duration.ofDays(30), merged.maxBanDuration());
		assertEquals(Duration.ofHours(24), merged.maxMuteDuration());
	}

	@Test
	void mergeLimitsIndependentPerField() {
		// ban null on 'a' (no constraint), mute null on 'b' (no constraint) — each field takes the finite value
		AccessLimits a = new AccessLimits(null, Duration.ofHours(6));
		AccessLimits b = new AccessLimits(Duration.ofDays(14), null);
		AccessLimits merged = a.merge(b);
		assertEquals(Duration.ofDays(14), merged.maxBanDuration());
		assertEquals(Duration.ofHours(6), merged.maxMuteDuration());
	}

	@Test
	void mergeResultLimitsPassThrough() {
		// r1: ban=7d, mute=null (no mute constraint)   r2: ban=null (no ban constraint), mute=12h
		// merged: ban=7d (finite wins over null), mute=12h (finite wins over null)
		AccessResult r1 = new AccessResult(EnumSet.of(AccessPermission.CMD_BAN),
			new AccessLimits(Duration.ofDays(7), null));
		AccessResult r2 = new AccessResult(EnumSet.of(AccessPermission.CMD_KICK),
			new AccessLimits(null, Duration.ofHours(12)));
		AccessResult merged = r1.merge(r2);
		assertEquals(Duration.ofDays(7),   merged.limits().maxBanDuration());
		assertEquals(Duration.ofHours(12), merged.limits().maxMuteDuration());
	}
}
