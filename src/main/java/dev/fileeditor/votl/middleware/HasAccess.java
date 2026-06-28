package dev.fileeditor.votl.middleware;

import dev.fileeditor.votl.App;
import dev.fileeditor.votl.contracts.middleware.Middleware;
import dev.fileeditor.votl.objects.AccessPermission;
import net.dv8tion.jda.api.events.interaction.command.GenericCommandInteractionEvent;
import org.jetbrains.annotations.NotNull;

public class HasAccess extends Middleware {
	public HasAccess(App bot) {
		super(bot);
	}

	@Override
	public boolean handle(@NotNull GenericCommandInteractionEvent event, @NotNull MiddlewareStack stack, String... args) {
		if (!event.isFromGuild()) {
			return stack.next();
		}

		assert event.getMember() != null;

		AccessPermission required = stack.getInteraction().getRequiredPermission();
		if (required == null) return stack.next();

		if (!bot.getCheckUtil().hasAccess(event.getMember(), required)) {
			return runErrorCheck(event, () -> {
				sendError(event, "errors.interaction.no_access");
				return false;
			});
		}
		return stack.next();
	}
}
