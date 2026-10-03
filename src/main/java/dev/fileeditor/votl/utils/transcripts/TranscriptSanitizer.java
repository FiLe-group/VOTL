package dev.fileeditor.votl.utils.transcripts;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

import org.jetbrains.annotations.NotNull;
import org.jsoup.nodes.Attribute;
import org.jsoup.nodes.Comment;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.nodes.Node;

/**
 * Hardens a generated transcript. Transcripts can be served from a web origin (Zipline),
 * where injected script would run alongside that site's session - not only opened as a local file.
 * <p>
 * Defense in depth on top of escaping in {@link Formatter}:
 * <ul>
 *     <li>Content-Security-Policy, allowing only the template's own scripts (by hash) and its pinned CDN files</li>
 *     <li>removes active elements and event handler attributes that did not come from the template</li>
 *     <li>links and media only over http(s), links open without referrer/opener</li>
 *     <li>asks not to be indexed and not to send the transcript URL as referrer</li>
 * </ul>
 */
public class TranscriptSanitizer {

	private static final String ACTIVE_ELEMENTS = "script, style, link, meta, base, iframe, frame, frameset, object, embed, applet, form, noscript, template";
	private static final String FONT_SOURCES = "https://cdn.jsdelivr.net";

	private final Set<Element> trusted = Collections.newSetFromMap(new IdentityHashMap<>());
	private final String policy;

	/**
	 * @param template Document, before any message content is added to it.
	 */
	public TranscriptSanitizer(@NotNull Document template) {
		trusted.addAll(template.select(ACTIVE_ELEMENTS));

		Set<String> scriptSources = new LinkedHashSet<>();
		Set<String> styleSources = new LinkedHashSet<>();
		for (Element script : template.select("script")) {
			if (script.hasAttr("src")) scriptSources.add(script.attr("src"));
			else scriptSources.add("'sha256-%s'".formatted(sha256(script.data())));
		}
		for (Element link : template.select("link[rel=stylesheet][href]")) {
			styleSources.add(link.attr("href"));
		}

		this.policy = String.join("; ", List.of(
			"default-src 'none'",
			"script-src " + String.join(" ", scriptSources),
			"style-src 'unsafe-inline' " + String.join(" ", styleSources),
			"font-src " + FONT_SOURCES,
			"img-src https: data:",
			"media-src https:",
			"base-uri 'none'",
			"form-action 'none'"
		));
	}

	public String getPolicy() {
		return policy;
	}

	public void sanitize(@NotNull Document document) {
		// Comments are visible in page source
		List<Node> comments = new ArrayList<>();
		document.forEachNode(node -> {
			if (node instanceof Comment) comments.add(node);
		});
		comments.forEach(Node::remove);

		// Active elements not from the template
		for (Element element : document.select(ACTIVE_ELEMENTS)) {
			if (!trusted.contains(element)) element.remove();
		}

		for (Element element : document.getAllElements()) {
			List<String> remove = new ArrayList<>();
			for (Attribute attribute : element.attributes()) {
				String key = attribute.getKey().toLowerCase(Locale.ROOT);
				if (key.startsWith("on") || key.equals("srcdoc") || key.equals("formaction")) {
					remove.add(attribute.getKey());
				}
			}
			remove.forEach(element::removeAttr);
		}

		for (Element link : document.select("a[href]")) {
			String href = link.attr("href").trim();
			if (href.startsWith("www.")) href = "https://" + href;
			if (isWebUrl(href)) {
				link.attr("href", href)
					.attr("rel", "noopener noreferrer nofollow ugc")
					.attr("target", "_blank");
			} else if (!href.startsWith("#")) {
				link.removeAttr("href");
			}
		}

		for (Element media : document.select("[src]")) {
			if (trusted.contains(media)) continue;
			String src = media.attr("src").trim();
			if (!isWebUrl(src) && !src.startsWith("data:image/")) media.removeAttr("src");
		}

		for (Element use : document.select("[xlink:href]")) {
			if (!use.attr("xlink:href").startsWith("#")) use.removeAttr("xlink:href");
		}

		// Must come before any script
		Element head = document.head();
		head.prependElement("meta").attr("name", "robots").attr("content", "noindex, nofollow, noarchive");
		head.prependElement("meta").attr("name", "referrer").attr("content", "no-referrer");
		head.prependElement("meta").attr("http-equiv", "Content-Security-Policy").attr("content", policy);
	}

	private static boolean isWebUrl(String url) {
		String lower = url.toLowerCase(Locale.ROOT);
		return lower.startsWith("https://") || lower.startsWith("http://");
	}

	private static String sha256(String data) {
		try {
			byte[] hash = MessageDigest.getInstance("SHA-256").digest(data.getBytes(StandardCharsets.UTF_8));
			return Base64.getEncoder().encodeToString(hash);
		} catch (NoSuchAlgorithmException ex) {
			throw new IllegalStateException(ex);
		}
	}

}
