package bot.commands.webhook;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;

import javax.annotation.Nonnull;

import com.jagrosh.jdautilities.command.SlashCommand;
import com.jagrosh.jdautilities.command.SlashCommandEvent;

import bot.App;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.entities.channel.middleman.GuildChannel;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.Webhook;
import net.dv8tion.jda.api.entities.WebhookType;
import net.dv8tion.jda.api.exceptions.PermissionException;
import net.dv8tion.jda.api.interactions.InteractionHook;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import net.dv8tion.jda.api.interactions.commands.build.SubcommandGroupData;
import net.dv8tion.jda.api.utils.messages.MessageCreateData;
import net.dv8tion.jda.api.utils.messages.MessageEditData;

@SuppressWarnings("null")
public class WebhookCmd extends SlashCommand {

	protected static Permission[] userPerms, botPerms;

	public WebhookCmd(App bot) {
		this.name = "webhook";
		this.help = bot.getMsg("bot.webhook.help");
		this.category = new Category("webhook");
		WebhookCmd.botPerms = new Permission[]{Permission.MANAGE_WEBHOOKS};
		WebhookCmd.userPerms = new Permission[]{Permission.MANAGE_SERVER};
		this.children = new SlashCommand[]{new ShowList(bot), new Create(bot), new Select(bot), new Remove(bot), new Move(bot)};
	}

	@Override
	protected void execute(SlashCommandEvent event)	{

	}

	private static class ShowList extends SlashCommand {

		private final App bot;

		public ShowList(App bot) {
			this.name = "list";
			this.help = bot.getMsg("bot.webhook.list.help");
			this.options = Collections.singletonList(
				new OptionData(OptionType.BOOLEAN, "all", bot.getMsg("bot.webhook.list.option_all"))
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

			Member member = Objects.requireNonNull(event.getMember());

			MessageCreateData permission = bot.getCheckUtil().lacksPermissions(event.getTextChannel(), member, true, botPerms);
			if (permission != null) {
				hook.editOriginal(MessageEditData.fromCreateData(permission)).queue();
				return;
			}
			
			permission = bot.getCheckUtil().lacksPermissions(event.getTextChannel(), member, userPerms);
			if (permission != null) {
				hook.editOriginal(MessageEditData.fromCreateData(permission)).queue();
				return;
			}

			Guild guild = Objects.requireNonNull(event.getGuild());
			String guildId = guild.getId();

			EmbedBuilder embedBuilder = bot.getEmbedUtil().getEmbed(member)
				.setTitle(bot.getMsg(guildId, "bot.webhook.list.embed.title"));
			
			// Retrieves every webhook in server
			guild.retrieveWebhooks().queue(webhooks -> {
				// Remove FOLLOWER type webhooks
				webhooks = webhooks.stream().filter(wh -> wh.getType().equals(WebhookType.INCOMING)).collect(Collectors.toList());

				// If there is any webhook and only saved in DB are to be shown
				if (!listAll) {
					// Keeps only saved in DB type Webhook objects
					List<String> regWebhookIDs = bot.getDBUtil().webhookGetIDs(guildId);
						
					webhooks = webhooks.stream().filter(wh -> regWebhookIDs.contains(wh.getId())).collect(Collectors.toList());
				}

				if (webhooks.isEmpty()) {
					embedBuilder.setDescription(
						bot.getMsg(guildId, (listAll ? "bot.webhook.list.embed.none_found" : "bot.webhook.list.embed.none_registered"))
					);
				} else {
					String title = bot.getMsg(guildId, "bot.webhook.list.embed.value");
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

	private static class Create extends SlashCommand {

		private final App bot;

		public Create(App bot) {
			this.name = "create";
			this.help = bot.getMsg("bot.webhook.add.create.help");

			List<OptionData> options = new ArrayList<OptionData>();
			options.add(new OptionData(OptionType.STRING, "name", bot.getMsg("bot.webhook.add.create.option_name"), true));
			options.add(new OptionData(OptionType.CHANNEL, "channel", bot.getMsg("bot.webhook.add.create.option_channel")));
			this.options = options;
			this.subcommandGroup = new SubcommandGroupData("add", bot.getMsg("bot.webhook.add.help"));
			this.bot = bot;
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

			Member member = Objects.requireNonNull(event.getMember());

			MessageCreateData permission = bot.getCheckUtil().lacksPermissions(event.getTextChannel(), member, true, botPerms);
			if (permission != null) {
				hook.editOriginal(MessageEditData.fromCreateData(permission)).queue();
				return;
			}
			
			permission = bot.getCheckUtil().lacksPermissions(event.getTextChannel(), member, userPerms);
			if (permission != null) {
				hook.editOriginal(MessageEditData.fromCreateData(permission)).queue();
				return;
			}

			if (name.isEmpty() || name.length() > 100) {
				hook.editOriginal(MessageEditData.fromCreateData(bot.getEmbedUtil().getError(event, "bot.webhook.add.create.invalid_range")));
				return;
			}

			Guild guild = Objects.requireNonNull(event.getGuild());
			String guildId = guild.getId();

			try {
				// DYK, guildChannel doesn't have WebhookContainer! no shit
				guild.getTextChannelById(channel.getId()).createWebhook(name).queue(
					webhook -> {
						bot.getDBUtil().webhookAdd(webhook.getId(), webhook.getGuild().getId(), webhook.getToken());
						hook.editOriginal(MessageEditData.fromEmbeds(
							bot.getEmbedUtil().getEmbed(member).setDescription(
								bot.getMsg(guildId, "bot.webhook.add.create.done").replace("{webhook_name}", webhook.getName())
							).build()
						)).queue();
					}
				);
			} catch (PermissionException ex) {
				hook.editOriginal(MessageEditData.fromCreateData(
					bot.getEmbedUtil().getPermError(event.getTextChannel(), member, ex.getPermission(), true)
				)).queue();
				ex.printStackTrace();
			}
		}
	}

	private static class Select extends SlashCommand {

		private final App bot;

		public Select(App bot) {
			this.name = "select";
			this.help = bot.getMsg("bot.webhook.add.select.help");
			this.options = Collections.singletonList(
				new OptionData(OptionType.STRING, "id", bot.getMsg("bot.webhook.add.select.option_id"), true)
			);
			this.subcommandGroup = new SubcommandGroupData("add", bot.getMsg("bot.webhook.add.help"));
			this.bot = bot;
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

			Member member = Objects.requireNonNull(event.getMember());

			MessageCreateData permission = bot.getCheckUtil().lacksPermissions(event.getTextChannel(), member, true, botPerms);
			if (permission != null) {
				hook.editOriginal(MessageEditData.fromCreateData(permission)).queue();
				return;
			}
			
			permission = bot.getCheckUtil().lacksPermissions(event.getTextChannel(), member, userPerms);
			if (permission != null) {
				hook.editOriginal(MessageEditData.fromCreateData(permission)).queue();
				return;
			}

			String guildId = Optional.ofNullable(event.getGuild()).map(g -> g.getId()).orElse("0");

			event.getJDA().retrieveWebhookById(Objects.requireNonNull(webhookId)).queue(
				webhook -> {
					if (bot.getDBUtil().webhookExists(webhookId)) {
						hook.editOriginal(MessageEditData.fromCreateData(
							bot.getEmbedUtil().getError(event, "bot.webhook.add.select.error_registered")
						)).queue();
					} else {
						bot.getDBUtil().webhookAdd(webhook.getId(), webhook.getGuild().getId(), webhook.getToken());
						hook.editOriginalEmbeds(
							bot.getEmbedUtil().getEmbed(member).setDescription(
								bot.getMsg(guildId, "bot.webhook.add.select.done").replace("{webhook_name}", webhook.getName())
							).build()
						).queue();
					}
				}, failure -> {
					hook.editOriginal(MessageEditData.fromCreateData(
						bot.getEmbedUtil().getError(event, "bot.webhook.add.select.error_not_found", failure.getMessage())
					)).queue();
				}
			);
			
		}
	}

	private static class Remove extends SlashCommand {

		private final App bot;

		public Remove(App bot) {
			this.name = "remove";
			this.help = bot.getMsg("bot.webhook.remove.help");
			List<OptionData> options = new ArrayList<OptionData>();
			options.add(new OptionData(OptionType.STRING, "id", bot.getMsg("bot.webhook.remove.option_id"), true));
			options.add(new OptionData(OptionType.BOOLEAN, "delete", bot.getMsg("bot.webhook.remove.option_delete")));
			this.options = options;
			this.bot = bot;
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

			Member member = Objects.requireNonNull(event.getMember());

			MessageCreateData permission = bot.getCheckUtil().lacksPermissions(event.getTextChannel(), member, true, botPerms);
			if (permission != null) {
				hook.editOriginal(MessageEditData.fromCreateData(permission)).queue();
				return;
			}
			
			permission = bot.getCheckUtil().lacksPermissions(event.getTextChannel(), member, userPerms);
			if (permission != null) {
				hook.editOriginal(MessageEditData.fromCreateData(permission)).queue();
				return;
			}

			Guild guild = Objects.requireNonNull(event.getGuild());
			String guildId = guild.getId();

			event.getJDA().retrieveWebhookById(webhookId).queue(
				webhook -> {
					if (!bot.getDBUtil().webhookExists(webhookId)) {
						hook.editOriginal(MessageEditData.fromCreateData(
							bot.getEmbedUtil().getError(event, "bot.webhook.remove.error_not_registered")
						)).queue();
					} else {
						if (webhook.getGuild().equals(guild)) {
							if (delete) {
								webhook.delete(webhook.getToken()).queue();
							}
							bot.getDBUtil().webhookRemove(webhookId);
							hook.editOriginalEmbeds(
								bot.getEmbedUtil().getEmbed(member).setDescription(
									bot.getMsg(guildId, "bot.webhook.remove.done").replace("{webhook_name}", webhook.getName())
								).build()
							).queue();
						} else {
							hook.editOriginal(MessageEditData.fromCreateData(
								bot.getEmbedUtil().getError(event, "bot.webhook.remove.error_not_guild", String.format("Selected webhook guild: %s", webhook.getGuild().getName()))
							)).queue();
						}
					}
				},
				failure -> {
					hook.editOriginal(MessageEditData.fromCreateData(
						bot.getEmbedUtil().getError(event, "bot.webhook.remove.error_not_found", failure.getMessage())
					)).queue();
				}
			);
		}

	}

	private static class Move extends SlashCommand {

		private final App bot;

		public Move(App bot) {
			this.name = "move";
			this.help = bot.getMsg("bot.webhook.move.help");
			List<OptionData> options = new ArrayList<OptionData>();
			options.add(new OptionData(OptionType.STRING, "id", bot.getMsg("bot.webhook.move.option_id"), true));
			options.add(new OptionData(OptionType.CHANNEL, "channel", bot.getMsg("bot.webhook.move.option_channel"), true));
			this.options = options;
			this.bot = bot;
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

			Member member = Objects.requireNonNull(event.getMember());

			MessageCreateData permission = bot.getCheckUtil().lacksPermissions(event.getTextChannel(), member, true, botPerms);
			if (permission != null) {
				hook.editOriginal(MessageEditData.fromCreateData(permission)).queue();
				return;
			}
			
			permission = bot.getCheckUtil().lacksPermissions(event.getTextChannel(), member, userPerms);
			if (permission != null) {
				hook.editOriginal(MessageEditData.fromCreateData(permission)).queue();
				return;
			}

			Guild guild = Objects.requireNonNull(event.getGuild());
			String guildId = guild.getId();

			event.getJDA().retrieveWebhookById(webhookId).queue(
				webhook -> {
					if (bot.getDBUtil().webhookExists(webhookId)) {
						webhook.getManager().setChannel(guild.getTextChannelById(channel.getId())).queue(
							wm -> {
								hook.editOriginalEmbeds(
									bot.getEmbedUtil().getEmbed(member).setDescription(
										bot.getMsg(guildId, "bot.webhook.move.done")
											.replace("{webhook_name}", webhook.getName())
											.replace("{channel}", channel.getName())
									).build()
								).queue();
							},
							failure -> {
								hook.editOriginal(MessageEditData.fromCreateData(
									bot.getEmbedUtil().getError(event, "errors.unknown", failure.getMessage())
								)).queue();
							}
						);
					} else {
						hook.editOriginal(MessageEditData.fromCreateData(
							bot.getEmbedUtil().getError(event, "bot.webhook.move.error_not_registered")
						)).queue();
					}
				}, failure -> {
					hook.editOriginal(MessageEditData.fromCreateData(
						bot.getEmbedUtil().getError(event, "bot.webhook.move.error_not_found", failure.getMessage())
					)).queue();
				}
			);
		}
	}
}
