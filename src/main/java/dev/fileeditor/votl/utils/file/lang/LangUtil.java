package dev.fileeditor.votl.utils.file.lang;

import java.util.List;

import dev.fileeditor.votl.objects.annotation.Nonnull;
import dev.fileeditor.votl.objects.annotation.Nullable;
import dev.fileeditor.votl.utils.file.FileManager;

import net.dv8tion.jda.api.interactions.DiscordLocale;

public final class LangUtil {

	private final FileManager fileManager;
	
	public LangUtil(FileManager fileManager) {
		this.fileManager = fileManager;
	}
	
	@dev.fileeditor.votl.objects.annotation.Nonnull
	public String getString(DiscordLocale locale, String path) {
		switch (locale) {
			case RUSSIAN:
				return fileManager.getString(locale.getLocale(), path);
			default: //en
				return fileManager.getString("en-GB", path);
		}
	}

	@Nullable
	public String getNullableString(DiscordLocale locale, String path) {
		switch (locale) {
			case RUSSIAN:
				return fileManager.getNullableString(locale.getLocale(), path);
			default: //en
				return fileManager.getNullableString("en-GB", path);
		}
	}

	@Nonnull
	public List<String> getStringList(DiscordLocale locale, String path) {
		switch (locale) {
			case RUSSIAN:
				return fileManager.getStringList(locale.getLocale(), path);
			default: //en
				return fileManager.getStringList("en-GB", path);
		}
	}

}
