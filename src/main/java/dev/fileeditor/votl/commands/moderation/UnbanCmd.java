package dev.fileeditor.votl.commands.moderation;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.entities.MessageEmbed;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.entities.Guild.Ban;
import net.dv8tion.jda.api.events.interaction.component.StringSelectInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import net.dv8tion.jda.api.interactions.components.selections.SelectOption;
import net.dv8tion.jda.api.interactions.components.selections.StringSelectMenu;

import com.jagrosh.jdautilities.commons.waiter.EventWaiter;

import dev.fileeditor.votl.App;
import dev.fileeditor.votl.commands.CommandBase;
import dev.fileeditor.votl.objects.CmdAccessLevel;
import dev.fileeditor.votl.objects.CmdModule;
import dev.fileeditor.votl.objects.command.SlashCommandEvent;
import dev.fileeditor.votl.objects.constants.CmdCategory;
import dev.fileeditor.votl.objects.constants.Constants;

public class UnbanCmd extends CommandBase {

	private EventWaiter waiter;
	
	public UnbanCmd(App bot, EventWaiter waiter) {
		super(bot);
		this.name = "unban";
		this.path = "bot.moderation.unban";
		List<OptionData> options = new ArrayList<>();
		options.add(new OptionData(OptionType.USER, "user", lu.getText(path+".option_user"), true));
		options.add(new OptionData(OptionType.STRING, "reason", lu.getText(path+".option_reason")).setMaxLength(400));
		this.options = options;
		this.botPermissions = new Permission[]{Permission.BAN_MEMBERS};
		this.category = CmdCategory.MODERATION;
		this.module = CmdModule.MODERATION;
		this.accessLevel = CmdAccessLevel.MOD;
		this.mustSetup = true;
		this.waiter = waiter;
	}

	@Override
	protected void execute(SlashCommandEvent event) {
		event.deferReply(false).queue();
		Guild guild = Objects.requireNonNull(event.getGuild());

		User tu = event.optUser("user");
		String reason = event.optString("reason", lu.getText(event, path+".no_reason"));

		if (tu == null) {
			editError(event, path+".not_found");
			return;
		}
		if (event.getUser().equals(tu) || event.getJDA().getSelfUser().equals(tu)) {
			editError(event, path+".not_self");
			return;
		}

		guild.retrieveBan(tu).queue(ban -> {
			// perform unban
			guild.unban(tu).reason(reason).queue();
			// create embed
			MessageEmbed embed = bot.getEmbedUtil().getEmbed(event)
				.setColor(Constants.COLOR_SUCCESS)
				.setDescription(lu.getText(event, path+".unban_success")
					.replace("{user_tag}", tu.getAsTag())
					.replace("{reason}", reason))
				.build();
			// ask for ban sync
			event.getHook().editOriginalEmbeds(embed).queue(msg -> {
				buttonSync(event, msg, ban, reason);
			});

			// log unban
			bot.getLogListener().onUnban(event, event.getMember(), ban, reason);
		},
		failure -> {
			editError(event, path+".no_ban");
		});
	}

	private void buttonSync(SlashCommandEvent event, final Message message, Ban ban, String reason) {
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
						guild.unban(ban.getUser()).reason("Sync: "+reason).queue(done -> {
							bot.getLogListener().onSyncUnban(event, guild, ban.getUser(), ban.getReason(), reason);
							
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
