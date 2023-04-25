package votl.commands.moderation;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import votl.App;
import votl.commands.CommandBase;
import votl.objects.CmdAccessLevel;
import votl.objects.CmdModule;
import votl.objects.command.CooldownScope;
import votl.objects.command.SlashCommandEvent;
import votl.objects.constants.CmdCategory;
import votl.objects.constants.Constants;

import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.entities.MessageEmbed;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.events.interaction.component.StringSelectInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import net.dv8tion.jda.api.interactions.components.selections.SelectOption;
import net.dv8tion.jda.api.interactions.components.selections.StringSelectMenu;

import com.jagrosh.jdautilities.commons.waiter.EventWaiter;

public class KickCmd extends CommandBase {

	private EventWaiter waiter;
	
	public KickCmd (App bot, EventWaiter waiter) {
		super(bot);
		this.name = "kick";
		this.path = "bot.moderation.kick";
		List<OptionData> options = new ArrayList<>();
		options.add(new OptionData(OptionType.USER, "member", lu.getText(path+".option_member"), true));
		options.add(new OptionData(OptionType.STRING, "reason", lu.getText(path+".option_reason")).setMaxLength(400));
		options.add(new OptionData(OptionType.BOOLEAN, "dm", lu.getText(path+".option_dm")));
		this.options = options;
		this.botPermissions = new Permission[]{Permission.KICK_MEMBERS};
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
		
		Member targetMember = event.optMember("member");
		String reason = event.optString("reason", lu.getLocalized(event.getGuildLocale(), path+".no_reason"));
		Boolean dm = event.optBoolean("dm", false);

		sendReply(event, targetMember, reason, dm);
	}

	private void sendReply(SlashCommandEvent event, Member tm, String reason, Boolean dm) {
		Guild guild = Objects.requireNonNull(event.getGuild());

		if (tm == null) {
			editError(event, path+".not_found");
			return;
		}
		if (event.getMember().equals(tm) || guild.getSelfMember().equals(tm)) {
			editError(event, path+".not_self");
			return;
		}

		if (dm) {
			tm.getUser().openPrivateChannel().queue(pm -> {
				MessageEmbed embed = bot.getEmbedUtil().getEmbed().setColor(Constants.COLOR_FAILURE)
					.setDescription("You were kicked from " + guild.getName() + ". | " + reason)
					.build();
				pm.sendMessageEmbeds(embed).queue();
			});
		}

		if (!guild.getSelfMember().canInteract(tm)) {
			editError(event, path+".kick_abort");
			return;
		}
		if (bot.getCheckUtil().hasHigherAccess(event.getClient(), tm, event.getMember())) {
			editError(event, path+".higher_access");
			return;
		}

		tm.kick().reason(reason).queue(done -> {
			// create embed
			MessageEmbed embed = bot.getEmbedUtil().getEmbed(event)
			.setColor(Constants.COLOR_SUCCESS)
			.setDescription(lu.getText(event, path+".kick_success")
				.replace("{user_tag}", tm.getUser().getAsTag())
				.replace("{reason}", reason))
			.build();
			// ask for kick sync
			event.getHook().editOriginalEmbeds(embed).queue(msg -> {
				buttonSync(event, msg, tm.getUser(), reason);
			});
			
			// log kick
			bot.getLogListener().onKick(event, tm.getUser(), event.getUser(), reason);
			
		},
		failure -> {
			editError(event, "errors.unknown", failure.getMessage());
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

						Boolean last = i + 1 == maxCount;
						guild.retrieveMember(tu).queue(member -> {
							guild.kick(tu).reason("Sync: "+reason).queue(done -> {
								bot.getLogListener().onSyncKick(event, guild, tu, reason);
	
								success.add(guild.getId());
								if (last) {
									selectEvent.getHook().editOriginalEmbeds(builder.setColor(Constants.COLOR_SUCCESS).setDescription(lu.getText(event, path+".sync.done")
										.replace("{count}", String.valueOf(success.size()))
										.replace("{max_count}", maxCount.toString())).build())
										.setComponents().queue();
								}
							},
							failure -> {
								if (last) {
									selectEvent.getHook().editOriginalEmbeds(builder.setColor(Constants.COLOR_SUCCESS).setDescription(lu.getText(event, path+".sync.done")
										.replace("{count}", String.valueOf(success.size()))
										.replace("{max_count}", maxCount.toString())).build())
										.setComponents().queue();
								}
							});
						},
						notFound -> {
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
