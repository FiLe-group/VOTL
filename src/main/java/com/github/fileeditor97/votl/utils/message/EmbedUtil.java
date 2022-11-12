package com.github.fileeditor97.votl.utils.message;

import java.time.ZonedDateTime;

import javax.annotation.Nonnull;

import com.github.fileeditor97.votl.objects.command.CommandEvent;
import com.github.fileeditor97.votl.objects.command.MessageContextMenuEvent;
import com.github.fileeditor97.votl.objects.command.SlashCommandEvent;
import com.github.fileeditor97.votl.objects.constants.Constants;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import net.dv8tion.jda.api.utils.messages.MessageEditBuilder;
import net.dv8tion.jda.api.utils.messages.MessageEditData;

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
		if (genericEvent instanceof SlashCommandEvent) {
			return getEmbed().setFooter(
				lu.getUserText(genericEvent, "embed.footer"),
				((SlashCommandEvent) genericEvent).getUser().getEffectiveAvatarUrl()
			);
		}
		if (genericEvent instanceof CommandEvent) {
			return getEmbed().setFooter(
				lu.getUserText(genericEvent, "embed.footer"),
				((CommandEvent) genericEvent).getAuthor().getEffectiveAvatarUrl()
			);
		}
		if (genericEvent instanceof MessageContextMenuEvent) {
			return getEmbed().setFooter(
				lu.getUserText(genericEvent, "embed.footer"),
				((MessageContextMenuEvent) genericEvent).getUser().getEffectiveAvatarUrl()
			);
		}
		throw new IllegalArgumentException("Argument passed is not supported event. Received: "+genericEvent.getClass());
	}

	@Nonnull
	private <T> EmbedBuilder getErrorEmbed(T event) {
		return getEmbed(event).setColor(Constants.COLOR_FAILURE);
	}

	@Nonnull
	public <T> EmbedBuilder getPermErrorEmbed(T event, TextChannel channel, Permission perm, boolean self) {
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
	public <T> MessageEditData getError(T event, @Nonnull String path) {
		return getError(event, path, null);
	}

	@Nonnull
	public <T> MessageEditData getError(T event, @Nonnull String path, String reason) {
		EmbedBuilder embedBuilder = getErrorEmbed(event)
			.setDescription(lu.getText(event, path));

		if (reason != null)
			embedBuilder.addField(
				lu.getText(event, "errors.title"),
				reason,
				false
			);

		return MessageEditData.fromEmbeds(embedBuilder.build());
	}

	@Nonnull
	public <T> MessageEditData getPermError(T event, Member member, Permission perm, boolean self) {
		return getPermError(event, member, null, perm, self);
	}

	@Nonnull
	public <T> MessageEditData getPermError(T event, Member member, TextChannel channel, Permission perm, boolean self) {
		User user = member.getUser();
		if (perm.equals(Permission.MESSAGE_SEND)) {
			user.openPrivateChannel()
				.flatMap(ch -> ch.sendMessage(lu.getText(event, "errors.no_send_perm")))
				.queue();
			return MessageEditData.fromContent("PM sended");
		}
		MessageEditBuilder mb = new MessageEditBuilder();

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
