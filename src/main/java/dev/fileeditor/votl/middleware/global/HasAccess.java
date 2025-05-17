package dev.fileeditor.votl.middleware.global;

import dev.fileeditor.votl.App;
import dev.fileeditor.votl.contracts.middleware.Middleware;
import dev.fileeditor.votl.middleware.MiddlewareStack;
import dev.fileeditor.votl.objects.CmdAccessLevel;
import net.dv8tion.jda.api.events.interaction.command.GenericCommandInteractionEvent;
import org.jetbrains.annotations.NotNull;

public class HasAccess extends Middleware {
	public HasAccess(App bot) {
		super(bot);
	}

	@Override
	public boolean handle(@NotNull GenericCommandInteractionEvent event, @NotNull MiddlewareStack stack, String... args) {
		if (stack.getInteraction().getAccessLevel().equals(CmdAccessLevel.ALL)) {
			return stack.next();
		}

		if (bot.getCheckUtil().getAccessLevel(event.getMember()).isLowerThan(stack.getInteraction().getAccessLevel())) {
			return runErrorCheck(event, () -> {
				sendError(event, "errors.interaction.no_access", "Required access: "+stack.getInteraction().getAccessLevel().getName());
				return false;
			});
		}

		return stack.next();
	}
}
