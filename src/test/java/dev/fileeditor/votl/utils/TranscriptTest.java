package dev.fileeditor.votl.utils;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Base64;

import dev.fileeditor.votl.BaseTest;
import dev.fileeditor.votl.utils.transcripts.Formatter;
import dev.fileeditor.votl.utils.transcripts.TranscriptSanitizer;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Attribute;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

public class TranscriptTest extends BaseTest {

	@Test
	public void testFormatterEscapesHtml() {
		String result = Formatter.format("<img src=x onerror=alert(1)><script>alert(2)</script>");
		assertFalse(result.contains("<img"));
		assertFalse(result.contains("<script"));
		assertTrue(result.contains("&lt;script&gt;"));

		// inside code blocks as well
		assertFalse(Formatter.format("`<b>x</b>`").contains("<b>"));
		// can't break out of a link attribute
		String link = Formatter.format("[a](https://example.com/\"onmouseover=\"alert(1))");
		assertFalse(link.contains("\"onmouseover"));
	}

	@Test
	public void testFormatterMarkdown() {
		assertEquals("<strong>bold</strong>", Formatter.format("**bold**"));
		assertEquals("<span class=\"quote\">quoted &amp; text</span>", Formatter.format("> quoted & text"));
		String emoji = Formatter.format("<:smile:123456>");
		assertTrue(emoji.startsWith("<img class=\"emoji\" src=\"https://cdn.discordapp.com/emojis/123456."), emoji);
	}

	@Test
	public void testSanitizer() throws Exception {
		Document document = loadTemplate();
		TranscriptSanitizer sanitizer = new TranscriptSanitizer(document);

		Element chatLog = document.getElementById("chatlog");
		assertNotNull(chatLog);
		chatLog.append("""
			<script>alert(1)</script>
			<img src="javascript:alert(2)" onerror="alert(3)">
			<a href="javascript:alert(4)">bad</a>
			<a href="https://example.com">good</a>
			<iframe src="https://example.com"></iframe>
			<div data-scroll-to="1" onclick="alert(5)"></div>
			""");
		sanitizer.sanitize(document);

		// Serialize the same way as the transcript generator
		document.outputSettings().indentAmount(0).prettyPrint(true);
		Document output = Jsoup.parse(document.outerHtml());

		Element csp = output.head().child(0);
		assertEquals("Content-Security-Policy", csp.attr("http-equiv"), "CSP must be the first element in head");
		String policy = csp.attr("content");

		// Only template scripts are left, and all of them are allowed by the policy
		for (Element script : output.select("script")) {
			assertFalse(script.data().contains("alert("));
			if (script.hasAttr("src")) {
				assertTrue(policy.contains(script.attr("src")), script.attr("src"));
				assertTrue(script.hasAttr("integrity"), script.attr("src"));
			} else {
				assertTrue(policy.contains("'sha256-%s'".formatted(sha256(script.data()))), "Inline script hash missing from CSP");
			}
		}

		assertTrue(output.select("iframe").isEmpty());
		for (Element element : output.getAllElements()) {
			for (Attribute attribute : element.attributes()) {
				assertFalse(attribute.getKey().startsWith("on"), attribute.toString());
				assertFalse(attribute.getValue().toLowerCase().startsWith("javascript:"), attribute.toString());
			}
		}
		Element good = output.selectFirst("a[href=https://example.com]");
		assertNotNull(good);
		assertTrue(good.attr("rel").contains("noreferrer"));
		assertNotNull(output.selectFirst("meta[name=referrer][content=no-referrer]"));
		assertNotNull(output.selectFirst("div[data-scroll-to=1]"));
	}

	private Document loadTemplate() throws IOException {
		try (InputStream is = getClass().getClassLoader().getResourceAsStream("template.html")) {
			assertNotNull(is);
			return Jsoup.parse(is, "UTF-8", "template.html");
		}
	}

	private static String sha256(String data) throws Exception {
		byte[] hash = MessageDigest.getInstance("SHA-256").digest(data.getBytes(StandardCharsets.UTF_8));
		return Base64.getEncoder().encodeToString(hash);
	}

}
