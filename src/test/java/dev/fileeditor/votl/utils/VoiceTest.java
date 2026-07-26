package dev.fileeditor.votl.utils;

import dev.fileeditor.votl.BaseTest;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

public class VoiceTest extends BaseTest {

	@Test
	void testFormatChannelNameKeepsValidNames() {
		assertEquals("Alice's channel", VoiceUtil.formatChannelName("Alice's channel"));
		assertEquals("ab", VoiceUtil.formatChannelName("ab"));
	}

	@Test
	void testFormatChannelNameTruncates() {
		String formatted = VoiceUtil.formatChannelName("x".repeat(150));

		assertEquals(100, formatted.length());
	}

	@Test
	void testFormatChannelNamePadsTooShort() {
		// Discord rejects names shorter than 2 characters, which a name template can produce
		// once its variables resolve to nothing.
		assertEquals(2, VoiceUtil.formatChannelName("").length());
		assertEquals(2, VoiceUtil.formatChannelName("a").length());
		assertTrue(VoiceUtil.formatChannelName("a").startsWith("a"));
	}

}
