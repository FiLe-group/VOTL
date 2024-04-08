package dev.fileeditor.votl.utils.exception;

import dev.fileeditor.votl.objects.annotation.Nonnull;

import net.dv8tion.jda.api.entities.MessageEmbed;
import net.dv8tion.jda.api.utils.messages.MessageCreateData;
import net.dv8tion.jda.api.utils.messages.MessageEditData;

public class CheckException extends Exception {
	private final MessageCreateData data;
	
	public CheckException(@Nonnull MessageEmbed embed) {
		super();
		this.data = MessageCreateData.fromEmbeds(embed);
	}

	public CheckException(@Nonnull MessageCreateData data) {
		super();
		this.data = data;
	}

	@Nonnull
	public MessageCreateData getCreateData() {
		return data;
	}

	@Nonnull
	public MessageEditData getEditData() {
		return MessageEditData.fromCreateData(data);
	}
}
