package dev.fileeditor.votl.utils.file.lang;

import java.util.List;

import dev.fileeditor.votl.objects.annotation.Nonnull;
import dev.fileeditor.votl.objects.annotation.Nullable;
import dev.fileeditor.votl.utils.file.FileManager;

import net.dv8tion.jda.api.interactions.DiscordLocale;

@SuppressWarnings("SwitchStatementWithTooFewBranches")
public final class LangUtil {

	private final FileManager fileManager;
	
	public LangUtil(FileManager fileManager) {
		this.fileManager = fileManager;
	}
	
	@dev.fileeditor.votl.objects.annotation.Nonnull
	public String getString(DiscordLocale locale, String path) {
		return switch (locale) {
			case RUSSIAN -> fileManager.getString(locale.getLocale(), path);
			default -> fileManager.getString("en-GB", path);
		};
	}

	@Nullable
	public String getNullableString(DiscordLocale locale, String path) {
		return switch (locale) {
			case RUSSIAN -> fileManager.getNullableString(locale.getLocale(), path);
			default -> fileManager.getNullableString("en-GB", path);
		};
	}

	@Nonnull
	public List<String> getStringList(DiscordLocale locale, String path) {
		return switch (locale) {
			case RUSSIAN -> fileManager.getStringList(locale.getLocale(), path);
			default -> fileManager.getStringList("en-GB", path);
		};
	}

}
