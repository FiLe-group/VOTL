package votl.objects;

import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

public enum Emotes {
	// Animated emotes
	LOADING     ("loading",     "960102018217828352", true),
	TYPING      ("typing",      "960102038291742750", true),
	THINKING	("thinking",    "960102089919447080", true),
	// Static/Normal emotes
	CHECK       ("check",       "960101819428769812", false),
	WARNING     ("warning",     "960101573571276820", false),
	INFORMATION ("information", "960101921362964511", false),
	SETTINGS_1  ("settings_1",  "960101709630275584", false),
	SETTINGS_2  ("settings_2",  "960101748775714816", false),
	SETTINGS_3  ("settings_3",  "960101769097150474", false),
	PING        ("ping",        "960101551857360906", false),
	CLOUD       ("cloud",       "960101979659599872", false),
	DOWNLOAD    ("download",    "960101994402562068", false),
	FAVORITES   ("favorites",   "960101970771845161", false),
	SHIELD      ("shield",      "960101908750663760", false),
	TROPHY      ("trophy",      "960101605091454996", false),
	MEGAPHONE   ("megaphone",   "960101946243571802", false),
	POWER       ("power",       "960101627136737280", false),
	ADDUSER     ("adduser",     "960101846687551508", false),
	REMOVEUSER  ("removeuser",  "960101868577648640", false);

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

	@Nonnull
	public static String getWithEmotes(@Nonnull String input) {
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
			return Objects.requireNonNull(builder.toString());
		}

		return input;
	}

	@Nullable
	private static String getEmote(String name) {
		for (Emotes emote : ALL) {
			if (emote.name().equalsIgnoreCase(name))
				return emote.getEmote();
		}

		return null;
	}
}
