package dev.fileeditor.votl.utils;

import dev.fileeditor.votl.BaseTest;
import dev.fileeditor.votl.objects.MediaType;
import dev.fileeditor.votl.utils.message.MediaLinkUtil;
import org.junit.jupiter.api.Test;

import java.util.List;

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

	@Test
	public void testScanMediaWithComment() {
		var scan = MediaLinkUtil.scanContent("look at this https://i.imgur.com/cat.png, so cute");
		assertEquals(List.of(MediaType.IMAGE), scan.mediaLinks());
		assertEquals(0, scan.otherLinks());
		assertTrue(scan.hasText());
	}

	@Test
	public void testScanMediaOnly() {
		var scan = MediaLinkUtil.scanContent(" https://youtu.be/dQw4w9WgXcQ ");
		assertEquals(List.of(MediaType.VIDEO), scan.mediaLinks());
		assertFalse(scan.hasText());
	}

	@Test
	public void testScanSuppressedLink() {
		// Wrapped links display no media at all
		var scan = MediaLinkUtil.scanContent("<https://i.imgur.com/cat.png>");
		assertFalse(scan.hasMediaLinks());
		assertEquals(1, scan.otherLinks());
		assertFalse(scan.hasText());
	}

	@Test
	public void testScanText() {
		var scan = MediaLinkUtil.scanContent("no links here");
		assertFalse(scan.hasLinks());
		assertTrue(scan.hasText());

		var withLink = MediaLinkUtil.scanContent("read https://example.com/page");
		assertTrue(withLink.hasLinks());
		assertFalse(withLink.hasMediaLinks());
		assertEquals(1, withLink.otherLinks());
	}

}
