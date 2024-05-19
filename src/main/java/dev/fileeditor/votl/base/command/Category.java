package dev.fileeditor.votl.base.command;

import java.util.Objects;

/**
 * To be used in {@link dev.fileeditor.votl.base.command.SlashCommand SlashCommand}s as a means of
 * organizing commands into "Categories".
 *
 * @author John Grosh (jagrosh)
 */
public class Category {
	private final String name;

	/**
	 * A Command Category containing a name.
	 *
	 * @param  name
	 *         The name of the Category
	 */
	public Category(String name) {
		this.name = name;
	}

	/**
	 * Gets the name of the Category.
	 *
	 * @return The name of the Category
	 */
	public String getName()
	{
		return name;
	}

	@Override
	public boolean equals(Object obj) {
		if (!(obj instanceof Category other))
			return false;
		return Objects.equals(name, other.name);
	}
}

