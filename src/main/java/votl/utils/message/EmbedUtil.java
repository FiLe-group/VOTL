package votl.utils.message;

import java.time.ZonedDateTime;

import javax.annotation.Nonnull;

import votl.objects.command.CommandEvent;
import votl.objects.constants.Constants;

import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.MessageEmbed;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.entities.channel.middleman.GuildChannel;
import net.dv8tion.jda.api.events.interaction.GenericInteractionCreateEvent;
import net.dv8tion.jda.api.utils.messages.MessageCreateBuilder;
import net.dv8tion.jda.api.utils.messages.MessageCreateData;

public class EmbedUtil {

	private final LocaleUtil lu;

	public EmbedUtil(LocaleUtil localeUtil) {
		this.lu = localeUtil;
	}

	@Nonnull
	public EmbedBuilder getEmbed() {
		return new EmbedBuilder().setColor(Constants.COLOR_DEFAULT).setTimestamp(ZonedDateTime.now());
	}

	@Nonnull
	public <T> EmbedBuilder getEmbed(T genericEvent) {
		if (genericEvent instanceof GenericInteractionCreateEvent) {
			return getEmbed().setFooter(
				lu.getUserText(genericEvent, "embed.footer"),
				((GenericInteractionCreateEvent) genericEvent).getUser().getEffectiveAvatarUrl()
			);
		}
		if (genericEvent instanceof CommandEvent) {
			return getEmbed().setFooter(
				lu.getUserText(genericEvent, "embed.footer"),
				((CommandEvent) genericEvent).getAuthor().getEffectiveAvatarUrl()
			);
		}
		throw new IllegalArgumentException("Passed argument is not supported Event. Received: "+genericEvent.getClass());
	}

	@Nonnull
	private <T> EmbedBuilder getErrorEmbed(T event) {
		return getEmbed(event).setColor(Constants.COLOR_FAILURE).setTitle(lu.getText(event, "errors.title"));
	}

	@Nonnull
	private <T> EmbedBuilder getPermErrorEmbed(T event, GuildChannel channel, Permission perm, boolean self) {
		EmbedBuilder embed = getErrorEmbed(event);
		String msg;
		if (self) {
			if (channel == null) {
				msg = lu.getText(event, "errors.missing_perms.self")
					.replace("{permissions}", perm.getName());
			} else {
				msg = lu.getText(event, "errors.missing_perms.self_channel")
					.replace("{permissions}", perm.getName())
					.replace("{channel}", channel.getAsMention());
			}
		} else {
			if (channel == null) {
				msg = lu.getText(event, "errors.missing_perms.other")
					.replace("{permissions}", perm.getName());
			} else {
				msg = lu.getText(event, "errors.missing_perms.other_channel")
					.replace("{permissions}", perm.getName())
					.replace("{channel}", channel.getAsMention());
			}
		}

		return embed.setDescription(msg);
	}

	@Nonnull
	public <T> MessageEmbed getError(T event, @Nonnull String path) {
		return getError(event, path, null);
	}

	@Nonnull
	public <T> MessageEmbed getError(T event, @Nonnull String path, String reason) {
		EmbedBuilder embedBuilder = getErrorEmbed(event)
			.setDescription(lu.getText(event, path));

		if (reason != null)
			embedBuilder.addField(
				lu.getText(event, "errors.additional"),
				reason,
				false
			);

		return embedBuilder.build();
	}

	@Nonnull
	public <T> MessageCreateData createPermError(T event, Member member, Permission perm, boolean self) {
		return createPermError(event, member, null, perm, self);
	}

	@Nonnull
	public <T> MessageCreateData createPermError(T event, Member member, GuildChannel channel, Permission perm, boolean self) {
		User user = member.getUser();
		if (perm.equals(Permission.MESSAGE_SEND)) {
			user.openPrivateChannel()
				.flatMap(ch -> ch.sendMessage(lu.getText(event, "errors.no_send_perm")))
				.queue();
			return MessageCreateData.fromContent("No MESSAGE_SEND perm"); //useles?
		}
		MessageCreateBuilder mb = new MessageCreateBuilder();

		if (perm.equals(Permission.MESSAGE_EMBED_LINKS)) {
			if (channel == null) {
				mb.setContent(
					lu.getText(event, "errors.missing_perms.self")
						.replace("{permission}", perm.getName())
				);
			} else {
				mb.setContent(
					lu.getText(event, "errors.missing_perms.self_channel")
						.replace("{permission}", perm.getName())
						.replace("{channel}", channel.getAsMention())
				);
			}
		} else {
			mb.setEmbeds(getPermErrorEmbed(event, channel, perm, self).build());
		}
		return mb.build();
	}

}
