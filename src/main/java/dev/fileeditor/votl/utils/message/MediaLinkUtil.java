package dev.fileeditor.votl.utils.message;

import dev.fileeditor.votl.objects.MediaType;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Pattern;

public final class MediaLinkUtil {

	// Domains Discord auto-embeds
	private static final Set<String> VIDEO_EMBED_HOSTS = Set.of(
		"youtube.com", "youtu.be", "m.youtube.com",
		"tiktok.com", "vm.tiktok.com",
		"twitch.tv", "clips.twitch.tv",
		"vimeo.com",
		"streamable.com"
	);

	private static final Set<String> GIF_EMBED_HOSTS = Set.of(
		"tenor.com", "giphy.com", "media.giphy.com", "klipy.com"
	);

	private static final Set<String> AUDIO_EMBED_HOSTS = Set.of(
		"soundcloud.com",
		"open.spotify.com", "spotify.com"
	);

	// Groups: optional embed suppressing brackets around the link itself
	private static final Pattern URL_PATTERN = Pattern.compile("(<?)(https?://[^\\s<>]+)(>?)");
	// Sentence punctuation that follows a link rather than being a part of it
	private static final Pattern TRAILING_PUNCTUATION = Pattern.compile("[.,!?;:)\\]]+$");

	/**
	 * Splits message content into the links it contains and the text left around them.
	 *
	 * @param content raw message content.
	 * @return scan of the provided content, never null.
	 */
	@NotNull
	public static ContentScan scanContent(@Nullable String content) {
		if (content == null || content.isBlank()) return ContentScan.EMPTY;

		List<MediaType> mediaLinks = new ArrayList<>();
		int otherLinks = 0;
		StringBuilder text = new StringBuilder();

		var matcher = URL_PATTERN.matcher(content);
		int textStart = 0;
		while (matcher.find()) {
			text.append(content, textStart, matcher.start());
			textStart = matcher.end();

			// <url> wrapping suppresses Discord embeds entirely, so such a link never displays media
			boolean suppressed = "<".equals(matcher.group(1)) && ">".equals(matcher.group(3));
			var mediaType = suppressed ? Optional.<MediaType>empty() : detectMediaType(matcher.group(2));

			if (mediaType.isPresent()) mediaLinks.add(mediaType.get());
			else otherLinks++;
		}
		text.append(content, textStart, content.length());

		return new ContentScan(List.copyOf(mediaLinks), otherLinks, text.toString().strip());
	}

	/**
	 * Resolves what media, if any, Discord will display for a single link.
	 *
	 * @param url link without any embed suppressing brackets.
	 * @return detected media type, or empty if the link displays nothing.
	 */
	@NotNull
	public static Optional<MediaType> detectMediaType(@Nullable String url) {
		if (url == null || url.isBlank()) return Optional.empty();

		String trimmed = TRAILING_PUNCTUATION.matcher(url.strip()).replaceAll("");
		if (trimmed.isBlank()) return Optional.empty();

		URI uri;
		try {
			uri = URI.create(trimmed);
		} catch (IllegalArgumentException e) {
			return Optional.empty();
		}

		// 1 - Direct media file (by extensions)
		var path = uri.getPath();
		if (path != null) {
			var byExt = MediaType.fromFilename(stripQuery(path));
			if (byExt.isPresent()) return byExt;
		}

		// 2 - Known embed link
		var host = uri.getHost();
		if (host == null) return Optional.empty();
		host = host.toLowerCase().replaceFirst("^www\\.", "");

		if (GIF_EMBED_HOSTS.contains(host)) return Optional.of(MediaType.ANIMATED);
		if (VIDEO_EMBED_HOSTS.contains(host)) return Optional.of(MediaType.VIDEO);
		if (AUDIO_EMBED_HOSTS.contains(host)) return Optional.of(MediaType.AUDIO);

		return Optional.empty();
	}

	private static String stripQuery(String path) {
		int q = path.indexOf('?');
		return q == -1 ? path : path.substring(0, q);
	}

	/**
	 * @param mediaLinks         type of each link that displays media, in the order they appear.
	 * @param otherLinks         amount of links that display nothing (suppressed or unsupported).
	 * @param textWithoutLinks   what is left of the content once every link is cut out.
	 */
	public record ContentScan(@NotNull List<MediaType> mediaLinks, int otherLinks, @NotNull String textWithoutLinks) {
		private static final ContentScan EMPTY = new ContentScan(List.of(), 0, "");

		public boolean hasMediaLinks() {
			return !mediaLinks.isEmpty();
		}

		public boolean hasLinks() {
			return !mediaLinks.isEmpty() || otherLinks > 0;
		}

		public boolean hasText() {
			return !textWithoutLinks.isBlank();
		}
	}

}
