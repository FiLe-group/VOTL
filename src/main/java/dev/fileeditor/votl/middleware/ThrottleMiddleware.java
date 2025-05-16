package dev.fileeditor.votl.middleware;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import dev.fileeditor.votl.App;
import dev.fileeditor.votl.contracts.middleware.ThrottleMessage;
import dev.fileeditor.votl.contracts.middleware.Middleware;
import dev.fileeditor.votl.objects.constants.Constants;
import net.dv8tion.jda.api.entities.ISnowflake;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.entities.channel.middleman.MessageChannel;
import net.dv8tion.jda.api.events.interaction.command.GenericCommandInteractionEvent;
import net.dv8tion.jda.api.utils.TimeFormat;
import org.jetbrains.annotations.NotNull;

import java.time.Instant;
import java.util.concurrent.TimeUnit;

import static dev.fileeditor.votl.utils.CastUtil.getOrDefault;

public class ThrottleMiddleware extends Middleware {

	public static final Cache<String, ThrottleEntity> cache = Caffeine.newBuilder()
		.expireAfterWrite(120, TimeUnit.SECONDS)
		.build();

	public ThrottleMiddleware(App bot) {
		super(bot);
	}

	@Override
	public boolean handle(@NotNull GenericCommandInteractionEvent event, @NotNull MiddlewareStack stack, String... args) {
		if (args.length < 3) {
			bot.getAppLogger().warn(
				"{} is parsing invalid amount of arguments to the throttle middleware, 3 arguments are required.", event.getFullCommandName()
			);
			return stack.next();
		}

		ThrottleType type = ThrottleType.fromName(args[0]);

		try {
			int maxAttempts = getOrDefault(args[1], 2);
			int decaySeconds = getOrDefault(args[2], 5);

			if (decaySeconds <= 0) {
				return stack.next();
			}

			ThrottleEntity entity = getEntityFromCache(type.genKey(event), maxAttempts, decaySeconds);
			if (entity.getHits() >= maxAttempts) {
				Instant expires = bot.getBlacklist().getRatelimit().hit(type, event);

				if (expires != null) {
					sendBlacklistMessage(event, type==ThrottleType.USER ? event.getUser() : event.getMessageChannel(), expires);
					return false;
				}

				return cancelCommandThrottleRequest(event, stack, entity);
			}

			boolean response = stack.next();

			if (response) {
				entity.incrementHit();
			}

			return response;
		} catch (NumberFormatException ex) {
			bot.getAppLogger().warn(
				"Invalid integers given to throttle middleware by {}, args: ({}, {})", event.getFullCommandName(), args[1], args[2]
			);
		}
		return false;
	}

	private boolean cancelCommandThrottleRequest(GenericCommandInteractionEvent event, MiddlewareStack stack, ThrottleEntity entity) {
		return runErrorCheck(event, () -> {
			String throttleMessage = bot.getLocaleUtil().getText(event, "errors.throttle.command")
				.formatted(TimeFormat.RELATIVE.after(((entity.getTime() - System.currentTimeMillis())/1000) + 1));

			ThrottleMessage annotation = stack.getInteraction().getClass().getAnnotation(ThrottleMessage.class);
			if (annotation != null && !annotation.message().trim().isEmpty()) {
				if (annotation.overwrite()) {
					throttleMessage = annotation.message();
				} else {
					throttleMessage += annotation.message();
				}
			}

			sendErrorMsg(event, throttleMessage);

			return false;
		});
	}

	private ThrottleEntity getEntityFromCache(@NotNull String key, int maxAttempts, int decaySeconds) {
		return cache.get(key, (v) -> new ThrottleEntity(maxAttempts, decaySeconds));
	}

	private void sendBlacklistMessage(GenericCommandInteractionEvent event, Object o, Instant expiresIn) {
		if (o instanceof User user) {
			sendBlacklistMessage(event, user, expiresIn);
		} else if (o instanceof MessageChannel channel) {
			sendBlacklistMessage(event, channel, expiresIn);
		}
	}

	private void sendBlacklistMessage(GenericCommandInteractionEvent event, User user, Instant expiresIn) {
		user.openPrivateChannel()
			.flatMap(channel ->
				channel.sendMessageEmbeds(
					bot.getEmbedUtil()
						.getEmbed(Constants.COLOR_WARNING)
						.setDescription(
							bot.getLocaleUtil().getText(event, "errors.throttle.pm_user")
								.formatted(TimeFormat.RELATIVE.format(expiresIn))
						)
						.setTimestamp(Instant.now())
						.build()
				)
			)
			.queue();
	}

	private void sendBlacklistMessage(GenericCommandInteractionEvent event, MessageChannel channel, Instant expiresIn) {
		channel.sendMessageEmbeds(
			bot.getEmbedUtil()
				.getEmbed(Constants.COLOR_WARNING)
				.setDescription(
					bot.getLocaleUtil().getText(event, "errors.throttle.pm_guild")
						.formatted(TimeFormat.RELATIVE.format(expiresIn))
				)
				.setTimestamp(Instant.now())
				.build()
		).queue();
	}

	public static class ThrottleEntity {
		private final int maxAttempts;
		private final long time;
		private int hit;

		ThrottleEntity(int maxAttempts, int decaySeconds) {
			this.time = System.currentTimeMillis() + (decaySeconds * 1000L);
			this.maxAttempts = maxAttempts;
			this.hit = 0;
		}

		public int getHits() {
			return hit;
		}

		public void incrementHit() {
			hit++;
		}

		public int getMaxAttempts() {
			return maxAttempts;
		}

		public long getTime() {
			return time;
		}

		public boolean hasExpired() {
			return System.currentTimeMillis() > time;
		}
	}

	public enum ThrottleType {
		USER("U:%d", "User"),
		CHANNEL("C:%d", "Channel"),
		GUILD("G:%d", "Guild");

		private final String format;
		private final String name;

		ThrottleType(String format, String name) {
			this.format = format;
			this.name = name;
		}

		@NotNull
		public static ThrottleType fromName(String name) {
			for (ThrottleType type : ThrottleType.values()) {
				if (type.name().equalsIgnoreCase(name)) {
					return type;
				}
			}
			return USER;
		}

		public String genKey(GenericCommandInteractionEvent event) {
			long id = getSnowflake(event).getIdLong();
			if (this == GUILD && !event.isFromGuild()) {
				return USER.genKey(id);
			}
			return genKey(id);
		}

		public String genKey(long id) {
			return format.formatted(id);
		}

		public ISnowflake getSnowflake(GenericCommandInteractionEvent event) {
			return switch (this) {
				case GUILD -> event.isFromGuild() ? event.getGuild() : event.getUser();
				case CHANNEL -> event.getChannel();
				case USER -> event.getUser();
			};
		}

		public String getName() {
			return name;
		}
	}

}
