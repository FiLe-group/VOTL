package dev.fileeditor.votl.middleware.global;

import dev.fileeditor.votl.App;
import dev.fileeditor.votl.contracts.middleware.Middleware;
import dev.fileeditor.votl.middleware.MiddlewareStack;
import net.dv8tion.jda.api.events.interaction.command.GenericCommandInteractionEvent;
import org.jetbrains.annotations.NotNull;

public class IsModuleEnabled extends Middleware {

	public IsModuleEnabled(App bot) {
		super(bot);
	}

	@Override
	public boolean handle(@NotNull GenericCommandInteractionEvent event, @NotNull MiddlewareStack stack, String... args) {
		if (!event.isFromGuild()) {
			return stack.next();
		}

		if (stack.getInteraction().getModule() == null) {
			return stack.next();
		}

		if (bot.getDBUtil().getGuildSettings(event.getGuild()).isDisabled(stack.getInteraction().getModule())) {
			return runErrorCheck(event, () -> {
				sendError(event, "modules.module_disabled");

				return false;
			});
		}

		return stack.next();
	}

}
