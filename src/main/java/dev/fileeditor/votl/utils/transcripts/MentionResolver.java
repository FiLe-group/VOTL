package dev.fileeditor.votl.utils.transcripts;

import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.LongFunction;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.Role;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.entities.channel.middleman.GuildChannel;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jsoup.nodes.Element;
import org.jsoup.nodes.Node;
import org.jsoup.nodes.TextNode;

/**
 * Replaces Discord markup in transcript text with readable names.
 * Embeds and components keep raw markup ({@code <@id>}), unlike message content display.
 */
public class MentionResolver {

	private static final Pattern MARKUP = Pattern.compile(
		"<(@!?|@&|#)(\\d{17,20})>"							// 1: type, 2: ID
		+ "|</([-_\\p{L}\\p{N} ]{1,100}):\\d{17,20}>"		// 3: slash command name
		+ "|<t:(-?\\d{1,13})(?::([tTdDfFR]))?>"				// 4: epoch seconds, 5: style
	);

	private static final DateTimeFormatter TIME = formatter("HH:mm");
	private static final DateTimeFormatter TIME_SECONDS = formatter("HH:mm:ss");
	private static final DateTimeFormatter DATE = formatter("dd.MM.yyyy");
	private static final DateTimeFormatter DATE_TIME = formatter("dd.MM.yyyy HH:mm");

	private final LongFunction<String> userName, roleName, channelName;

	/**
	 * @param userName user ID -> display name, null if unknown
	 * @param roleName role ID -> name, null if unknown
	 * @param channelName channel ID -> name, null if unknown
	 */
	public MentionResolver(LongFunction<String> userName, LongFunction<String> roleName, LongFunction<String> channelName) {
		this.userName = userName;
		this.roleName = roleName;
		this.channelName = channelName;
	}

	/**
	 * Resolves names from cache - all members are cached.
	 */
	public static MentionResolver of(@NotNull Guild guild) {
		return new MentionResolver(
			id -> Optional.ofNullable(guild.getMemberById(id)).map(Member::getEffectiveName)
				.or(() -> Optional.ofNullable(guild.getJDA().getUserById(id)).map(User::getEffectiveName))
				.orElse(null),
			id -> Optional.ofNullable(guild.getRoleById(id)).map(Role::getName).orElse(null),
			id -> Optional.ofNullable(guild.getGuildChannelById(id)).map(GuildChannel::getName).orElse(null)
		);
	}

	public void resolve(@NotNull Element root) {
		List<TextNode> textNodes = new ArrayList<>();
		root.traverse((node, _) -> {
			if (node instanceof TextNode text && !insideCode(text)) textNodes.add(text);
		});
		textNodes.forEach(this::replace);
	}

	// Discord shows markup in code blocks as is
	private static boolean insideCode(Node node) {
		for (Element parent = (Element) node.parent(); parent != null; parent = parent.parent()) {
			if (parent.hasClass("pre")) return true;
		}
		return false;
	}

	private void replace(TextNode node) {
		String text = node.getWholeText();
		Matcher matcher = MARKUP.matcher(text);
		if (!matcher.find()) return;

		List<Node> replacement = new ArrayList<>();
		int last = 0;
		do {
			if (matcher.start() > last) replacement.add(new TextNode(text.substring(last, matcher.start())));
			replacement.add(toElement(matcher));
			last = matcher.end();
		} while (matcher.find());
		if (last < text.length()) replacement.add(new TextNode(text.substring(last)));

		for (Node part : replacement) node.before(part);
		node.remove();
	}

	private Element toElement(Matcher matcher) {
		if (matcher.group(1) != null) {
			long id = Long.parseLong(matcher.group(2));
			String label = switch (matcher.group(1)) {
				case "@&" -> "@" + orElse(roleName.apply(id), "deleted-role");
				case "#" -> "#" + orElse(channelName.apply(id), "unknown");
				default -> "@" + orElse(userName.apply(id), "unknown-user");
			};
			return new Element("span").addClass("mention").text(label);
		}
		if (matcher.group(3) != null) {
			return new Element("span").addClass("mention").text("/" + matcher.group(3));
		}

		Instant time = Instant.ofEpochSecond(Long.parseLong(matcher.group(4)));
		String style = matcher.group(5) == null ? "f" : matcher.group(5);
		DateTimeFormatter format = switch (style) {
			case "t" -> TIME;
			case "T" -> TIME_SECONDS;
			case "d", "D" -> DATE;
			default -> DATE_TIME; // f, F and R - relative time would be outdated
		};
		return new Element("span").addClass("timestamp")
			.attr("title", DateTimeFormatter.ISO_INSTANT.format(time))
			.text(format.format(time) + (format == DATE ? "" : " UTC"));
	}

	private static String orElse(@Nullable String value, String fallback) {
		return value == null ? fallback : value;
	}

	private static DateTimeFormatter formatter(String pattern) {
		return DateTimeFormatter.ofPattern(pattern).withZone(ZoneOffset.UTC);
	}

}
