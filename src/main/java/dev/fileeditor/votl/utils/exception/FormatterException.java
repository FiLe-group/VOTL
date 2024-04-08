package dev.fileeditor.votl.utils.exception;

import dev.fileeditor.votl.objects.annotation.Nonnull;

public class FormatterException extends Exception {
	private final String path;
	
	public FormatterException(@Nonnull String path) {
		super();
		this.path = path;
	}

	@Nonnull
	public String getPath() {
		return path;
	}
}
