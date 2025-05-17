package dev.fileeditor.votl.middleware.global;

import dev.fileeditor.votl.App;
import dev.fileeditor.votl.base.command.*;
import dev.fileeditor.votl.contracts.middleware.Middleware;
import dev.fileeditor.votl.middleware.MiddlewareStack;
import net.dv8tion.jda.api.events.interaction.command.GenericCommandInteractionEvent;
import org.jetbrains.annotations.NotNull;

public class RunCommand extends Middleware {

	public RunCommand(App bot) {
		super(bot);
	}

	@Override
	public boolean handle(@NotNull GenericCommandInteractionEvent event, @NotNull MiddlewareStack stack, String... args) {
		return switch (stack.getInteraction()) {
			case SlashCommand command -> command.run((SlashCommandEvent) event);
			case MessageContextMenu menu -> menu.run((MessageContextMenuEvent) event);
			case UserContextMenu menu -> menu.run((UserContextMenuEvent) event);
			default -> false;
		};
	}

}
