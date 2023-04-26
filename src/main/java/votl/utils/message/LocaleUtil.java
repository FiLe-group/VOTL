package votl.utils.message;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import votl.App;
import votl.objects.Emotes;
import votl.objects.command.CommandEvent;
import votl.objects.command.MessageContextMenuEvent;
import votl.objects.command.SlashCommandEvent;
import votl.objects.constants.Constants;
import votl.objects.constants.Links;
import votl.utils.file.lang.LangUtil;

import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.interactions.DiscordLocale;

public class LocaleUtil {

	private final App bot;
	private final LangUtil langUtil;
	private final String defaultLanguage;
	private final DiscordLocale defaultLocale;

	public LocaleUtil(App bot, LangUtil langUtil, String defaultLanguage, DiscordLocale defaultLocale) {
		this.bot = bot;
		this.langUtil = langUtil;
		this.defaultLanguage = defaultLanguage;
		this.defaultLocale = defaultLocale;
	}

	@Nonnull
	public String getDefaultLanguage() {
		return defaultLanguage;
	}

	@Nonnull
	private DiscordLocale getGuildLocale(@Nullable Guild guild) {
		return (guild == null ? defaultLocale : guild.getLocale());
	}

	@Nonnull
	public String getLocalized(DiscordLocale locale, String path) {
		return setPlaceholders(langUtil.getString(locale.getLocale(), path));
	}

	@Nonnull
	public String getLocalized(DiscordLocale locale, String path, String user) {
		return getLocalized(locale, path, user, true);
	}

	@Nonnull
	public String getLocalized(DiscordLocale locale, String path, String user, boolean format) {
		if (format)
			user = bot.getMessageUtil().getFormattedMembers(locale, user);

		return Objects.requireNonNull(getLocalized(locale, path).replace("{user}", user));
	}

	@Nonnull
	public String getLocalized(DiscordLocale locale, String path, String user, String target) {
		target = (target == null ? "null" : target);
		
		return getLocalized(locale, path, user, Collections.singletonList(target), false);
	}

	@Nonnull
	public String getLocalized(DiscordLocale locale, String path, String user, List<String> targets) {
		return getLocalized(locale, path, user, targets, false);
	}

	@Nonnull
	public String getLocalized(DiscordLocale locale, String path, String user, List<String> targets, boolean format) {
		String targetReplacement = targets.isEmpty() ? "null" : bot.getMessageUtil().getFormattedMembers(locale, targets.toArray(new String[0]));

		return Objects.requireNonNull(getLocalized(locale, path, user, format)
			.replace("{target}", targetReplacement)
			.replace("{targets}", targetReplacement)
		);
	}

	@Nonnull
	public Map<DiscordLocale, String> getFullLocaleMap(String path) {
		Map<DiscordLocale, String> localeMap = new HashMap<>();
		for (DiscordLocale locale : bot.getFileManager().getLanguages()) {
			// Also counts en-US as en-GB (otherwise rises problem)
			// Later may be changed
			if (locale.getLocale().equals("en-GB"))
				localeMap.put(DiscordLocale.ENGLISH_US, getLocalized(DiscordLocale.ENGLISH_US, path));
			localeMap.put(locale, getLocalized(locale, path));
		}
		return localeMap;
	}

	@Nonnull
	private String setPlaceholders(@Nonnull String msg) {
		return Objects.requireNonNull(Emotes.getWithEmotes(msg)
			.replace("{name}", "Voice of the Lord")
			.replace("{prefix}", "/")
			.replace("{guild_invite}", Links.DISCORD)
			.replace("{developer_name}", Constants.DEVELOPER_TAG)
			.replace("{developer_id}", Constants.DEVELOPER_ID)
			.replace("{bot_version}", bot.version)
		);
	}

	@Nonnull
	public String getText(@Nonnull String path) {
		return getLocalized(defaultLocale, path);
	}

	@Nonnull
	public <T> String getText(T genericEvent, @Nonnull String path) {
		if (genericEvent instanceof SlashCommandEvent) {
			return getLocalized(((SlashCommandEvent) genericEvent).getUserLocale(), path);
		}
		if (genericEvent instanceof CommandEvent) {
			return getLocalized(getGuildLocale( ((CommandEvent) genericEvent).getGuild() ), path);
		}
		if (genericEvent instanceof MessageContextMenuEvent) {
			return getLocalized(((MessageContextMenuEvent) genericEvent).getUserLocale(), path);
		}
		throw new IllegalArgumentException("Passed argument is not supported Event. Received: "+genericEvent.getClass());
	}

	@Nonnull
	public <T> String getUserText(T genericEvent, @Nonnull String path) {
		return getUserText(genericEvent, path, Collections.emptyList(), false);
	}

	@Nonnull
	public <T> String getUserText(T genericEvent, @Nonnull String path, boolean format) {
		return getUserText(genericEvent, path, Collections.emptyList(), format);
	}

	@Nonnull
	public <T> String getUserText(T genericEvent, @Nonnull String path, String target) {
		return getUserText(genericEvent, path, Collections.singletonList(target), false);
	}
	
	@Nonnull
	public <T> String getUserText(T genericEvent, @Nonnull String path, List<String> targets) {
		return getUserText(genericEvent, path, targets, false);
	}

	@Nonnull
	private <T> String getUserText(T genericEvent, @Nonnull String path, List<String> targets, boolean format) {
		if (genericEvent instanceof SlashCommandEvent) {
			SlashCommandEvent event = (SlashCommandEvent) genericEvent;
			return getLocalized(event.getUserLocale(), path, event.getUser().getAsTag(), targets, format);
		}
		if (genericEvent instanceof CommandEvent) {
			CommandEvent event = (CommandEvent) genericEvent;
			return getLocalized(getGuildLocale(event.getGuild()), path, event.getAuthor().getAsTag(), targets, format);
		}
		if (genericEvent instanceof MessageContextMenuEvent) {
			MessageContextMenuEvent event = (MessageContextMenuEvent) genericEvent;
			return getLocalized(event.getUserLocale(), path, event.getUser().getAsTag(), targets, format);
		}
		throw new IllegalArgumentException("Passed argument is not supported Event. Received: "+genericEvent.getClass());
	}

}
