package dev.fileeditor.votl.utils;

import dev.fileeditor.votl.BaseTest;
import org.junit.jupiter.api.Test;

import java.awt.*;

import static org.junit.jupiter.api.Assertions.*;

public class ColorTest extends BaseTest {

	@Test
	void testDecodeHex() {
		Color c = ColorUtil.decodeHex("#FF8800", 255);
		assertEquals(255, c.getRed());
		assertEquals(136, c.getGreen());
		assertEquals(0,   c.getBlue());
		assertEquals(255, c.getAlpha());
	}

	@Test
	void testDecodeHex_withAlpha() {
		Color c = ColorUtil.decodeHex("#FF0000", 0);
		assertEquals(0,   c.getAlpha());
	}

	@Test
	void testDecodeHex_valueClamp() {
		Color c = ColorUtil.decodeHex("#FF0000", 300);
		assertEquals(255, c.getAlpha());

		c = ColorUtil.decodeHex("#FF0000", -1);
		assertEquals(255, c.getAlpha());
	}

	@Test
	void testDecodeInt() {
		Color c = ColorUtil.decodeHex(0xFF8800, 1.0f);
		assertEquals(255, c.getRed());
		assertEquals(136, c.getGreen());
		assertEquals(0,   c.getBlue());
		assertEquals(255, c.getAlpha());
	}

	@Test
	void testDecodeInt_withAlpha() {
		Color c = ColorUtil.decodeHex(0xFF0000, 0.0f);
		assertEquals(0,   c.getAlpha());
	}

	@Test
	void testDecodeInt_valueClamp() {
		Color c = ColorUtil.decodeHex(0xFF0000, 1.5f);
		assertEquals(255, c.getAlpha());

		c = ColorUtil.decodeHex(0xFF0000, -0.5f);
		assertEquals(255, c.getAlpha());
	}

	@Test
	void testDecodeString() {
		Color c = ColorUtil.decodeHex("#FF8800");
		assertEquals(255, c.getRed());
		assertEquals(136, c.getGreen());
		assertEquals(0,   c.getBlue());
	}

}
