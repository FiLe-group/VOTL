package dev.fileeditor.votl.middleware;

import dev.fileeditor.votl.App;
import dev.fileeditor.votl.contracts.middleware.Middleware;
import dev.fileeditor.votl.utils.exception.CheckException;
import net.dv8tion.jda.api.events.interaction.command.GenericCommandInteractionEvent;
import org.jetbrains.annotations.NotNull;

public class PermissionsCheck extends Middleware {

	public PermissionsCheck(App bot) {
		super(bot);
	}

	@Override
	public boolean handle(@NotNull GenericCommandInteractionEvent event, @NotNull MiddlewareStack stack, String... args) {
		if (!event.isFromGuild()) {
			return stack.next();
		}

		assert event.getMember() != null;
		try {
			bot.getCheckUtil()
				.hasPermissions(event, stack.getInteraction().getBotPermissions())
				.hasPermissions(event, stack.getInteraction().getUserPermissions(), event.getMember());
		} catch (CheckException e) {
			return runErrorCheck(event, () -> {
				sendError(event, e.getEditData());
				return false;
			});
		}

		return stack.next();
	}

}
