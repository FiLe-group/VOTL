package dev.fileeditor.votl.objects;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;

@SuppressWarnings("UnnecessaryUnicodeEscape")
public enum MediaType {

	// Supported by Discord and displayed correctly in the client
	IMAGE(
		1,
		"\uD83D\uDDBC\uFE0F",
		Set.of("png", "jpg", "jpeg", "webp", "avif")
	),
	ANIMATED(
		2,
		"\uD83D\uDC7E",
		Set.of("gif")
	),
	VIDEO(
		3,
		"\uD83C\uDF9E\uFE0F",
		Set.of("mp4", "mov", "webm")
	),
	AUDIO(
		4,
		"\uD83C\uDFA7",
		Set.of("mp3", "ogg", "wav", "flac", "m4a")
	),;

	private final int offset;
	private final String emoji;
	private final Set<String> extensions;

	private final static Map<Integer, MediaType> BY_VALUE = new HashMap<>(values().length);
	private final static int SUM;

	MediaType(int value, String emoji, Set<String> extensions) {
		this.offset = 2^(value - 1);
		this.emoji = emoji;
		this.extensions = extensions;
	}

	static {
		int sum = 0;
		for (MediaType c : values()) {
			BY_VALUE.put(c.offset, c);
			sum += c.offset;
		}
		SUM = sum;
	}

	public int getOffset() {
		return offset;
	}

	public String getEmoji() {
		return emoji;
	}

	public Set<String> getExtensions() {
		return extensions;
	}

	public static MediaType valueOf(int value) {
		return BY_VALUE.get(value);
	}

	public static int encode(EnumSet<MediaType> input) {
		return input.stream().mapToInt(MediaType::getOffset).sum();
	}

	public static EnumSet<MediaType> decode(int input) {
		if (input == 0) return EnumSet.allOf(MediaType.class);

		EnumSet<MediaType> allowed = EnumSet.noneOf(MediaType.class);
		for (MediaType v : values()) {
			if ((input & v.offset) == v.offset) allowed.add(v);
		}

		return allowed;
	}

	public static int allMedia() {
		return SUM;
	}

	public boolean matches(@Nullable String extension) {
		return extension != null && extensions.contains(normalize(extension));
	}

	public static Optional<MediaType> fromFilename(@Nullable String filename) {
		return extractExtension(filename).flatMap(MediaType::fromExtension);
	}

	public static Optional<MediaType> fromExtension(@Nullable String extension) {
		if (extension == null || extension.isBlank()) return Optional.empty();
		var ext = normalize(extension);
		return Arrays.stream(values())
			.filter(t -> t.extensions.contains(ext))
			.findFirst();
	}

	private static Optional<String> extractExtension(@Nullable String filename) {
		if (filename == null || filename.isBlank()) return Optional.empty();

		int dot = filename.lastIndexOf('.');
		if (dot < 0 || dot == filename.length() - 1) return Optional.empty();
		return Optional.of(filename.substring(dot+1));
	}

	private static String normalize(@NotNull String extension) {
		var e = extension.startsWith(".") ? extension.substring(1) : extension;
		return e.toLowerCase(Locale.ROOT);
	}

}
