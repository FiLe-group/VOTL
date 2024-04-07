package dev.fileeditor.votl.commands.moderation;

import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.events.interaction.component.StringSelectInteractionEvent;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.entities.MessageEmbed;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import net.dv8tion.jda.api.interactions.components.selections.SelectOption;
import net.dv8tion.jda.api.interactions.components.selections.StringSelectMenu;

import com.jagrosh.jdautilities.commons.waiter.EventWaiter;

import dev.fileeditor.votl.App;
import dev.fileeditor.votl.commands.CommandBase;
import dev.fileeditor.votl.objects.CmdAccessLevel;
import dev.fileeditor.votl.objects.CmdModule;
import dev.fileeditor.votl.objects.command.CooldownScope;
import dev.fileeditor.votl.objects.command.SlashCommandEvent;
import dev.fileeditor.votl.objects.constants.CmdCategory;
import dev.fileeditor.votl.objects.constants.Constants;
import dev.fileeditor.votl.utils.exception.FormatterException;

public class BanCmd extends CommandBase {

	private EventWaiter waiter;
	
	public BanCmd(App bot, EventWaiter waiter) {
		super(bot);
		this.name = "ban";
		this.path = "bot.moderation.ban";
		List<OptionData> options = new ArrayList<>();
		options.add(new OptionData(OptionType.USER, "user", lu.getText(path+".option_user"), true));
		options.add(new OptionData(OptionType.STRING, "time", lu.getText(path+".option_time")));
		options.add(new OptionData(OptionType.STRING, "reason", lu.getText(path+".option_reason")).setMaxLength(400));
		options.add(new OptionData(OptionType.BOOLEAN, "delete", lu.getText(path+".option_delete")));
		options.add(new OptionData(OptionType.BOOLEAN, "dm", lu.getText(path+".option_dm")));
		this.options = options;
		this.botPermissions = new Permission[]{Permission.BAN_MEMBERS};
		this.category = CmdCategory.MODERATION;
		this.module = CmdModule.MODERATION;
		this.accessLevel = CmdAccessLevel.MOD;
		this.mustSetup = true;
		this.cooldown = 10;
		this.cooldownScope = CooldownScope.GUILD;
		this.waiter = waiter;
	}

	@Override
	protected void execute(SlashCommandEvent event) {
		event.deferReply(false).queue();
		
		User targetUser = event.optUser("user");
		String time = event.optString("time");
		String reason = event.optString("reason", lu.getLocalized(event.getGuildLocale(), path+".no_reason"));
		Boolean delete = event.optBoolean("delete", true);
		Boolean dm = event.optBoolean("dm", true);

		sendReply(event, targetUser, time, reason, delete, dm);
	}

	private void sendReply(SlashCommandEvent event, User tu, String time, String reason, Boolean delete, Boolean dm) {
		Guild guild = Objects.requireNonNull(event.getGuild());

		if (tu == null) {
			editError(event, path+".not_found");
			return;
		}
		if (event.getUser().equals(tu) || event.getJDA().getSelfUser().equals(tu)) {
			editError(event, path+".not_self");
			return;
		}

		final Duration duration;
		try {
			duration = bot.getTimeUtil().stringToDuration(time, false);
		} catch (FormatterException ex) {
			editError(event, ex.getPath());
			return;
		}

		if (dm) {
			tu.openPrivateChannel().queue(pm -> {
				MessageEmbed embed = bot.getEmbedUtil().getEmbed().setColor(Constants.COLOR_FAILURE)
					.setDescription("You were banned from " + guild.getName() + (duration.isZero() ? "" : " for " + bot.getTimeUtil().durationToString(duration)) + ". | " + reason)
					.build();
				pm.sendMessageEmbeds(embed).queue();
			});
		}

		guild.retrieveBan(tu).queueAfter((dm ? 3 : 0), TimeUnit.SECONDS, ban -> {
			Map<String, Object> banData = bot.getDBUtil().ban.getMemberExpirable(tu.getId(), guild.getId());
			if (!banData.isEmpty()) {
				Integer caseId = Integer.valueOf(banData.get("banId").toString());
				if (duration.isZero()) {
					// make current temporary ban inactive
					bot.getDBUtil().ban.setInactive(caseId);
					// create new entry
					Integer banId = 1 + bot.getDBUtil().ban.lastId();
					Member mod = event.getMember();
					bot.getDBUtil().ban.add(banId, tu.getId(), tu.getAsTag(), mod.getId(), mod.getUser().getAsTag(),
						guild.getId(), reason, Timestamp.from(Instant.now()), duration);
					// create embed
					MessageEmbed embed = bot.getEmbedUtil().getEmbed(event)
						.setColor(Constants.COLOR_SUCCESS)
						.setDescription(lu.getText(event, path+".ban_success")
							.replace("{user_tag}", tu.getAsTag())
							.replace("{duration}", lu.getText(event, "bot.moderation.embeds.permanently"))
							.replace("{reason}", reason))
						.build();
					// ask for ban sync
					event.getHook().editOriginalEmbeds(embed).queue(msg -> {
						buttonSync(event, msg, tu, reason);
					});

					// log ban
					bot.getLogListener().onBan(event, tu, mod, banId);
				} else {
					// already has expirable ban (show caseID and use /duration to change time)
					MessageEmbed embed = bot.getEmbedUtil().getEmbed(event)
						.setColor(Constants.COLOR_WARNING)
						.setDescription(lu.getText(event, path+".already_temp").replace("{id}", caseId.toString()))
						.build();
					event.getHook().editOriginalEmbeds(embed).queue();
				}
				return;
			}

			// user has permament ban
			MessageEmbed embed = bot.getEmbedUtil().getEmbed(event)
				.setColor(Constants.COLOR_WARNING)
				.setDescription(lu.getText(event, path+".already_banned"))
				.addField(lu.getText(event, "bot.moderation.embeds.ban.short_title"), lu.getText(event, "bot.moderation.embeds.ban.short_info")
					.replace("{username}", ban.getUser().getAsTag())
					.replace("{reason}", ban.getReason())
					, false)
				.build();
			event.getHook().editOriginalEmbeds(embed).queue(msg -> {
				buttonSync(event, msg, ban.getUser(), ban.getReason());
			});
		},
		failure -> {
			// checks if thrown something except from "ban not found"
			if (!failure.getMessage().startsWith("10026")) {
				bot.getLogger().warn(failure.getMessage());
				editError(event, "errors.unknown", failure.getMessage());
				return;
			}

			Member tm = event.optMember("user");
			Member mod = event.getMember();
			if (tm != null) {
				if (!guild.getSelfMember().canInteract(tm)) {
					editError(event, path+".ban_abort");
					return;
				}
				if (bot.getCheckUtil().hasHigherAccess(event.getClient(), tm, mod)) {
					editError(event, path+".higher_access");
					return;
				}
			}

			guild.ban(tu, (delete ? 10 : 0), TimeUnit.HOURS).reason(reason).queue(done -> {
				// get new caseId/banId
				Integer banId = 1 + bot.getDBUtil().ban.lastId();
				// fail-safe check if has expirable ban (to prevent auto unban)
				Map<String, Object> banData = bot.getDBUtil().ban.getMemberExpirable(tu.getId(), guild.getId());
				if (!banData.isEmpty()) {
					bot.getDBUtil().ban.setInactive(Integer.valueOf(banData.get("banId").toString()));
				}
				// add info to db
				bot.getDBUtil().ban.add(banId, tu.getId(), tu.getAsTag(), mod.getId(), mod.getUser().getAsTag(),
					guild.getId(), reason, Timestamp.from(Instant.now()), duration);
				// create embed
				MessageEmbed embed = bot.getEmbedUtil().getEmbed(event)
					.setColor(Constants.COLOR_SUCCESS)
					.setDescription(lu.getText(event, path+".ban_success")
						.replace("{user_tag}", tu.getAsTag())
						.replace("{duration}", duration.isZero() ? lu.getText(event, "bot.moderation.embeds.permanently") : 
							lu.getText(event, "bot.moderation.embeds.temporary")
								.replace("{time}", bot.getTimeUtil().formatTime(Instant.now().plus(duration), true))
						)
						.replace("{reason}", reason))
					.build();
				// ask for ban sync
				event.getHook().editOriginalEmbeds(embed).queue(msg -> {
					if (duration.isZero()) buttonSync(event, msg, tu, reason);
				});
				
				// log ban
				bot.getLogListener().onBan(event, tu, mod, banId);
			},
			failed -> {
				editError(event, "errors.unknown", failed.getMessage());
			});
		});
	}

	private void buttonSync(SlashCommandEvent event, final Message message, User tu, String reason) {
		if (!bot.getCheckUtil().hasAccess(event, CmdAccessLevel.ADMIN)) return;

		List<Map<String, Object>> groups = bot.getDBUtil().group.getMasterGroups(event.getGuild().getId());
		if (groups.isEmpty()) return;

		EmbedBuilder builder = bot.getEmbedUtil().getEmbed()
			.setDescription(lu.getText(event, path+".sync.title"));
		StringSelectMenu menu = StringSelectMenu.create("groupId")
			.setPlaceholder(lu.getText(event, path+".sync.value"))
			.addOptions(groups.stream().map(group ->
				SelectOption.of(group.get("name").toString(), group.get("groupId").toString()).withDescription("ID: "+group.get("groupId").toString())
			).collect(Collectors.toList()))
			.setMaxValues(5)
			.build();

		message.replyEmbeds(builder.build()).setActionRow(menu).queue(msg -> {
			waiter.waitForEvent(
				StringSelectInteractionEvent.class,
				e -> e.getMessageId().equals(msg.getId()) && e.getUser().equals(event.getUser()),
				selectEvent -> {
					selectEvent.deferEdit().queue();
					List<SelectOption> selected = selectEvent.getSelectedOptions();
					
					List<String> guilds = new ArrayList<>();
					for (SelectOption option : selected) {
						Integer groupId = Integer.parseInt(option.getValue());
						guilds.addAll(bot.getDBUtil().group.getGroupGuildIds(groupId));
					}

					if (guilds.isEmpty()) {
						selectEvent.getHook().editOriginalEmbeds(builder.setColor(Constants.COLOR_FAILURE).setDescription(lu.getText(event, path+".sync.no_guilds")).build())
							.setComponents().queue();
						return;
					}

					Integer maxCount = guilds.size();
					List<String> success = new ArrayList<>();
					for (int i = 0; i < maxCount; i++) {
						Guild guild = event.getJDA().getGuildById(guilds.get(i));
						// fail-safe check if has expirable ban (to prevent auto unban)
						Map<String, Object> banData = bot.getDBUtil().ban.getMemberExpirable(tu.getId(), guild.getId());
						if (!banData.isEmpty()) {
							bot.getDBUtil().ban.setInactive(Integer.valueOf(banData.get("banId").toString()));
						}
						
						Boolean last = i + 1 == maxCount;
						guild.ban(tu, 0, TimeUnit.SECONDS).reason("Sync: "+reason).queue(done -> {
							bot.getLogListener().onSyncBan(event, guild, tu, reason);
							
							success.add(guild.getId());
							if (last) {
								selectEvent.getHook().editOriginalEmbeds(builder.setColor(Constants.COLOR_SUCCESS).setDescription(lu.getText(event, path+".sync.done")
									.replace("{count}", String.valueOf(success.size()))
									.replace("{max_count}", maxCount.toString())).build())
									.setComponents().queue();
							}
						},
						failed -> {
							if (last) {
								selectEvent.getHook().editOriginalEmbeds(builder.setColor(Constants.COLOR_SUCCESS).setDescription(lu.getText(event, path+".sync.done")
									.replace("{count}", String.valueOf(success.size()))
									.replace("{max_count}", maxCount.toString())).build())
									.setComponents().queue();
							}
						});
					}
					
				},
				15,
				TimeUnit.SECONDS,
				() -> msg.delete().queue()
			);
		});
	}
	
}
