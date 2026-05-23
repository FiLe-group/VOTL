package dev.fileeditor.votl.utils.message;

import dev.fileeditor.votl.objects.MediaType;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.net.URI;
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

	private static final Pattern URL_PATTERN = Pattern.compile("^(https?://\\S+)$");

	@NotNull
	public static Optional<MediaType> detectMediaType(@Nullable String message) {
		if (message == null) return Optional.empty();

		String trimmed = message.strip();
		if (trimmed.isBlank()) return Optional.empty();

		if (trimmed.contains("\n") || trimmed.contains("\t")) {
			return Optional.empty();
		}

		// <url> wrapping suppresses Discord embeds entirely
		if (trimmed.startsWith("<") && trimmed.endsWith(">")) return Optional.empty();

		if (!URL_PATTERN.matcher(trimmed).matches()) return Optional.empty();

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

}
