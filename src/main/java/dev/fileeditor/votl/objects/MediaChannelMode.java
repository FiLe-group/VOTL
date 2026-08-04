package dev.fileeditor.votl.objects;

import dev.fileeditor.votl.utils.file.lang.LocaleUtil;
import net.dv8tion.jda.api.interactions.commands.Command;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

/**
 * Defines what a member is allowed to send in a media channel.
 */
public enum MediaChannelMode {
	/** Media (attachments or links) is required, any text alongside it is kept. */
	MEDIA_WITH_COMMENTS(0, "media_mode.media_with_comments"),
	/** Media (attachments or links) is required and nothing else may be sent. */
	MEDIA_ONLY(1, "media_mode.media_only"),
	/** Text is required, attachments and links are removed. */
	COMMENTS_ONLY(2, "media_mode.comments_only"),
	/** Nothing may be sent - members can only use slash commands here. */
	COMMANDS_ONLY(3, "media_mode.commands_only");

	private final int value;
	private final String path;

	private static final Map<Integer, MediaChannelMode> BY_VALUE = new HashMap<>();

	static {
		for (MediaChannelMode mode : values()) {
			BY_VALUE.put(mode.getValue(), mode);
		}
	}

	MediaChannelMode(int value, String path) {
		this.value = value;
		this.path = path;
	}

	public int getValue() {
		return value;
	}

	public String getPath() {
		return path;
	}

	/**
	 * Whether media is expected in this mode, and thus the allowed media types
	 * and the attachment limit are taken into account.
	 */
	public boolean allowsMedia() {
		return this == MEDIA_WITH_COMMENTS || this == MEDIA_ONLY;
	}

	public static MediaChannelMode byValue(int value) {
		return BY_VALUE.getOrDefault(value, MEDIA_WITH_COMMENTS);
	}

	public static List<Command.Choice> asChoices(LocaleUtil lu) {
		return Stream.of(values())
			.map(mode -> new Command.Choice(lu.getText(mode.getPath()), mode.getValue())
				.setNameLocalizations(lu.getLocaleMap(mode.getPath())))
			.toList();
	}
}
