package dev.fileeditor.votl.utils;

import dev.fileeditor.votl.BaseTest;
import dev.fileeditor.votl.objects.MediaType;
import dev.fileeditor.votl.utils.message.MediaLinkUtil;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

public class MediaTypeTest extends BaseTest {

	@Test
	public void testFilename() {
		var a = MediaType.fromFilename("cat.png");
		assertTrue(a.isPresent());
		assertEquals(MediaType.IMAGE, a.get());

		var b = MediaType.fromFilename("dog.gif");
		assertTrue(b.isPresent());
		assertEquals(MediaType.ANIMATED, b.get());

		var c = MediaType.fromFilename("cow.mp4");
		assertTrue(c.isPresent());
		assertEquals(MediaType.VIDEO, c.get());

		var d = MediaType.fromFilename("fish.mp3");
		assertTrue(d.isPresent());
		assertEquals(MediaType.AUDIO, d.get());

		var f = MediaType.fromFilename("unknown.ogr");
		assertTrue(f.isEmpty());
	}

	@Test
	public void testExtension() {
		assertTrue(MediaType.IMAGE.matches(".jpg"));
		assertFalse(MediaType.ANIMATED.matches(".crab"));
	}

	@Test
	public void testEmbedLink() {
		var a = MediaLinkUtil.detectMediaType("https://youtu.be/dQw4w9WgXcQ");
		assertTrue(a.isPresent());
		assertEquals(MediaType.VIDEO, a.get());

		var b = MediaLinkUtil.detectMediaType("https://klipy.com/gifs/monkey-pissed-3");
		assertTrue(b.isPresent());
		assertEquals(MediaType.ANIMATED, b.get());

		var c = MediaLinkUtil.detectMediaType("https://tenor.com/l7JANLSlo81.gif");
		assertTrue(c.isPresent());
		assertEquals(MediaType.ANIMATED, c.get());

		var d = MediaLinkUtil.detectMediaType("https://upload.wikimedia.org/wikipedia/commons/7/70/Example.png");
		assertTrue(d.isPresent());
		assertEquals(MediaType.IMAGE, d.get());
	}

}
