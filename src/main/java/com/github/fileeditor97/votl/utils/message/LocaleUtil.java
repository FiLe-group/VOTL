package com.github.fileeditor97.votl.utils.message;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import com.github.fileeditor97.votl.App;
import com.github.fileeditor97.votl.objects.Emotes;
import com.github.fileeditor97.votl.objects.command.CommandEvent;
import com.github.fileeditor97.votl.objects.command.MessageContextMenuEvent;
import com.github.fileeditor97.votl.objects.command.SlashCommandEvent;
import com.github.fileeditor97.votl.objects.constants.Constants;
import com.github.fileeditor97.votl.objects.constants.Links;
import com.github.fileeditor97.votl.utils.file.lang.LangUtil;
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
	@SuppressWarnings("null")
	public String getDefaultLanguage() {
		return defaultLanguage;
	}

	@Nonnull
	@SuppressWarnings("null")
	public DiscordLocale getGuildLocale(@Nullable Guild guild) {
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
			.replace("{guild_invite}", Links.DISCORD)
			.replace("{owner_id}", bot.getFileManager().getString("config", "owner-id"))
			.replace("{developer_name}", Constants.DEVELOPER_NAME)
			.replace("{developer_id}", Constants.DEVELOPER_ID)
			.replace("{bot_invite}", bot.getFileManager().getString("config", "bot-invite"))
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
		throw new IllegalArgumentException("Argument passed is not supported event. Received: "+genericEvent.getClass());
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
	public <T> String getUserText(T genericEvent, @Nonnull String path, String user, String target) {
		return getUserText(genericEvent, path, Collections.singletonList(target), false);
	}
	
	@Nonnull
	public <T> String getUserText(T genericEvent, @Nonnull String path, String user, List<String> targets) {
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
		throw new IllegalArgumentException("Argument passed is not supported event. Received: "+genericEvent.getClass());
	} 
}
