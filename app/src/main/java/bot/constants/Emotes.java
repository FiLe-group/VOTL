package bot.constants;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

public enum Emotes {
	// Animated emotes
	LOADING     ("loading",     "921686838115196988", true),
	TYPING      ("typing",      "921724249213919313", true),
	// Static/Normal emotes
	CHECK       ("check",       "921698379262730260", false),
	WARNING     ("warning",     "921713575557488661", false),
	INFORMATION ("information", "921698379065589800", false),
	SETTINGS_1  ("settings_1",  "921698379342413824", false),
	SETTINGS_2  ("settings_2",  "921698379111743518", false),
	SETTINGS_3  ("settings_3",  "921698378977538088", false),
	PING        ("ping",        "921725669921140736", false),
	CLOUD       ("cloud",       "921698378495176736", false),
	DOWNLOAD    ("download",    "921698378436476959", false),
	FAVORITES   ("favorites",   "921698378964951060", false),
	SHIELD      ("shield",      "921698379082391552", false),
	TROPHY      ("trophy",      "921698380315525170", false),
	MEGAPHONE   ("megaphone",   "921698379011084348", false),
	POWER       ("power",       "921698380277768202", false),
	ADDUSER     ("adduser",     "921698378511974411", false),
	REMOVEUSER  ("removeuser",  "921698379111747624", false);

	private static final Pattern emote_pattern = Pattern.compile("\\{EMOTE_(?<name>[A-Z0-9_]+)}");
	private static final Emotes[] ALL = values();
	
	private final String emoteName;
	private final String id;
	private final boolean animated;

	Emotes(String emoteName, String id, boolean animated) {
		this.emoteName = emoteName;
		this.id = id;
		this.animated = animated;
	}

	public String getEmote() {
		return String.format(
			"<%s:%s:%s>",
			this.animated ? "a" : "",
			this.emoteName,
			this.id
		);
	}

	public String getNameAndId() {
		return String.format(
			"<%s:%s>",
			this.emoteName,
			this.id
		);
	}

	public String getId() {
		return this.id;
	}

	public static String getWithEmotes(String input) {
		Matcher matcher = emote_pattern.matcher(input);
		if (matcher.find()) {
			StringBuilder builder = new StringBuilder();

			do {
				String name = getEmote(matcher.group("name"));
				if (name == null)
					continue;
			
				matcher.appendReplacement(builder, name);
			} while (matcher.find());

			matcher.appendTail(builder);
			input = builder.toString();
		}

		return input;
	}

	private static String getEmote(String name) {
		for (Emotes emote : ALL) {
			if (emote.name().equalsIgnoreCase(name))
				return emote.getEmote();
		}

		return null;
	}
}
