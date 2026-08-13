package dev.fileeditor.votl.utils;

import dev.fileeditor.votl.BaseTest;
import dev.fileeditor.votl.utils.database.managers.RepeatMessageManager;
import dev.fileeditor.votl.utils.database.managers.RepeatMessageManager.RepeatMessage;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.entities.MessageEmbed;
import org.junit.jupiter.api.Test;

import java.awt.Color;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

public class RepeatMessageTest extends BaseTest {

	private RepeatMessage build(String content, String embeds) {
		Map<String, Object> data = new HashMap<>();
		data.put("repeatId", 7);
		data.put("guildId", 123456789012345678L);
		data.put("channelId", 234567890123456789L);
		data.put("sourceId", 345678901234567890L);
		data.put("content", content);
		data.put("embeds", embeds);
		data.put("interval", 21600);
		data.put("nextRun", 1893456000L);
		data.put("skipIfLast", 1);
		data.put("lastMessageId", null);
		return new RepeatMessage(data);
	}

	@Test
	public void testRowMapping() {
		RepeatMessage data = build("hello", null);

		assertEquals(7, data.getRepeatId());
		assertEquals(123456789012345678L, data.getGuildId());
		assertEquals(234567890123456789L, data.getChannelId());
		assertEquals(21600L, data.getInterval());
		assertEquals(1893456000L, data.getNextRun());
		assertTrue(data.isSkipIfLast());
		assertNull(data.getLastMessageId());
		assertTrue(data.getEmbeds().isEmpty());
	}

	@Test
	public void testEmbedsRoundTrip() {
		MessageEmbed source = new EmbedBuilder()
			.setTitle("Server rules")
			.setDescription("Be nice to each other.")
			.setColor(Color.CYAN)
			.addField("Rule 1", "No spam", false)
			.setFooter("updated daily")
			.build();

		String encoded = RepeatMessageManager.encodeEmbeds(List.of(source));
		assertNotNull(encoded);

		List<MessageEmbed> decoded = build(null, encoded).getEmbeds();
		assertEquals(1, decoded.size());

		MessageEmbed result = decoded.getFirst();
		assertEquals(source.getTitle(), result.getTitle());
		assertEquals(source.getDescription(), result.getDescription());
		assertEquals(source.getColor(), result.getColor());
		assertEquals(1, result.getFields().size());
		assertEquals("Rule 1", result.getFields().getFirst().getName());
		assertNotNull(result.getFooter());
		assertEquals("updated daily", result.getFooter().getText());
	}

	@Test
	public void testEncodeEmbedsEmpty() {
		assertNull(RepeatMessageManager.encodeEmbeds(List.of()));
	}

	@Test
	public void testToMessageDataKeepsBothParts() {
		String encoded = RepeatMessageManager.encodeEmbeds(
			List.of(new EmbedBuilder().setDescription("embedded").build())
		);
		var message = build("plain text", encoded).toMessageData();

		assertEquals("plain text", message.getContent());
		assertEquals(1, message.getEmbeds().size());
		// A snapshot re-posted on a timer must never turn into a recurring mass ping
		assertFalse(message.getAllowedMentions().contains(net.dv8tion.jda.api.entities.Message.MentionType.EVERYONE));
	}

	@Test
	public void testToMessageDataEmbedOnly() {
		String encoded = RepeatMessageManager.encodeEmbeds(
			List.of(new EmbedBuilder().setDescription("embedded").build())
		);
		var message = build(null, encoded).toMessageData();

		assertTrue(message.getContent().isEmpty());
		assertEquals(1, message.getEmbeds().size());
	}

}
