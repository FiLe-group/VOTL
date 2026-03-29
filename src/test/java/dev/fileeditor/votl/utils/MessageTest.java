package dev.fileeditor.votl.utils;

import dev.fileeditor.votl.BaseTest;
import dev.fileeditor.votl.utils.message.MessageUtil;
import org.junit.jupiter.api.Test;

import java.awt.Color;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

public class MessageTest extends BaseTest {

	@Test
	void testCapitalize() {
		assertEquals("Hello, world!", MessageUtil.capitalize("hello, WORLD!"));
	}

	@Test
	void testDecodeRoleList() {
		// 2 roles and other role (total size: 3)
		List<Long> roles = MessageUtil.getRoleIdsFromString("<@&12345>+<@&54321>BAD_TEXT22222");

		assertEquals(3, roles.size());
		assertTrue(roles.containsAll(List.of(0L, 12345L, 54321L)));
	}

	@Test
	void testColorParse() {
		assertNotNull(MessageUtil.getColor("random"));

		Color c = MessageUtil.getColor("#FF8800");
		assertNotNull(c);
		assertEquals(255, c.getRed());
		assertEquals(136, c.getGreen());
		assertEquals(0,   c.getBlue());
		assertEquals(255, c.getAlpha());

		c = MessageUtil.getColor("FF8800");
		assertNotNull(c);
		assertEquals(255, c.getRed());
		assertEquals(136, c.getGreen());
		assertEquals(0,   c.getBlue());
		assertEquals(255, c.getAlpha());

		c = MessageUtil.getColor("255,136,0");
		assertNotNull(c);
		assertEquals(255, c.getRed());
		assertEquals(136, c.getGreen());
		assertEquals(0,   c.getBlue());
		assertEquals(255, c.getAlpha());

		c = MessageUtil.getColor("some_text");
		assertNull(c);

		c = MessageUtil.getColor("123,456,789");
		assertNull(c);
	}

	@Test
	void testLimitString() {
		assertEquals("12345...", MessageUtil.limitString("1234567890", 8));
	}
}
