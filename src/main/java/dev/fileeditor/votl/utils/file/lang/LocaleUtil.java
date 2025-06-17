package dev.fileeditor.votl.utils.file.lang;

import dev.fileeditor.votl.App;
import dev.fileeditor.votl.objects.Emote;

import dev.fileeditor.votl.utils.message.MessageUtil;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.interactions.DiscordLocale;
import net.dv8tion.jda.api.interactions.callbacks.IReplyCallback;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

@SuppressWarnings("unused")
public class LocaleUtil {

	private final App bot;
	private final LangUtil langUtil;

	public static final DiscordLocale DEFAULT_LOCALE = DiscordLocale.ENGLISH_UK;

	public LocaleUtil(App bot) {
		this.bot = bot;
		this.langUtil = new LangUtil(bot.getFileManager());
	}

	@NotNull
	public String getLocalized(DiscordLocale locale, String path) {
		return Emote.getWithEmotes(langUtil.getString(locale, path));
	}

	@NotNull
	public String getLocalized(DiscordLocale locale, String path, User user) {
		return getLocalized(locale, path, user, true);
	}

	@NotNull
	public String getLocalized(DiscordLocale locale, String path, User user, boolean format) {
		String name = user.getEffectiveName();
		if (format)
			name = MessageUtil.getFormattedUsers(this, name);

		return Objects.requireNonNull(getLocalized(locale, path)
			.replace("{user}", name));
	}

	@NotNull
	public String getLocalized(DiscordLocale locale, String path, User user, String target) {
		if (target == null)
			return getLocalized(locale, path, user, List.of(), false);
		else
			return getLocalized(locale, path, user, List.of(target), false);
	}

	@NotNull
	public String getLocalized(DiscordLocale locale, String path, User user, List<String> targets) {
		return getLocalized(locale, path, user, targets, false);
	}

	@NotNull
	public String getLocalized(DiscordLocale locale, String path, User user, List<String> targets, boolean format) {
		String targetReplacement = targets.isEmpty() ? "null" : MessageUtil.getFormattedUsers(this, targets.toArray(String[]::new));

		return Objects.requireNonNull(getLocalized(locale, path, user, format)
			.replace("{target}", targetReplacement)
			.replace("{targets}", targetReplacement)
		);
	}

	@Nullable
	public String getLocalizedNullable(DiscordLocale locale, String path) {
		return langUtil.getNullableString(locale, path);
	}

	@NotNull
	public String getLocalizedRandom(DiscordLocale locale, String path) {
		return Emote.getWithEmotes(langUtil.getRandomString(locale, path));
	}

	@NotNull
	public Map<DiscordLocale, String> getFullLocaleMap(String path, String defaultText) {
		Map<DiscordLocale, String> localeMap = new HashMap<>();
		for (DiscordLocale locale : bot.getFileManager().getLanguages()) {
			// Ignores UK/US change
			if (locale.equals(DiscordLocale.ENGLISH_UK) || locale.equals(DiscordLocale.ENGLISH_US)) continue;
			localeMap.put(locale, getLocalized(locale, path));
		}
		localeMap.put(DiscordLocale.ENGLISH_UK, defaultText);
		localeMap.put(DiscordLocale.ENGLISH_US, defaultText);
		return localeMap;
	}

	@NotNull
	public Map<DiscordLocale, String> getLocaleMap(String path) {
		Map<DiscordLocale, String> localeMap = new HashMap<>();
		for (DiscordLocale locale : bot.getFileManager().getLanguages()) {
			// Ignores UK/US change
			if (locale.equals(DiscordLocale.ENGLISH_UK) || locale.equals(DiscordLocale.ENGLISH_US)) continue;
			localeMap.put(locale, getLocalized(locale, path));
		}
		return localeMap;
	}


	@NotNull
	public String getText(@NotNull String path) {
		return getLocalized(DEFAULT_LOCALE, path);
	}

	@NotNull
	public String getText(IReplyCallback replyCallback, @NotNull String path) {
		return getLocalized(getLocale(replyCallback), path);
	}

	@NotNull
	public String getUserText(IReplyCallback replyCallback, @NotNull String path) {
		return getUserText(replyCallback, path, List.of(), false);
	}

	@NotNull
	public String getUserText(IReplyCallback replyCallback, @NotNull String path, boolean format) {
		return getUserText(replyCallback, path, List.of(), format);
	}

	@NotNull
	public String getUserText(IReplyCallback replyCallback, @NotNull String path, String target) {
		return getUserText(replyCallback, path, List.of(target), false);
	}
	
	@NotNull
	public String getUserText(IReplyCallback replyCallback, @NotNull String path, List<String> targets) {
		return getUserText(replyCallback, path, targets, false);
	}

	@NotNull
	private String getUserText(IReplyCallback replyCallback, @NotNull String path, List<String> targets, boolean format) {
		return getLocalized(replyCallback.getUserLocale(), path, replyCallback.getUser(), targets, format);
	}

	@NotNull
	public String getGuildText(IReplyCallback replyCallback, @NotNull String path) {
		DiscordLocale locale = App.getInstance().getDBUtil().getGuildSettings(replyCallback.getGuild()).getLocale();
		if (locale == DiscordLocale.UNKNOWN)
			locale = replyCallback.getGuildLocale();

		return getLocalized(locale, path);
	}

	@NotNull
	public DiscordLocale getLocale(IReplyCallback replyCallback) {
		if (replyCallback.isFromGuild()) {
			DiscordLocale locale = App.getInstance().getDBUtil().getGuildSettings(replyCallback.getGuild()).getLocale();
			if (locale == DiscordLocale.UNKNOWN) {
				return replyCallback.getGuildLocale();
			} else {
				return locale;
			}
		} else {
			return replyCallback.getUserLocale();
		}
	}

}
