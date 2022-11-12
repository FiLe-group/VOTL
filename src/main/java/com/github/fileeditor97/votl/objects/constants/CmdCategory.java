package com.github.fileeditor97.votl.objects.constants;

import com.github.fileeditor97.votl.objects.command.Command.Category;

public final class CmdCategory {
	private CmdCategory() {
		throw new IllegalStateException("Utility class");
	}

	public static final Category GUILD = new Category("guild");
	public static final Category OWNER = new Category("owner");
	public static final Category VOICE = new Category("voice");
	public static final Category WEBHOOK = new Category("webhook");
	public static final Category OTHER = new Category("other");
	
}
