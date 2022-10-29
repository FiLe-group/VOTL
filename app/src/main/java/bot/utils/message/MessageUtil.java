package bot.utils.message;

import java.text.DecimalFormat;
import java.time.temporal.TemporalAccessor;
import java.util.Arrays;
import java.util.Random;
import java.awt.Color;

import net.dv8tion.jda.api.interactions.DiscordLocale;
import net.dv8tion.jda.api.utils.TimeFormat;
import bot.App;

public class MessageUtil {

	private final Random random;
	private final LocaleUtil lu;
	
	private final DecimalFormat decimalFormat = new DecimalFormat("#,###,###");

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
