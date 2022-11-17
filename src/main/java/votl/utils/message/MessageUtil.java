package votl.utils.message;

import java.awt.Color;
import java.text.DecimalFormat;
import java.time.Duration;
import java.time.temporal.TemporalAccessor;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Optional;
import java.util.Random;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.annotation.Nullable;

import votl.App;
import votl.utils.exception.FormatterException;

import net.dv8tion.jda.api.interactions.DiscordLocale;
import net.dv8tion.jda.api.utils.TimeFormat;

public class MessageUtil {

	private final Random random;
	private final LocaleUtil lu;

	private final Pattern timePatternFull = Pattern.compile("^(([0-9]+)([smhdw]{1}))+$", Pattern.CASE_INSENSITIVE);
	private final Pattern timePattern = Pattern.compile("([0-9]+)([smhdw]{1})", Pattern.CASE_INSENSITIVE);
	
	private final DecimalFormat decimalFormat = new DecimalFormat("# ### ###");

	public MessageUtil(App bot) {
		this.random = bot.getRandom();
		this.lu = bot.getLocaleUtil();
	}

	public String formatTime(TemporalAccessor time) {
		if (time != null) {
			return String.format(
				"%s (%s)",
				TimeFormat.DATE_TIME_SHORT.format(time),
				TimeFormat.RELATIVE.format(time)
			);
		}
		return "";
	}

	private enum TimeFormats{
		SECONDS('s', 1),
		MINUTES('m', 60),
		HOURS  ('h', 3600),
		DAYS   ('d', 86400),
		WEEKS  ('w', 604800);

		private final Character character;
		private final Integer multip;

		private static final HashMap<Character, TimeFormats> BY_CHAR = new HashMap<Character, TimeFormats>();

		static {
			for (TimeFormats format : TimeFormats.values()) {
				BY_CHAR.put(format.getChar(), format);
			}
		}

		TimeFormats(Character character, Integer multip) {
			this.character = character;
			this.multip = multip;
		}

		public Character getChar() {
			return character;
		}

		public Integer getMultip() {
			return multip;
		}

		@Nullable
		public static Integer getMultipByChar(Character c) {
			return Optional.ofNullable(BY_CHAR.get(c)).map(tf -> tf.getMultip()).orElse(null);
		}
	}

	/*
	 * Duration and Period class have parse() method,
	 * but they are quite inconvinient, as we want to
	 * use both duration(h m s) and period(w d).
	 */
	public Duration getDuration(String text, boolean allowSeconds) throws FormatterException {
		if (text == null || text.isEmpty()) {
			return Duration.ZERO;
		}

		if (!timePatternFull.matcher(text).matches()) {
			throw new FormatterException("errors.formatter.no_time_provided");
		}
		
		Matcher timeMatcher = timePattern.matcher(text);
		Long time = 0L;
		while (timeMatcher.find()) {
			Character c = timeMatcher.group(2).charAt(0);
			if (c.equals('s') && !allowSeconds) {
				throw new FormatterException("errors.formatter.except_seconds");
			}
			Integer multip = TimeFormats.getMultipByChar(c);
			if (multip == null) {
				throw new FormatterException("errors.formatter.no_multip");
			}

			try {
				time = Math.addExact(time, Math.multiplyExact(Long.valueOf(timeMatcher.group(1)), multip));
			} catch (NumberFormatException ex) {
				throw new FormatterException("errors.formatter.parse_long");
			} catch (ArithmeticException ex) {
				throw new FormatterException("errors.formatter.long_owerflow");
			}
		}
		
		return Duration.ofSeconds(time);
	}

	public String capitalize(final String str) {
		if (str == null || str.length() == 0) {
			return "";
		}

		final String s0 = str.substring(0, 1).toUpperCase();
		return s0 + str.substring(1);
	}

	public Color getColor(String input) {
		input = input.toLowerCase();
		if (!input.equals("random") && !(input.startsWith("hex:") || input.startsWith("rgb:")))
			return null;

		Color color = null;

		if (input.equals("random")) {
			int r = random.nextInt(256);
			int g = random.nextInt(256);
			int b = random.nextInt(256);

			return new Color(r, g, b);
		}

		String[] split = input.split(":");
		if (split.length <= 1)
			return null;

		String value = split[1];

		switch (split[0]) {
			case "hex":
				if (value.isEmpty())
					return null;
				try {
					color = Color.decode(value.startsWith("#") ? value : "#" + value);
				} catch (NumberFormatException ignored) {
					return null;
				}
				break;

			case "rgb":
				if (value.isEmpty())
					return null;
				String[] rgb = Arrays.copyOf(value.split(","), 3);
				try {
					color = new Color(Integer.parseInt(rgb[0]), Integer.parseInt(rgb[1]), Integer.parseInt(rgb[2]));
				} catch (NumberFormatException ignored) {
					return null;
				}
		}

		return color;
	}

	public String getFormattedMembers(DiscordLocale locale, String... members) {
		if (members.length == 1)
			return "**" + escapeAll(members[0]) + "**";

		StringBuilder builder = new StringBuilder();
		for (String member : members) {
			if (builder.length() > 0)
				builder.append(", ");

			builder.append("**").append(escapeAll(member)).append("**");
		}

		return replaceLast(builder.toString(), ", ", " "+lu.getText("misc.and")+" ");
	}

	public String replaceLast(String input, String target, String replacement) {
		if (!input.contains(target))
			return input;

		StringBuilder builder = new StringBuilder(input);
		builder.replace(input.lastIndexOf(target), input.lastIndexOf(target) + 1, replacement);

		return builder.toString();
	}

	public String formatNumber(long number) {
		return decimalFormat.format(number);
	}

	private String escapeAll(String name) {
		return name.replace("*", "\\*")
			.replace("_", "\\_")
			.replace("`", "\\`")
			.replace("~", "\\~");
	}

}
