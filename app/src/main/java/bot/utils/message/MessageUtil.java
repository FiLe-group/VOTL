package bot.utils.message;

import java.text.DecimalFormat;
import java.time.temporal.TemporalAccessor;
import java.util.Arrays;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.awt.Color;

import net.dv8tion.jda.api.entities.ChannelType;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.GuildChannel;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.Role;
import net.dv8tion.jda.api.utils.TimeFormat;
import bot.App;

public class MessageUtil {

	private final App bot;

	private final Pattern placeholder = Pattern.compile("(\\{(.+?)})", Pattern.CASE_INSENSITIVE);
	private final Pattern rolePattern = Pattern.compile("(\\{r_(name|mention):(\\d+)})", Pattern.CASE_INSENSITIVE);
	private final Pattern channelPattern = Pattern.compile("(\\{c_(name|mention):(\\d+)})", Pattern.CASE_INSENSITIVE);
	
	private final DecimalFormat decimalFormat = new DecimalFormat("#,###,###");

	public MessageUtil(App bot) {
		this.bot = bot;
	}

	public String formatTime(TemporalAccessor time) {
		return String.format(
			"%s (%s)",
			TimeFormat.DATE_TIME_SHORT.format(time),
			TimeFormat.RELATIVE.format(time)
		);
	}

	public Color getColor(String input) {
		input = input.toLowerCase();
		if (!input.equals("random") && !(input.startsWith("hex:") || input.startsWith("rgb:")))
			return null;

		Color color = null;

		if (input.equals("random")) {
			int r = bot.getRandom().nextInt(256);
			int g = bot.getRandom().nextInt(256);
			int b = bot.getRandom().nextInt(256);

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

	public String formatPlaceholders(String msg, Member member) {
		Guild guild = member.getGuild();

		Matcher roleMatcher = rolePattern.matcher(msg);
		if (roleMatcher.find()) {
			StringBuilder builder = new StringBuilder();
			do {
				Role role = guild.getRoleById(roleMatcher.group(3));
				if (role == null)
					continue;

				switch (roleMatcher.group(2).toLowerCase()) {
					case "name":
						roleMatcher.appendReplacement(builder, role.getName());
						break;

					case "mention":
						roleMatcher.appendReplacement(builder, role.getAsMention());
						break;
				}
			} while (roleMatcher.find());

			roleMatcher.appendTail(builder);
			msg = builder.toString();
		}

		Matcher channelMatcher = channelPattern.matcher(msg);
		if (channelMatcher.find()) {
			StringBuilder builder = new StringBuilder();
			do {
				GuildChannel channel = guild.getGuildChannelById(channelMatcher.group(3));
				if (channel == null)
					continue;

				switch (channelMatcher.group(2).toLowerCase()) {
					case "name":
						channelMatcher.appendReplacement(builder, channel.getName());
						break;

					case "mention":
						if (channel.getType().equals(ChannelType.CATEGORY))
							continue;
						channelMatcher.appendReplacement(builder, channel.getAsMention());
						break;
				}
			} while (channelMatcher.find());

			channelMatcher.appendTail(builder);
			msg = builder.toString();
		}

		Matcher matcher = placeholder.matcher(msg);
		if (matcher.find()) {
			StringBuilder builder = new StringBuilder();
			do {
				switch (matcher.group(2).toLowerCase()) {
					case "mention":
						matcher.appendReplacement(builder, member.getAsMention());
						break;

					case "name":
					case "username":
						matcher.appendReplacement(builder, member.getEffectiveName());
						break;

					case "guild":
					case "server":
						matcher.appendReplacement(builder, guild.getName());
						break;

					case "count":
					case "members":
						matcher.appendReplacement(builder, String.valueOf(guild.getMemberCount()));
						break;

					case "count_formatted":
					case "members_formatted":
						matcher.appendReplacement(builder, formatNumber(guild.getMemberCount()));
						break;

					case "tag":
						matcher.appendReplacement(builder, member.getUser().getAsTag());
				}
			} while (matcher.find());

			matcher.appendTail(builder);
			msg = builder.toString();
		}

		return msg;
	}

	public String getFormattedMembers(String id, String... members) {
		if (members.length == 1)
			return "**" + escapeAll(members[0]) + "**";

		StringBuilder builder = new StringBuilder();
		for (String member : members) {
			if (builder.length() > 0)
				builder.append(", ");

			builder.append("**").append(escapeAll(member)).append("**");
		}

		return replaceLast(builder.toString(), ", ", " and ");
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

	public boolean hasArgs(String value, String... args) {
		if (args.length == 0)
			return false;

		for (String arg : args) {
			if (arg.equalsIgnoreCase("--" + value))
				return true;

			if (arg.equalsIgnoreCase("\u2014" + value))
				return true;
		}
		
		return false;
	}

	private String escapeAll(String name) {
		return name.replace("*", "\\*")
			.replace("_", "\\_")
			.replace("`", "\\`")
			.replace("~", "\\~");
	}

}
