package bot.commands.webhook;

import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

import com.jagrosh.jdautilities.command.SlashCommand;
import com.jagrosh.jdautilities.command.SlashCommandEvent;

import bot.App;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.entities.Webhook;
import net.dv8tion.jda.api.entities.WebhookType;
import net.dv8tion.jda.api.interactions.InteractionHook;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import net.dv8tion.jda.api.utils.messages.MessageCreateData;
import net.dv8tion.jda.api.utils.messages.MessageEditData;

public class WebhookCmd extends SlashCommand {

	protected static Permission[] userPerms, botPerms;

	public WebhookCmd(App bot) {
		this.name = "webhook";
		this.category = new Category("webhook");
		WebhookCmd.botPerms = new Permission[]{Permission.MANAGE_WEBHOOKS};
		WebhookCmd.userPerms = new Permission[]{Permission.MANAGE_SERVER};
		this.children = new SlashCommand[]{new ShowList(bot)};
	}

	@Override
	protected void execute(SlashCommandEvent event)	{

	} //    /webhook list [all:true/false]

	private static class ShowList extends SlashCommand {

		private final App bot;

		public ShowList(App bot) {
			this.name = "list";
			this.help = bot.getMsg("0", "bot.webhook.list.description");
			this.options = Collections.singletonList(
				new OptionData(OptionType.BOOLEAN, "all", bot.getMsg("0", "bot.webhook.list.option_description"))
			);
			this.bot = bot;
		}

		@Override
		protected void execute(SlashCommandEvent event) {

			event.deferReply(true).queue(
				hook -> {
					Boolean listAll = event.getOption("all", false, OptionMapping::getAsBoolean);

					sendReply(event, hook, listAll);
				}
			);
		}

		private void sendReply(SlashCommandEvent event, InteractionHook hook, boolean listAll) {
			MessageCreateData permission = bot.getCheckUtil().lacksPermissions(event.getTextChannel(), event.getMember(), true, botPerms);
			if (permission != null) {
				hook.editOriginal(MessageEditData.fromCreateData(permission)).queue();
				return;
			}
			
			permission = bot.getCheckUtil().lacksPermissions(event.getTextChannel(), event.getMember(), userPerms);
			if (permission != null) {
				hook.editOriginal(MessageEditData.fromCreateData(permission)).queue();
				return;
			}

			EmbedBuilder embedBuilder = bot.getEmbedUtil().getEmbed(event.getMember())
				.setTitle(bot.getMsg(event.getGuild().getId(), "bot.webhook.list.embed.title"));
			
			// Retrieves every webhook in server
			event.getGuild().retrieveWebhooks().queue(webhooks -> {
				// Remove FOLLOWER type webhooks
				webhooks = webhooks.stream().filter(wh -> wh.getType().equals(WebhookType.INCOMING)).collect(Collectors.toList());

				// If there is any webhook and only saved in DB are to be shown
				if (!listAll) {
					// Keeps only saved in DB type Webhook objects
					List<String> regWebhookIDs = bot.getDBUtil().webhookGetIDs(event.getGuild().getId());
						
					webhooks = webhooks.stream().filter(wh -> regWebhookIDs.contains(wh.getId())).collect(Collectors.toList());
				}

				if (webhooks.isEmpty()) {
					embedBuilder.setDescription(
						bot.getMsg(event.getGuild().getId(),(listAll ? "bot.webhook.list.embed.none_found" : "bot.webhook.list.embed.none_registered"))
					);
				} else {
					String title = bot.getMsg(event.getGuild().getId(), "bot.webhook.list.embed.value");
					StringBuilder text = new StringBuilder();
					for (Webhook wh : webhooks) {
						if (text.length() > 790) { // max characters for field value = 1024, and max for each line = ~226, so 4.5 lines fits in one field
							embedBuilder.addField(title, text.toString(), false);
							title = "\u200b";
							text.setLength(0);
						}
						text.append(String.format("%s | `%s` | %s\n", wh.getName(), wh.getId(), wh.getChannel().getAsMention()));
					}

					embedBuilder.addField(title, text.toString(), false);
				}

				hook.editOriginalEmbeds(embedBuilder.build()).queue();
			});

		}

	}

}
