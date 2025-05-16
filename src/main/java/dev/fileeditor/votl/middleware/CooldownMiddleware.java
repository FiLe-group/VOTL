package dev.fileeditor.votl.middleware;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import dev.fileeditor.votl.App;
import dev.fileeditor.votl.base.command.CooldownScope;
import dev.fileeditor.votl.base.command.SlashCommandEvent;
import dev.fileeditor.votl.contracts.middleware.Middleware;
import net.dv8tion.jda.api.events.interaction.command.GenericCommandInteractionEvent;
import net.dv8tion.jda.api.utils.TimeFormat;
import org.jetbrains.annotations.NotNull;

import java.time.OffsetDateTime;
import java.time.temporal.ChronoUnit;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

public class CooldownMiddleware extends Middleware {

	public static final Cache<String, OffsetDateTime> cooldowns = Caffeine.newBuilder()
		.expireAfterWrite(10, TimeUnit.MINUTES)
		.build();

	public CooldownMiddleware(App bot) {
		super(bot);
	}

	@Override
	public boolean handle(@NotNull GenericCommandInteractionEvent event, @NotNull MiddlewareStack stack, String... args) {
		if (stack.getInteraction().getCooldown() >= 0 && !bot.getCheckUtil().isOperatorPlus(event.getGuild(), event.getUser())) {
			var scope = stack.getInteraction().getCooldownScope();
			String key = getCooldownKey(event, scope);
			int remaining = getRemainingCooldown(key);
			if (remaining > 0) {
				runErrorCheck(event, () -> {
					sendErrorMsg(event, getCooldownError(event, scope, remaining));

					return false;
				});
			} else {
				applyCooldown(key, stack.getInteraction().getCooldown());
			}
		}

		return stack.next();
	}

	/**
	 * Gets an error message for this Command under the provided
	 * {@link SlashCommandEvent SlashCommandEvent}.
	 *
	 * @param event The CommandEvent to generate the error message for.
	 * @param scope CooldownScope
	 * @param remaining The remaining number of seconds a command is on cooldown for.
	 *
	 * @return A String error message for this command if {@code remaining > 0},
	 *         else {@code null}.
	 */
	private String getCooldownError(GenericCommandInteractionEvent event, CooldownScope scope, int remaining) {
		if (remaining <= 0)
			return null;

		String descriptor;
		if (scope.equals(CooldownScope.USER_GUILD) && event.getGuild()==null)
			descriptor = bot.getLocaleUtil().getText(event, CooldownScope.USER_CHANNEL.getErrorPath());
		else if (scope.equals(CooldownScope.GUILD) && event.getGuild()==null)
			descriptor = bot.getLocaleUtil().getText(event, CooldownScope.CHANNEL.getErrorPath());
		else if (!scope.equals(CooldownScope.USER))
			descriptor = bot.getLocaleUtil().getText(event, scope.getErrorPath());
		else
			descriptor = null;

		return bot.getLocaleUtil().getText(event, "errors.cooldown.cooldown_command")
			.formatted(descriptor == null ? "" : descriptor, TimeFormat.RELATIVE.after(remaining));
	}

	/**
	 * Gets the proper cooldown key for this Command under the provided
	 * {@link SlashCommandEvent SlashCommandEvent}.
	 *
	 * @param event The CommandEvent to generate the cooldown for.
	 * @param scope CooldownScope
	 *
	 * @return A String key to use when applying a cooldown.
	 */
	public String getCooldownKey(GenericCommandInteractionEvent event, CooldownScope scope) {
		String name = event.getFullCommandName();
		return switch (scope) {
			case USER -> scope.genKey(name, event.getUser().getIdLong());
			case USER_GUILD -> Optional.ofNullable(event.getGuild())
				.map(g -> scope.genKey(name, event.getUser().getIdLong(), g.getIdLong()))
				.orElse(CooldownScope.USER_CHANNEL.genKey(name, event.getUser().getIdLong(), event.getChannel().getIdLong()));
			case USER_CHANNEL ->
				scope.genKey(name, event.getUser().getIdLong(), event.getChannel().getIdLong());
			case GUILD -> Optional.ofNullable(event.getGuild())
				.map(g -> scope.genKey(name, g.getIdLong()))
				.orElse(CooldownScope.CHANNEL.genKey(name, event.getChannel().getIdLong()));
			case CHANNEL -> scope.genKey(name, event.getChannel().getIdLong());
			case SHARD -> scope.genKey(name, event.getJDA().getShardInfo().getShardId());
			case USER_SHARD -> scope.genKey(name, event.getUser().getIdLong(), event.getJDA().getShardInfo().getShardId());
			case GLOBAL -> scope.genKey(name, 0);
		};
	}

	/**
	 * Gets the remaining number of seconds on the specified cooldown.
	 *
	 * @param  name
	 *         The cooldown name
	 *
	 * @return The number of seconds remaining
	 */
	public int getRemainingCooldown(String name) {
		OffsetDateTime remaining = cooldowns.getIfPresent(name);
		if (remaining != null) {
			int time = (int) Math.ceil(OffsetDateTime.now().until(remaining, ChronoUnit.MILLIS) / 1000D);
			if (time<=0) {
				cooldowns.invalidate(name);
				return 0;
			}
			return time;
		}
		return 0;
	}

	/**
	 * Applies the specified cooldown with the provided name.
	 *
	 * @param  name
	 *         The cooldown name
	 * @param  seconds
	 *         The time to make the cooldown last
	 */
	public void applyCooldown(String name, int seconds) {
		cooldowns.put(name, OffsetDateTime.now().plusSeconds(seconds));
	}

	/**
	 * Cleans up expired cooldowns to reduce memory.
	 */
	public static void cleanCooldowns() {
		OffsetDateTime now = OffsetDateTime.now();
		cooldowns.asMap().forEach((key, value) -> {
			if (value.isBefore(now)) {
				cooldowns.invalidate(key);
			}
		});
	}

}
