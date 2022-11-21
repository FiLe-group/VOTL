package votl.utils;

import java.time.Duration;
import java.util.HashMap;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.annotation.Nullable;

import votl.utils.exception.FormatterException;

public class FormatUtil {

	private final Pattern timePatternFull = Pattern.compile("^(([0-9]+)([smhdw]{1}))+$", Pattern.CASE_INSENSITIVE);
	private final Pattern timePattern = Pattern.compile("([0-9]+)([smhdw]{1})", Pattern.CASE_INSENSITIVE);
	
	public FormatUtil() {}

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

}
