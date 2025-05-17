package dev.fileeditor.votl.contracts.reflection;

import dev.fileeditor.votl.App;

public abstract class Reflectionable implements Reflectional {
	protected final App bot;

	public Reflectionable(final App bot) {
		this.bot = bot;
	}
}
