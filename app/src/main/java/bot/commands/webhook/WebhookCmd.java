package bot.commands.webhook;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

import javax.annotation.Nonnull;

import bot.App;
import bot.objects.CmdAccessLevel;
import bot.objects.CmdModule;
import bot.objects.command.SlashCommand;
import bot.objects.command.SlashCommandEvent;
import bot.objects.constants.CmdCategory;
import bot.utils.message.LocaleUtil;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.entities.channel.ChannelType;
import net.dv8tion.jda.api.entities.channel.middleman.GuildChannel;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Webhook;
import net.dv8tion.jda.api.entities.WebhookType;
import net.dv8tion.jda.api.exceptions.PermissionException;
import net.dv8tion.jda.api.interactions.DiscordLocale;
import net.dv8tion.jda.api.interactions.InteractionHook;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import net.dv8tion.jda.api.interactions.commands.build.SubcommandGroupData;

@SuppressWarnings("null")
public class WebhookCmd extends SlashCommand {

	public WebhookCmd(App bot) {
		this.name = "webhook";
		this.helpPath = "bot.webhook.help";
		this.children = new SlashCommand[]{new ShowList(bot.getLocaleUtil()), new Create(bot.getLocaleUtil()), new Select(bot.getLocaleUtil()),
			new Remove(bot.getLocaleUtil()), new Move(bot.getLocaleUtil())};
		this.userPermissions = new Permission[]{Permission.MANAGE_WEBHOOKS};
		this.botPermissions = new Permission[]{Permission.MANAGE_WEBHOOKS};
		this.bot = bot;
		this.category = CmdCategory.WEBHOOK;
		this.module = CmdModule.WEBHOOK;
		this.accessLevel = CmdAccessLevel.ADMIN;
		this.mustSetup = true;
	}

	@Override
	protected void execute(SlashCommandEvent event)	{

	}

	private class ShowList extends SlashCommand {

		public ShowList(LocaleUtil lu) {
			this.name = "list";
			this.helpPath = "bot.webhook.list.help";
			this.options = Collections.singletonList(
				new OptionData(OptionType.BOOLEAN, "all", lu.getText("bot.webhook.list.option_all"))
			);
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

			Guild guild = Objects.requireNonNull(event.getGuild());
			String guildId = guild.getId();
			DiscordLocale userLocale = event.getUserLocale();

			EmbedBuilder embedBuilder = bot.getEmbedUtil().getEmbed(event)
				.setTitle(lu.getLocalized(userLocale, "bot.webhook.list.embed.title"));
			
			// Retrieves every webhook in server
			guild.retrieveWebhooks().queue(webhooks -> {
				// Remove FOLLOWER type webhooks
				webhooks = webhooks.stream().filter(wh -> wh.getType().equals(WebhookType.INCOMING)).collect(Collectors.toList());

				// If there is any webhook and only saved in DB are to be shown
				if (!listAll) {
					// Keeps only saved in DB type Webhook objects
					List<String> regWebhookIDs = bot.getDBUtil().webhookGetIds(guildId);
						
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

				hook.editOriginalEmbeds(embedBuilder.build()).queue();
			});

		}

	}

	private class Create extends SlashCommand {

		public Create(LocaleUtil lu) {
			this.name = "create";
			this.helpPath = "bot.webhook.add.create.help";
			List<OptionData> options = new ArrayList<OptionData>();
			options.add(new OptionData(OptionType.STRING, "name", lu.getText("bot.webhook.add.create.option_name"), true));
			options.add(new OptionData(OptionType.CHANNEL, "channel", lu.getText("bot.webhook.add.create.option_channel")));
			this.options = options;
			this.subcommandGroup = new SubcommandGroupData("add", lu.getText("bot.webhook.add.help"));
		}

		@Override
		protected void execute(SlashCommandEvent event) {
			event.deferReply(true).queue(
				hook -> {
					String name = event.getOption("name", "Dafault name", OptionMapping::getAsString).trim();
					GuildChannel channel = event.getOption("channel", event.getGuildChannel(), OptionMapping::getAsChannel);

					sendReply(event, hook, name, channel);
				}
			);
		}

		private void sendReply(SlashCommandEvent event, InteractionHook hook, String name, GuildChannel channel) {

			Guild guild = Objects.requireNonNull(event.getGuild());
			DiscordLocale userLocale = event.getUserLocale();

			if (name.isEmpty() || name.length() > 100) {
				hook.editOriginal(bot.getEmbedUtil().getError(event, "bot.webhook.add.create.invalid_range")).queue();
				return;
			}

			try {
				// DYK, guildChannel doesn't have WebhookContainer! no shit
				guild.getTextChannelById(channel.getId()).createWebhook(name).queue(
					webhook -> {
						bot.getDBUtil().webhookAdd(webhook.getId(), webhook.getGuild().getId(), webhook.getToken());
						hook.editOriginalEmbeds(
							bot.getEmbedUtil().getEmbed(event).setDescription(
								lu.getLocalized(userLocale, "bot.webhook.add.create.done").replace("{webhook_name}", webhook.getName())
							).build()
						).queue();
					}
				);
			} catch (PermissionException ex) {
				hook.editOriginal(
					bot.getEmbedUtil().getPermError(event, event.getMember(), ex.getPermission(), true)
				).queue();
				ex.printStackTrace();
			}
		}
	}

	private class Select extends SlashCommand {

		public Select(LocaleUtil lu) {
			this.name = "select";
			this.helpPath = "bot.webhook.add.select.help";
			this.options = Collections.singletonList(
				new OptionData(OptionType.STRING, "id", lu.getText("bot.webhook.add.select.option_id"), true)
			);
			this.subcommandGroup = new SubcommandGroupData("add", lu.getText("bot.webhook.add.help"));
		}

		@Override
		protected void execute(SlashCommandEvent event) {
			event.deferReply(true).queue(
				hook -> {
					String webhookId = event.getOption("id", "0", OptionMapping::getAsString).trim();
					
					sendReply(event, hook, webhookId);
				}
			);
		}

		private void sendReply(SlashCommandEvent event, InteractionHook hook, @Nonnull String webhookId) {

			DiscordLocale userLocale = event.getUserLocale();

			try {
				event.getJDA().retrieveWebhookById(Objects.requireNonNull(webhookId)).queue(
					webhook -> {
						if (bot.getDBUtil().webhookExists(webhookId)) {
							hook.editOriginal(
								bot.getEmbedUtil().getError(event, "bot.webhook.add.select.error_registered")
							).queue();
						} else {
							bot.getDBUtil().webhookAdd(webhook.getId(), webhook.getGuild().getId(), webhook.getToken());
							hook.editOriginalEmbeds(
								bot.getEmbedUtil().getEmbed(event).setDescription(
									lu.getLocalized(userLocale, "bot.webhook.add.select.done").replace("{webhook_name}", webhook.getName())
								).build()
							).queue();
						}
					}, failure -> {
						hook.editOriginal(
							bot.getEmbedUtil().getError(event, "bot.webhook.add.select.error_not_found", failure.getMessage())
						).queue();
					}
				);
			} catch (IllegalArgumentException ex) {
				hook.editOriginal(
					bot.getEmbedUtil().getError(event, "bot.webhook.remove.error_not_found", ex.getMessage())
				).queue();
			}
		}
	}

	private class Remove extends SlashCommand {

		public Remove(LocaleUtil lu) {
			this.name = "remove";
			this.helpPath = "bot.webhook.remove.help";
			List<OptionData> options = new ArrayList<OptionData>();
			options.add(new OptionData(OptionType.STRING, "id", lu.getText("bot.webhook.remove.option_id"), true));
			options.add(new OptionData(OptionType.BOOLEAN, "delete", lu.getText("bot.webhook.remove.option_delete")));
			this.options = options;
		}

		@Override
		protected void execute(SlashCommandEvent event) {
			event.deferReply(true).queue(
				hook -> {
					String webhookId = event.getOption("id", "0", OptionMapping::getAsString).trim();
					Boolean delete = event.getOption("delete", false, OptionMapping::getAsBoolean);

					sendReply(event, hook, webhookId, delete);
				}
			);
		}

		private void sendReply(SlashCommandEvent event, InteractionHook hook, @Nonnull String webhookId, Boolean delete) {

			Guild guild = Objects.requireNonNull(event.getGuild());
			DiscordLocale userLocale = event.getUserLocale();

			try {
				event.getJDA().retrieveWebhookById(webhookId).queue(
					webhook -> {
						if (!bot.getDBUtil().webhookExists(webhookId)) {
							hook.editOriginal(
								bot.getEmbedUtil().getError(event, "bot.webhook.remove.error_not_registered")
							).queue();
						} else {
							if (webhook.getGuild().equals(guild)) {
								if (delete) {
									webhook.delete(webhook.getToken()).queue();
								}
								bot.getDBUtil().webhookRemove(webhookId);
								hook.editOriginalEmbeds(
									bot.getEmbedUtil().getEmbed(event).setDescription(
										lu.getLocalized(userLocale, "bot.webhook.remove.done").replace("{webhook_name}", webhook.getName())
									).build()
								).queue();
							} else {
								hook.editOriginal(
									bot.getEmbedUtil().getError(event, "bot.webhook.remove.error_not_guild", String.format("Selected webhook guild: %s", webhook.getGuild().getName()))
								).queue();
							}
						}
					},
					failure -> {
						hook.editOriginal(
							bot.getEmbedUtil().getError(event, "bot.webhook.remove.error_not_found", failure.getMessage())
						).queue();
					}
				);
			} catch (IllegalArgumentException ex) {
				hook.editOriginal(
					bot.getEmbedUtil().getError(event, "bot.webhook.remove.error_not_found", ex.getMessage())
				).queue();
			}
		}

	}

	private class Move extends SlashCommand {

		public Move(LocaleUtil lu) {
			this.name = "move";
			this.helpPath = "bot.webhook.move.help";
			List<OptionData> options = new ArrayList<OptionData>();
			options.add(new OptionData(OptionType.STRING, "id", lu.getText("bot.webhook.move.option_id"), true));
			options.add(new OptionData(OptionType.CHANNEL, "channel", lu.getText("bot.webhook.move.option_channel"), true));
			this.options = options;
		}

		@Override
		protected void execute(SlashCommandEvent event) {
			event.deferReply(true).queue(
				hook -> {
					String webhookId = event.getOption("id", OptionMapping::getAsString).trim();
					GuildChannel channel = event.getOption("channel", OptionMapping::getAsChannel);

					sendReply(event, hook, webhookId, channel);
				}
			);
		}

		private void sendReply(SlashCommandEvent event, InteractionHook hook, @Nonnull String webhookId, GuildChannel channel) {

			Guild guild = Objects.requireNonNull(event.getGuild());
			DiscordLocale userLocale = event.getUserLocale();

			if (!channel.getType().equals(ChannelType.TEXT)) {
				hook.editOriginal(bot.getEmbedUtil().getError(event, "bot.webhook.move.error_channel", "Selected channel is not Text Channel")).queue();
				return;
			}

			event.getJDA().retrieveWebhookById(webhookId).queue(
				webhook -> {
					if (bot.getDBUtil().webhookExists(webhookId)) {
						webhook.getManager().setChannel(guild.getTextChannelById(channel.getId())).queue(
							wm -> {
								hook.editOriginalEmbeds(
									bot.getEmbedUtil().getEmbed(event).setDescription(
										lu.getLocalized(userLocale, "bot.webhook.move.done")
											.replace("{webhook_name}", webhook.getName())
											.replace("{channel}", channel.getName())
									).build()
								).queue();
							},
							failure -> {
								hook.editOriginal(
									bot.getEmbedUtil().getError(event, "errors.unknown", failure.getMessage())
								).queue();
							}
						);
					} else {
						hook.editOriginal(
							bot.getEmbedUtil().getError(event, "bot.webhook.move.error_not_registered")
						).queue();
					}
				}, failure -> {
					hook.editOriginal(
						bot.getEmbedUtil().getError(event, "bot.webhook.move.error_not_found", failure.getMessage())
					).queue();
				}
			);
		}
	}
}
