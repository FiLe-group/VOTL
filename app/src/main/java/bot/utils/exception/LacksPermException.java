package bot.utils.exception;

import javax.annotation.Nonnull;

import net.dv8tion.jda.api.utils.messages.MessageCreateData;
import net.dv8tion.jda.api.utils.messages.MessageEditData;

public class LacksPermException extends Exception {

	@Nonnull
	private MessageEditData permError;

	public LacksPermException (@Nonnull MessageEditData permError) {
		super();
		this.permError = permError;
	}

	@Nonnull
	public MessageCreateData getCreateData() {
		return MessageCreateData.fromEditData(permError);
	}

	@Nonnull
	public MessageEditData getEditData() {
		return permError;
	}

}