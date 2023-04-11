package votl.commands.webhook;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

import votl.App;
import votl.commands.CommandBase;
import votl.objects.CmdAccessLevel;
import votl.objects.CmdModule;
import votl.objects.command.SlashCommand;
import votl.objects.command.SlashCommandEvent;
import votl.objects.constants.CmdCategory;

import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Webhook;
import net.dv8tion.jda.api.entities.WebhookType;
import net.dv8tion.jda.api.entities.channel.ChannelType;
import net.dv8tion.jda.api.entities.channel.middleman.GuildChannel;
import net.dv8tion.jda.api.exceptions.PermissionException;
import net.dv8tion.jda.api.interactions.DiscordLocale;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import net.dv8tion.jda.api.interactions.commands.build.SubcommandGroupData;

public class WebhookCmd extends CommandBase {

	public WebhookCmd(App bot) {
		super(bot);
		this.name = "webhook";
		this.path = "bot.webhook";
		this.children = new SlashCommand[]{new ShowList(bot), new Create(bot), new Select(bot),
			new Remove(bot), new Move(bot)};
		this.userPermissions = new Permission[]{Permission.MANAGE_WEBHOOKS};
		this.botPermissions = new Permission[]{Permission.MANAGE_WEBHOOKS};
		this.category = CmdCategory.WEBHOOK;
		this.module = CmdModule.WEBHOOK;
		this.accessLevel = CmdAccessLevel.ADMIN;
		this.mustSetup = true;
	}

	@Override
	protected void execute(SlashCommandEvent event)	{

	}

	private class ShowList extends CommandBase {

		public ShowList(App bot) {
			super(bot);
			this.name = "list";
			this.path = "bot.webhook.list";
			this.options = Collections.singletonList(
				new OptionData(OptionType.BOOLEAN, "all", lu.getText(path+".option_all"))
			);
		}

		@Override
		protected void execute(SlashCommandEvent event) {
			event.deferReply(true).queue();

			Guild guild = Objects.requireNonNull(event.getGuild());
			String guildId = guild.getId();
			DiscordLocale userLocale = event.getUserLocale();

			Boolean listAll = event.optBoolean("all", false);

			EmbedBuilder embedBuilder = bot.getEmbedUtil().getEmbed(event)
				.setTitle(lu.getLocalized(userLocale, "bot.webhook.list.embed.title"));
			
			// Retrieves every webhook in server
			guild.retrieveWebhooks().queue(webhooks -> {
				// Remove FOLLOWER type webhooks
				webhooks = webhooks.stream().filter(wh -> wh.getType().equals(WebhookType.INCOMING)).collect(Collectors.toList());

				// If there is any webhook and only saved in DB are to be shown
				if (!listAll) {
					// Keeps only saved in DB type Webhook objects
					List<String> regWebhookIDs = bot.getDBUtil().webhook.getIds(guildId);
						
					webhooks = webhooks.stream().filter(wh -> regWebhookIDs.contains(wh.getId())).collect(Collectors.toList());
				}

				if (webhooks.isEmpty()) {
					embedBuilder.setDescription(
						lu.getLocalized(userLocale, (listAll ? "bot.webhook.list.embed.none_found" : "bot.webhook.list.embed.none_registered"))
					);
				} else {
					String title = lu.getLocalized(userLocale, "bot.webhook.list.embed.value");
					StringBuilder text = new StringBuilder();
					for (Webhook wh : webhooks) {
						if (text.length() > 790) { // max characters for field value = 1024, and max for each line = ~226, so at least 4.5 lines fits in one field
							embedBuilder.addField(title, text.toString(), false);
							title = "\u200b";
							text.setLength(0);
						}
						text.append(String.format("%s | `%s` | %s\n", wh.getName(), wh.getId(), wh.getChannel().getAsMention()));
					}

					embedBuilder.addField(title, text.toString(), false);
				}

				editHookEmbed(event, embedBuilder.build());
			});
		}

	}

	private class Create extends CommandBase {

		public Create(App bot) {
			super(bot);
			this.name = "create";
			this.path = "bot.webhook.add.create";
			List<OptionData> options = new ArrayList<OptionData>();
			options.add(new OptionData(OptionType.STRING, "name", lu.getText(path+".option_name"), true));
			options.add(new OptionData(OptionType.CHANNEL, "channel", lu.getText(path+".option_channel")));
			this.options = options;
			this.subcommandGroup = new SubcommandGroupData("add", lu.getText("bot.webhook.add.help"));
		}

		@Override
		protected void execute(SlashCommandEvent event) {
			Guild guild = Objects.requireNonNull(event.getGuild());

			String setName = event.optString("name", "Default name").trim();
			GuildChannel channel = event.optGuildChannel("channel", event.getGuildChannel());

			if (setName.isEmpty() || setName.length() > 100) {
				createError(event, "bot.webhook.add.create.invalid_range");
				return;
			}

			try {
				// DYK, guildChannel doesn't have WebhookContainer! no shit
				guild.getTextChannelById(channel.getId()).createWebhook(setName).reason("By "+event.getUser().getAsTag()).queue(
					webhook -> {
						bot.getDBUtil().webhook.add(webhook.getId(), webhook.getGuild().getId(), webhook.getToken());
						createReplyEmbed(event,
							bot.getEmbedUtil().getEmbed(event).setDescription(
								lu.getText(event, "bot.webhook.add.create.done").replace("{webhook_name}", webhook.getName())
							).build()
						);
					}
				);
			} catch (PermissionException ex) {
				createPermError(event, event.getMember(), ex.getPermission(), true);
				ex.printStackTrace();
			}
		}

	}

	private class Select extends CommandBase {

		public Select(App bot) {
			super(bot);
			this.name = "select";
			this.path = "bot.webhook.add.select";
			this.options = Collections.singletonList(
				new OptionData(OptionType.STRING, "id", lu.getText(path+".option_id"), true)
			);
			this.subcommandGroup = new SubcommandGroupData("add", lu.getText("bot.webhook.add.help"));
		}

		@Override
		protected void execute(SlashCommandEvent event) {
			String webhookId = event.optString("id", "0").trim();

			try {
				event.getJDA().retrieveWebhookById(Objects.requireNonNull(webhookId)).queue(
					webhook -> {
						if (bot.getDBUtil().webhook.exists(webhookId)) {
							createError(event, "bot.webhook.add.select.error_registered");
						} else {
							bot.getDBUtil().webhook.add(webhook.getId(), webhook.getGuild().getId(), webhook.getToken());
							createReplyEmbed(event,
								bot.getEmbedUtil().getEmbed(event).setDescription(
									lu.getText(event, "bot.webhook.add.select.done").replace("{webhook_name}", webhook.getName())
								).build()
							);
						}
					}, failure -> {
						createError(event, "bot.webhook.add.select.error_not_found", failure.getMessage());
					}
				);
			} catch (IllegalArgumentException ex) {
				createError(event, "bot.webhook.remove.error_not_found", ex.getMessage());
			}
		}

	}

	private class Remove extends CommandBase {

		public Remove(App bot) {
			super(bot);
			this.name = "remove";
			this.path = "bot.webhook.remove";
			List<OptionData> options = new ArrayList<OptionData>();
			options.add(new OptionData(OptionType.STRING, "id", lu.getText(path+".option_id"), true));
			options.add(new OptionData(OptionType.BOOLEAN, "delete", lu.getText(path+".option_delete")));
			this.options = options;
		}

		@Override
		protected void execute(SlashCommandEvent event) {
			Guild guild = Objects.requireNonNull(event.getGuild());

			String webhookId = event.optString("id", "0").trim();
			Boolean delete = event.optBoolean("delete", false); 

			try {
				event.getJDA().retrieveWebhookById(webhookId).queue(
					webhook -> {
						if (!bot.getDBUtil().webhook.exists(webhookId)) {
							createError(event, "bot.webhook.remove.error_not_registered");
						} else {
							if (webhook.getGuild().equals(guild)) {
								if (delete) {
									webhook.delete(webhook.getToken()).reason("By "+event.getUser().getAsTag()).queue();
								}
								bot.getDBUtil().webhook.remove(webhookId);
								createReplyEmbed(event,
									bot.getEmbedUtil().getEmbed(event).setDescription(
										lu.getText(event, "bot.webhook.remove.done").replace("{webhook_name}", webhook.getName())
									).build()
								);
							} else {
								createError(event, "bot.webhook.remove.error_not_guild", 
									String.format("Selected webhook guild: %s", webhook.getGuild().getName()));
							}
						}
					},
					failure -> {
						createError(event, "bot.webhook.remove.error_not_found", failure.getMessage());
					}
				);
			} catch (IllegalArgumentException ex) {
				createError(event, "bot.webhook.remove.error_not_found", ex.getMessage());
			}
		}

	}

	private class Move extends CommandBase {

		public Move(App bot) {
			super(bot);
			this.name = "move";
			this.path = "bot.webhook.move";
			List<OptionData> options = new ArrayList<OptionData>();
			options.add(new OptionData(OptionType.STRING, "id", lu.getText(path+".option_id"), true));
			options.add(new OptionData(OptionType.CHANNEL, "channel", lu.getText(path+".option_channel"), true));
			this.options = options;
		}

		@Override
		protected void execute(SlashCommandEvent event) {
			Guild guild = Objects.requireNonNull(event.getGuild());

			String webhookId = event.optString("id", "0").trim();
			GuildChannel channel = event.optGuildChannel("channel");

			if (!channel.getType().equals(ChannelType.TEXT)) {
				createError(event, "bot.webhook.move.error_channel", "Selected channel is not Text Channel");
				return;
			}

			event.getJDA().retrieveWebhookById(webhookId).queue(
				webhook -> {
					if (bot.getDBUtil().webhook.exists(webhookId)) {
						webhook.getManager().setChannel(guild.getTextChannelById(channel.getId())).reason("By "+event.getUser().getAsTag()).queue(
							wm -> {
								createReplyEmbed(event,
									bot.getEmbedUtil().getEmbed(event).setDescription(
										lu.getText(event, "bot.webhook.move.done")
											.replace("{webhook_name}", webhook.getName())
											.replace("{channel}", channel.getName())
									).build()
								);
							},
							failure -> {
								createError(event, "errors.unknown", failure.getMessage());
							}
						);
					} else {
						createError(event, "bot.webhook.move.error_not_registered");
					}
				}, failure -> {
					createError(event, "bot.webhook.move.error_not_found", failure.getMessage());
				}
			);
		}

	}
}
