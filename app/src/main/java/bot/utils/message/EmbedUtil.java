package bot.utils.message;

import java.time.ZonedDateTime;
import java.util.Optional;

import javax.annotation.Nonnull;

import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import net.dv8tion.jda.api.interactions.DiscordLocale;
import net.dv8tion.jda.api.interactions.Interaction;
import net.dv8tion.jda.api.utils.messages.MessageEditBuilder;
import net.dv8tion.jda.api.utils.messages.MessageEditData;
import bot.App;
import bot.objects.constants.Constants;

public class EmbedUtil {

	private final App bot;

	public EmbedUtil(App bot) {
		this.bot = bot;
	}

	@Nonnull
	public EmbedBuilder getEmbed() {
		return new EmbedBuilder().setColor(Constants.COLOR_DEFAULT).setTimestamp(ZonedDateTime.now());
	}

	@Nonnull
	public EmbedBuilder getEmbed(Interaction interaction) {
		return getEmbed().setFooter(
			bot.getLocalized(interaction.getUserLocale(), "embed.footer", interaction.getUser().getAsTag(), false),
			interaction.getUser().getEffectiveAvatarUrl()
		);
	}

	@Nonnull
	private EmbedBuilder getErrorEmbed(Interaction interaction) {
		return getEmbed(interaction).setColor(Constants.COLOR_FAILURE);
	}

	@Nonnull
	public EmbedBuilder getPermErrorEmbed(Interaction interaction, TextChannel channel, Permission perm, boolean self) {
		EmbedBuilder embed = getErrorEmbed(interaction);
		String msg;
		if (self) {
			if (channel == null) {
				msg = bot.getLocalized(interaction.getUserLocale(), "errors.missing_perms.self")
					.replace("{permissions}", perm.getName());
			} else {
				msg = bot.getLocalized(interaction.getUserLocale(), "errors.missing_perms.self_channel")
					.replace("{permissions}", perm.getName())
					.replace("{channel}", channel.getAsMention());
			}
		} else {
			if (channel == null) {
				msg = bot.getLocalized(interaction.getUserLocale(), "errors.missing_perms.other")
					.replace("{permissions}", perm.getName());
			} else {
				msg = bot.getLocalized(interaction.getUserLocale(), "errors.missing_perms.other_channel")
					.replace("{permissions}", perm.getName())
					.replace("{channel}", channel.getAsMention());
			}
		}

		return embed.setDescription(msg);
	}

	@Nonnull
	public MessageEditData getError(Interaction interaction, String path) {
		return getError(interaction, path, null);
	}

	@Nonnull
	public MessageEditData getError(Interaction interaction, String path, String reason) {
		DiscordLocale userLocale = interaction.getUserLocale();

		EmbedBuilder embed = getErrorEmbed(interaction);
		embed.setDescription(
			Optional.ofNullable(interaction.getMember()).map(m -> bot.getLocalized(userLocale, path, m.getEffectiveName())).orElse(bot.getLocalized(userLocale, path))
		);

		if (reason != null)
			embed.addField(
				bot.getLocalized(userLocale, "errors.title"),
				reason,
				false
			);

		return MessageEditData.fromEmbeds(embed.build());
	}

	@Nonnull
	public MessageEditData getPermError(Interaction interaction, Permission perm, boolean self) {
		return getPermError(interaction, null, perm, self);
	}

	

	@Nonnull
	public MessageEditData getPermError(Interaction interaction, TextChannel channel, Permission perm, boolean self) {
		DiscordLocale userLocale = interaction.getUserLocale();

		if (perm.equals(Permission.MESSAGE_SEND)) {
			interaction.getUser().openPrivateChannel()
				.flatMap(ch -> ch.sendMessage(bot.getLocalized(userLocale, "errors.no_send_perm")))
				.queue();
			return MessageEditData.fromContent("PM sended");
		}
		MessageEditBuilder mb = new MessageEditBuilder();

		if (perm.equals(Permission.MESSAGE_EMBED_LINKS)) {
			if (channel == null) {
				mb.setContent(
					bot.getLocalized(userLocale, "errors.missing_perms.self")
						.replace("{permission}", perm.getName())
				);
			} else {
				mb.setContent(
					bot.getLocalized(userLocale, "errors.missing_perms.self_channel")
						.replace("{permission}", perm.getName())
						.replace("{channel}", channel.getAsMention())
				);
			}
		} else {
			mb.setEmbeds(getPermErrorEmbed(interaction, channel, perm, self).build());
		}
		return mb.build();
	}
	
}
