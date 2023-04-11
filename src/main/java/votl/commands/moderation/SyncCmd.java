package votl.commands.moderation;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import votl.App;
import votl.commands.CommandBase;
import votl.objects.CmdAccessLevel;
import votl.objects.CmdModule;
import votl.objects.command.CooldownScope;
import votl.objects.command.SlashCommand;
import votl.objects.command.SlashCommandEvent;
import votl.objects.constants.CmdCategory;
import votl.objects.constants.Constants;

import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.MessageEmbed;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import net.dv8tion.jda.api.interactions.components.ActionRow;
import net.dv8tion.jda.api.interactions.components.buttons.Button;
import net.dv8tion.jda.api.interactions.components.buttons.ButtonStyle;

import com.jagrosh.jdautilities.commons.waiter.EventWaiter;

public class SyncCmd extends CommandBase {

	private static EventWaiter waiter;
	
	public SyncCmd(App bot, EventWaiter waiter) {
		super(bot);
		this.name = "sync";
		this.path = "bot.moderation.sync";
		this.children = new SlashCommand[]{new Ban(bot), new Unban(bot)};
		this.category = CmdCategory.MODERATION;
		this.module = CmdModule.MODERATION;
		this.accessLevel = CmdAccessLevel.ADMIN;
		this.mustSetup = true;
		SyncCmd.waiter = waiter;
	}

	@Override
	protected void execute(SlashCommandEvent event) {}

	private class Ban extends CommandBase {

		public Ban(App bot) {
			super(bot);
			this.name = "ban";
			this.path = "bot.moderation.sync.ban";
			List<OptionData> options = new ArrayList<>();
			options.add(new OptionData(OptionType.INTEGER, "case_id", lu.getText(path+".option_case"), true).setMinValue(1));
			options.add(new OptionData(OptionType.INTEGER, "master_group", lu.getText(path+".option_group"), true, true).setMinValue(1));
			this.options = options;
			this.cooldownScope = CooldownScope.GUILD;
			this.cooldown = 20;
		}

		@Override
		protected void execute(SlashCommandEvent event) {
			event.deferReply(true).queue();
			Map<String, Object> banData = bot.getDBUtil().ban.getInfo(event.optInteger("case_id", 0));
			if (banData.isEmpty() || !event.getGuild().getId().equals(banData.get("guildId").toString())) {
				editError(event, path+".not_found");
				return;
			}
			if (bot.getDBUtil().ban.utils.isExpirable(banData) || !bot.getDBUtil().ban.utils.isPermament(banData)) {
				editError(event, path+".expirable_ban");
				return;
			}
			
			Integer groupId = event.optInteger("master_group");
			String masterId = bot.getDBUtil().group.getMaster(groupId);
			if (masterId == null || !masterId.equals(event.getGuild().getId())) {
				editError(event, path+".no_group", "Group ID: `"+groupId.toString()+"`");
				return;
			}

			EmbedBuilder builder = bot.getEmbedUtil().getEmbed(event);
			event.getGuild().retrieveBan(User.fromId(banData.get("userId").toString())).queue(ban -> {
				MessageEmbed embed = builder.setDescription(lu.getText(event, path+".embed_title")).build();
				ActionRow button = ActionRow.of(
					Button.of(ButtonStyle.PRIMARY, "button:confirm", lu.getText(event, path+".button_confirm"))
				);
				event.getHook().editOriginalEmbeds(embed).setComponents(button).queue(msg -> {
					waiter.waitForEvent(
						ButtonInteractionEvent.class,
						e -> msg.getId().equals(e.getMessageId()) && e.getComponentId().equals("button:confirm"),
						action -> {
							User target = ban.getUser();
							String reason = ban.getReason();

							List<String> guilds = bot.getDBUtil().group.getGroupGuildIds(groupId);
							if (guilds.isEmpty()) {
								editError(event, path+".no_guilds");
								return;
							};

							Integer maxCount = guilds.size();
							List<String> success = new ArrayList<>();
							for (int i = 0; i < maxCount; i++) {
								Guild guild = event.getJDA().getGuildById(guilds.get(i));
								// fail-safe check if has expirable ban (to prevent auto unban)
								Map<String, Object> banDataExp = bot.getDBUtil().ban.getMemberExpirable(target.getId(), guild.getId());
								if (!banDataExp.isEmpty()) {
									bot.getDBUtil().ban.setInactive(Integer.valueOf(banDataExp.get("banId").toString()));
								}
								
								Boolean last = i + 1 == maxCount;
								guild.ban(target, 0, TimeUnit.SECONDS).reason("Sync: "+reason).queue(done -> {
									bot.getLogListener().onSyncBan(event, guild, target, reason);
									
									success.add(guild.getId());
									if (last) {
										event.getHook().editOriginalEmbeds(builder.setColor(Constants.COLOR_SUCCESS).setDescription(lu.getText(event, path+".done")
											.replace("{count}", String.valueOf(success.size()))
											.replace("{max_count}", maxCount.toString())).build())
											.setComponents().queue();
									}
								},
								failed -> {
									if (last) {
										event.getHook().editOriginalEmbeds(builder.setColor(Constants.COLOR_SUCCESS).setDescription(lu.getText(event, path+".done")
											.replace("{count}", String.valueOf(success.size()))
											.replace("{max_count}", maxCount.toString())).build())
											.setComponents().queue();
									}
								});
							}
						},
						20,
						TimeUnit.SECONDS,
						() -> {
							event.getHook().editOriginalComponents(ActionRow.of(Button.of(ButtonStyle.SECONDARY, "timedout", "Timed out").asDisabled())).queue();
						}
					);
				});
			},
			t -> {

			});
		}

	}

	private class Unban extends CommandBase {

		public Unban(App bot) {
			super(bot);
			this.name = "unban";
			this.path = "bot.moderation.sync.unban";
			List<OptionData> options = new ArrayList<>();
			options.add(new OptionData(OptionType.USER, "member", lu.getText(path+".option_member"), true));
			options.add(new OptionData(OptionType.INTEGER, "master_group", lu.getText(path+".option_group"), true, true).setMinValue(0));
			this.options = options;
			this.cooldownScope = CooldownScope.GUILD;
			this.cooldown = 20;
		}

		@Override
		protected void execute(SlashCommandEvent event) {
			event.deferReply(true).queue();

			Member target = event.optMember("member");
			if (target == null) {
				editError(event, path+".not_found");
				return;
			}
			if (event.getMember().equals(target) || event.getGuild().getSelfMember().equals(target)) {
				editError(event, path+".not_self");
				return;
			}

			Integer groupId = event.optInteger("master_group");
			String masterId = bot.getDBUtil().group.getMaster(groupId);
			if (masterId == null || !masterId.equals(event.getGuild().getId())) {
				editError(event, path+".no_group", "Group ID: `"+groupId.toString()+"`");
				return;
			}

			EmbedBuilder builder = bot.getEmbedUtil().getEmbed(event);

			MessageEmbed embed = builder.setDescription(lu.getText(event, path+".embed_title")).build();
			ActionRow button = ActionRow.of(
				Button.of(ButtonStyle.PRIMARY, "button:confirm", lu.getText(event, path+".button_confirm"))
			);
			event.getHook().editOriginalEmbeds(embed).setComponents(button).queue(msg -> {
				waiter.waitForEvent(
					ButtonInteractionEvent.class,
					e -> msg.getId().equals(e.getMessageId()) && e.getComponentId().equals("button:confirm"),
					action -> {
						List<String> guilds = bot.getDBUtil().group.getGroupGuildIds(groupId);
						if (guilds.isEmpty()) {
							editError(event, path+".no_guilds");
							return;
						};

						Integer maxCount = guilds.size();
						List<String> success = new ArrayList<>();
						for (int i = 0; i < maxCount; i++) {
							Guild guild = event.getJDA().getGuildById(guilds.get(i));

							Boolean last = i + 1 == maxCount;
							guild.retrieveBan(target).queue(ban -> {
								guild.unban(target).reason("Sync: Manual ban lift").queue(done -> {
									bot.getLogListener().onSyncUnban(event, guild, ban.getUser(), ban.getReason(), "Manual ban lift");
									
									success.add(guild.getId());
									if (last) {
										event.getHook().editOriginalEmbeds(builder.setColor(Constants.COLOR_SUCCESS).setDescription(lu.getText(event, path+".done")
											.replace("{count}", String.valueOf(success.size()))
											.replace("{max_count}", maxCount.toString())).build())
											.setComponents().queue();
									}
								},
								failed -> {
									if (last) {
										event.getHook().editOriginalEmbeds(builder.setColor(Constants.COLOR_SUCCESS).setDescription(lu.getText(event, path+".done")
											.replace("{count}", String.valueOf(success.size()))
											.replace("{max_count}", maxCount.toString())).build())
											.setComponents().queue();
									}
								});
							});
						}
					},
					20,
					TimeUnit.SECONDS,
					() -> {
						event.getHook().editOriginalComponents(ActionRow.of(Button.of(ButtonStyle.SECONDARY, "timed_out", "Timed out").asDisabled())).queue();
					}
				);
			});
		}

	}

}
