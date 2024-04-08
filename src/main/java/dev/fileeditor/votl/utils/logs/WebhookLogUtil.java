package dev.fileeditor.votl.utils.logs;

import java.util.function.Supplier;

import dev.fileeditor.votl.objects.annotation.Nonnull;
import dev.fileeditor.votl.objects.annotation.Nullable;
import dev.fileeditor.votl.objects.logs.LogType;
import dev.fileeditor.votl.utils.database.DBUtil;
import dev.fileeditor.votl.utils.database.managers.GuildLogsManager.WebhookData;

import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.MessageEmbed;
import net.dv8tion.jda.internal.requests.IncomingWebhookClientImpl;

public class WebhookLogUtil {

	private final DBUtil db;

	public WebhookLogUtil(DBUtil dbUtil) {
		this.db = dbUtil;
	}

	public void sendMessageEmbed(JDA client, long guildId, LogType type, @Nonnull MessageEmbed embed) {
		WebhookData data = db.logs.getLogWebhook(type, guildId);
		if (data != null)
			new IncomingWebhookClientImpl(data.getWebhookId(), data.getToken(), client)
				.sendMessageEmbeds(embed).queue();
	}

	public void sendMessageEmbed(JDA client, long guildId, LogType type, @Nonnull Supplier<MessageEmbed> embedSupplier) {
		WebhookData data = db.logs.getLogWebhook(type, guildId);
		if (data != null)
			new IncomingWebhookClientImpl(data.getWebhookId(), data.getToken(), client)
				.sendMessageEmbeds(embedSupplier.get()).queue();
	}

	public void sendMessageEmbed(@Nullable Guild guild, LogType type, @Nonnull MessageEmbed embed) {
		if (guild == null) return;
		sendMessageEmbed(guild.getJDA(), guild.getIdLong(), type, embed);
	}

	public void sendMessageEmbed(@Nullable Guild guild, LogType type, @Nonnull Supplier<MessageEmbed> embedSupplier) {
		if (guild == null) return;
		sendMessageEmbed(guild.getJDA(), guild.getIdLong(), type, embedSupplier);
	}

	public IncomingWebhookClientImpl getWebhookClient(@Nullable Guild guild, LogType type) {
		if (guild == null) return null;
		WebhookData data = db.logs.getLogWebhook(type, guild.getIdLong());
		if (data == null) return null;
		
		return new IncomingWebhookClientImpl(data.getWebhookId(), data.getToken(), guild.getJDA());
	}
}
