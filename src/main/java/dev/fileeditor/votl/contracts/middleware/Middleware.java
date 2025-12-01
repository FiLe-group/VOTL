package dev.fileeditor.votl.contracts.middleware;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import dev.fileeditor.votl.App;
import dev.fileeditor.votl.middleware.MessageFactory;
import dev.fileeditor.votl.middleware.MiddlewareStack;
import net.dv8tion.jda.api.events.interaction.command.GenericCommandInteractionEvent;
import net.dv8tion.jda.api.utils.messages.MessageEditData;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Objects;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import java.util.function.Supplier;

@SuppressWarnings("SameParameterValue")
public abstract class Middleware {

	@SuppressWarnings("NullableProblems")
	private static final Cache<Long, Boolean> errorCache = Caffeine.newBuilder()
		.expireAfterWrite(2000, TimeUnit.MILLISECONDS)
		.build();

	protected final App bot;

	public Middleware(App bot) {
		this.bot = bot;
	}

	public abstract boolean handle(@NotNull GenericCommandInteractionEvent event, @NotNull MiddlewareStack stack, String... args);

	protected boolean runErrorCheck(@NotNull GenericCommandInteractionEvent event, @NotNull Supplier<Boolean> callback) {
		return Objects.requireNonNull(
			errorCache.get(event.getUser().getIdLong(), _ -> Objects.requireNonNullElse(callback.get(), false))
		);
	}

	// Send Error
	protected void sendErrorMsg(GenericCommandInteractionEvent event, @NotNull String message) {
		sendError(event, new MessageFactory(bot, event)
			.asEmbed(true)
			.setType(MessageFactory.MessageType.ERROR)
			.setMessage(message)
			.build()
		);
	}

	protected void sendError(GenericCommandInteractionEvent event, @NotNull String path) {
		sendError(event, path, null);
	}

	protected void sendError(GenericCommandInteractionEvent event, @NotNull String path, @Nullable String reason) {
		sendError(event, new MessageFactory(bot, event)
			.asEmbed(true)
			.setType(MessageFactory.MessageType.ERROR)
			.setPath(path)
			.setMessage(reason)
			.build()
		);
	}

	protected void sendError(GenericCommandInteractionEvent event, @NotNull MessageEditData data) {
		event.getHook()
			.editOriginal(data)
			.setComponents()
			.queue(msg -> {
				if (!msg.isEphemeral())
					msg.delete().queueAfter(20, TimeUnit.SECONDS, null, ignoreRest);
			});
	}

	protected static final Consumer<Throwable> ignoreRest = ignored -> {
		// Ignore everything
	};

}
