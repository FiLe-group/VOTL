package dev.fileeditor.votl.utils.transcripts;

import net.dv8tion.jda.api.entities.emoji.CustomEmoji;
import net.dv8tion.jda.api.entities.emoji.Emoji;

import java.awt.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Created by Ryzeon
 * Project: discord-html-transcripts
 * Date: 2/12/21 @ 00:32
 * Twitter: @Ryzeon_ 😎
 * GitHub: github.ryzeon.me
 */
public class Formatter {

    private Formatter() {
        throw new UnsupportedOperationException("This is a utility class.");
    }

    // All Message related patterns
    private static final Pattern STRONG = Pattern.compile("\\*\\*(.+?)\\*\\*"); // Bold
    private static final Pattern EM = Pattern.compile("\\*(.+?)\\*|_(.+?)_"); // Italics
    private static final Pattern S = Pattern.compile("~~(.+?)~~"); // Strikethrough
    private static final Pattern U = Pattern.compile("__(.+?)__"); // Underline
    private static final Pattern CODE = Pattern.compile("```(.+?)```"); // Multi-line code block
    private static final Pattern CODE_1 = Pattern.compile("`(.+?)`"); // Code block
    private static final Pattern QUOTE = Pattern.compile("^(?:&gt;){1,3} (.*)$"); // Quote (one line or multiple), matched after escaping
    private static final Pattern HEADING_3 = Pattern.compile("(?m)^### (.+)$"); // Small header
    private static final Pattern HEADING_2 = Pattern.compile("(?m)^## (.+)$"); // Medium header
    private static final Pattern HEADING_1 = Pattern.compile("(?m)^# (.+)$"); // Large header
    private static final Pattern SUBTEXT = Pattern.compile("(?m)^-# (.+)$"); // Small grey text
    private static final Pattern MASKED_LINK = Pattern.compile("\\[([^\\[]+)](\\((www|http:|https:)+\\S+\\w\\))"); // Masked links
    private static final Pattern LINK = Pattern.compile("^(?!.*\\[[^]]*]\\([^)]*\\))((www|http:|https:)\\S+\\w)$"); // Link
    private static final Pattern EMOJI = Pattern.compile("&lt;(a?:[a-zA-Z0-9_]+:[0-9]+)&gt;"); // Emoji, matched after escaping
    private static final Pattern SPOILER = Pattern.compile("\\|\\|(.+?)\\|\\|", Pattern.DOTALL); // Spoiler, can span lines

    // Pattern to detect new lines
    private static final Pattern NEW_LINE = Pattern.compile("\\r\\n|\\r|\\n|\\u2028|\\u2029"); // New line (and it's variants)

    public static String formatBytes(long bytes) {
        int unit = 1024;
        if (bytes < unit)
            return bytes + " B";
        int exp = (int) (Math.log(bytes) / Math.log(unit));
        String pre = String.valueOf("KMGTPE".charAt(exp - 1));
        return String.format("%.1f %sB", bytes / Math.pow(unit, exp), pre);
    }

    /**
     * Escapes HTML special characters, so text can't inject markup.
     */
    public static String escape(String text) {
        StringBuilder builder = new StringBuilder(text.length());
        for (char c : text.toCharArray()) {
            switch (c) {
                case '&' -> builder.append("&amp;");
                case '<' -> builder.append("&lt;");
                case '>' -> builder.append("&gt;");
                case '"' -> builder.append("&quot;");
                case '\'' -> builder.append("&#39;");
                default -> builder.append(c);
            }
        }
        return builder.toString();
    }

    /**
     * Replaces spoilered parts of plain text, for previews where they can't be revealed.
     */
    public static String hideSpoilers(String text) {
        return SPOILER.matcher(text).replaceAll("[spoiler]");
    }

    /**
     * Converts Discord markdown to HTML.
     * The text is escaped first - only markup produced here ends up in the output.
     */
    public static String format(String originalText) {
        String newText = escape(originalText);

        Matcher matcher = HEADING_3.matcher(newText);
        while (matcher.find()) {
            String group = matcher.group();
            newText = newText.replace(group,
                "<span class=\"chatlog__markdown-header chatlog__markdown-header--3\">" + matcher.group(1) + "</span>");
        }
        matcher = HEADING_2.matcher(newText);
        while (matcher.find()) {
            String group = matcher.group();
            newText = newText.replace(group,
                "<span class=\"chatlog__markdown-header chatlog__markdown-header--2\">" + matcher.group(1) + "</span>");
        }
        matcher = HEADING_1.matcher(newText);
        while (matcher.find()) {
            String group = matcher.group();
            newText = newText.replace(group,
                "<span class=\"chatlog__markdown-header chatlog__markdown-header--1\">" + matcher.group(1) + "</span>");
        }
        matcher = SUBTEXT.matcher(newText);
        while (matcher.find()) {
            String group = matcher.group();
            newText = newText.replace(group,
                "<span class=\"chatlog__markdown-subtext\">" + matcher.group(1) + "</span>");
        }

        matcher = STRONG.matcher(newText);
        while (matcher.find()) {
            String group = matcher.group();
            newText = newText.replace(group,
                    "<strong>" + group.replace("**", "") + "</strong>");
        }
        matcher = EM.matcher(newText);
        while (matcher.find()) {
            String group = matcher.group();
            newText = newText.replace(group,
                    "<em>" + group.replace("*", "").replace("_", "") + "</em>");
        }
        matcher = S.matcher(newText);
        while (matcher.find()) {
            String group = matcher.group();
            newText = newText.replace(group,
                    "<s>" + group.replace("~~", "") + "</s>");
        }
        matcher = U.matcher(newText);
        while (matcher.find()) {
            String group = matcher.group();
            newText = newText.replace(group,
                    "<u>" + group.replace("__", "") + "</u>");
        }
        matcher = QUOTE.matcher(newText);
        while (matcher.find()) {
            String group = matcher.group();

            newText = newText.replace(group,
                    "<span class=\"quote\">" + matcher.group(1) + "</span>");
        }
        matcher = MASKED_LINK.matcher(newText);
        while (matcher.find()) {
            String group = matcher.group(1);
            String link = matcher.group(2);
            String raw = "[" + group + "]" + link;

            newText = newText.replace(raw, "<a href=\"" + link.replace("(", "").replace(")", "") + "\">" + group + "</a>");
        }

        matcher = LINK.matcher(newText);
        while (matcher.find()) {
            String link = matcher.group();
            newText = "<a href=\"" + link + "\">" + link + "</a>";
        }

        matcher = CODE.matcher(newText);
        while (matcher.find()) {
            String group = matcher.group();
            newText = newText.replace(group,
                    "<div class=\"pre pre--multiline nohighlight\">"
                            + group.replace("```", "") + "</div>");
        }
        matcher = CODE_1.matcher(newText);
        while (matcher.find()) {
            String group = matcher.group();
            newText = newText.replace(group,
                    "<span class=\"pre pre--inline\">" + group.replace("`", "") + "</span>");
        }
        matcher = EMOJI.matcher(newText);
        while(matcher.find()) {
            String group = matcher.group();
            Emoji emoji = Emoji.fromFormatted("<" + matcher.group(1) + ">");
            if (emoji.getType() == Emoji.Type.CUSTOM) {
                CustomEmoji customEmoji = (CustomEmoji)emoji;
                newText = newText.replace(group,
                        "<img class=\"emoji\" src=\"" + customEmoji.getImageUrl() + "\">");
            }
        }

        newText = SPOILER.matcher(newText)
            .replaceAll("<label class=\"spoiler-text\"><input type=\"checkbox\" class=\"spoiler-toggle\"><span>$1</span></label>");

        matcher = NEW_LINE.matcher(newText);
        while (matcher.find()) {
            newText = newText.replace(matcher.group(), "<br />");
        }
        return newText;
    }

    public static String toHex(Color color) {
        StringBuilder hex = new StringBuilder(Integer.toHexString(color.getRGB() & 0xffffff));
        while (hex.length() < 6) {
            hex.insert(0, "0");
        }
        return hex.toString();
    }
}
