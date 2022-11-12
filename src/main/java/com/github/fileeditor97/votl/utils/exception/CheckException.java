package com.github.fileeditor97.votl.utils.exception;

import javax.annotation.Nonnull;

import net.dv8tion.jda.api.utils.messages.MessageCreateData;
import net.dv8tion.jda.api.utils.messages.MessageEditData;

public class CheckException extends Exception {

	@Nonnull
	private MessageEditData msg;
	
	public CheckException(@Nonnull MessageEditData msg) {
		super();
		this.msg = msg;
	}

	@Nonnull
	public MessageEditData getEditData() {
		return msg;
	}

	@Nonnull
	public MessageCreateData getCreateData() {
		return MessageCreateData.fromEditData(msg);
	}
}
