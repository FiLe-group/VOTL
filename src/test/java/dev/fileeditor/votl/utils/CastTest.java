package dev.fileeditor.votl.utils;

import dev.fileeditor.votl.BaseTest;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

public class CastTest extends BaseTest {

	@Test
	public void testCastLong() {
		assertEquals(1L, CastUtil.castLong(1L));
		assertEquals(1L, CastUtil.castLong(1));
		assertEquals(1L, CastUtil.castLong("1"));
		assertEquals(1L, CastUtil.castLong('1'));

		assertEquals(-1L, CastUtil.castLong(-1));
		assertEquals(-1L, CastUtil.castLong("-1"));

		assertEquals(0L, CastUtil.castLong(0));
		assertNull(CastUtil.castLong(null));
	}

	@Test
	public void testGetOrDefault() {
		assertEquals("hello", CastUtil.getOrDefault("hello", "default"));
		assertEquals("default", CastUtil.getOrDefault(null, "default"));
		assertNull(CastUtil.getOrDefault(null, null));

		assertEquals(1L, CastUtil.getOrDefault(1L, 0L));
		assertEquals(1L, CastUtil.getOrDefault(1, 0L));
		assertEquals(1L, CastUtil.getOrDefault("1", 0L));

		assertEquals(42L, CastUtil.getOrDefault(42, 0L));
		assertEquals(99L, CastUtil.getOrDefault(null, 99L));
	}

	@Test
	public void testRequireNonNull() {
		assertEquals("hello", CastUtil.requireNonNull("hello"));

		NullPointerException ex = assertThrows(NullPointerException.class,
			() -> CastUtil.requireNonNull(null));
		assertEquals("Object is null", ex.getMessage());

		assertEquals(5L, (long) CastUtil.requireNonNull(5L));
		assertEquals(5,  (int) CastUtil.requireNonNull(5));
		assertEquals("abc", CastUtil.requireNonNull("abc"));
	}

	@Test
	public void testResolveOrDefault() {
		assertEquals(5, CastUtil.resolveOrDefault("hello", v->((String) v).length(), 0));
		assertEquals("default", CastUtil.resolveOrDefault(null, Object::toString, "default"));
		assertEquals("HELLO", CastUtil.resolveOrDefault("hello", obj -> obj.toString().toUpperCase(), "default"));

		//noinspection DataFlowIssue
		assertNull(CastUtil.resolveOrDefault("hello", _ -> null, "default"));
		assertNull(CastUtil.resolveOrDefault(null, Object::toString, null));
	}

}
