package votl.commands.moderation;

import java.util.ArrayList;
import java.util.Collections;
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
import votl.objects.command.SlashCommand;
import votl.objects.command.SlashCommandEvent;
import votl.objects.constants.CmdCategory;
import votl.objects.constants.Constants;

import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.MessageEmbed;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import net.dv8tion.jda.api.events.interaction.component.StringSelectInteractionEvent;
import net.dv8tion.jda.api.interactions.InteractionHook;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import net.dv8tion.jda.api.interactions.components.ActionRow;
import net.dv8tion.jda.api.interactions.components.buttons.Button;
import net.dv8tion.jda.api.interactions.components.buttons.ButtonStyle;
import net.dv8tion.jda.api.interactions.components.selections.SelectOption;
import net.dv8tion.jda.api.interactions.components.selections.StringSelectMenu;

import com.jagrosh.jdautilities.commons.waiter.EventWaiter;

public class GroupCmd extends CommandBase {
	
	private static EventWaiter waiter;

	public GroupCmd(App bot, EventWaiter waiter) {
		super(bot);
		this.name = "group";
		this.path = "bot.moderation.group";
		this.children = new SlashCommand[]{new Create(bot), new Delete(bot), new Join(bot), new Leave(bot), new Remove(bot), new View(bot), new Rename(bot)};
		this.category = CmdCategory.MODERATION;
		this.module = CmdModule.MODERATION;
		this.accessLevel = CmdAccessLevel.ADMIN;
		this.mustSetup = true;
		GroupCmd.waiter = waiter;
	}

	@Override
	protected void execute(SlashCommandEvent event) {}

	private class Create extends CommandBase {

		public Create(App bot) {
			super(bot);
			this.name = "create";
			this.path = "bot.moderation.group.create";
			this.options = Collections.singletonList(
				new OptionData(OptionType.STRING, "name", lu.getText(path+".option_name"), true)
					.setMaxLength(100)
			);
			this.cooldownScope = CooldownScope.GUILD;
			this.cooldown = 60;
		}

		@Override
		protected void execute(SlashCommandEvent event) {
			String guildId = Objects.requireNonNull(event.getGuild()).getId();
			if (bot.getDBUtil().group.getMasterGroups(guildId).size() >= 3 ) {
				createError(event, path+".max_amount");
				return;
			}

			String groupName = event.optString("name");

			Integer groupId = bot.getDBUtil().group.lastId() + 1;
			bot.getDBUtil().group.create(groupId, guildId, groupName);
			bot.getLogListener().onGroupCreation(event, groupId, groupName);

			MessageEmbed embed = bot.getEmbedUtil().getEmbed(event)
				.setColor(Constants.COLOR_SUCCESS)
				.setDescription(lu.getText(event, path+".done").replace("{group_name}", groupName).replace("{group_id}", groupId.toString()))
				.build();
			createReplyEmbed(event, embed);
		}

	}

	private class Delete extends CommandBase {
		
		public Delete(App bot) {
			super(bot);
			this.name = "delete";
			this.path = "bot.moderation.group.delete";
			this.options = Collections.singletonList(
				new OptionData(OptionType.INTEGER, "master_group", lu.getText(path+".option_group"), true, true)
					.setMinValue(0)
			);
			this.cooldownScope = CooldownScope.GUILD;
			this.cooldown = 30;
		}

		@Override
		protected void execute(SlashCommandEvent event) {
			Integer groupId = event.optInteger("master_group");
			String masterId = bot.getDBUtil().group.getMaster(groupId);
			if (masterId == null || !masterId.equals(event.getGuild().getId())) {
				createError(event, path+".no_group", "Group ID: `"+groupId.toString()+"`");
				return;
			}

			String groupName = bot.getDBUtil().group.getName(groupId);

			bot.getDBUtil().group.delete(groupId);
			bot.getLogListener().onGroupDeletion(event, groupId, groupName);

			MessageEmbed embed = bot.getEmbedUtil().getEmbed(event)
				.setColor(Constants.COLOR_SUCCESS)
				.setDescription(lu.getText(event, path+".done").replace("{group_name}", groupName).replace("{group_id}", groupId.toString()))
				.build();
			event.replyEmbeds(embed).queue();
		}

	}

	private class Join extends CommandBase {

		public Join(App bot) {
			super(bot);
			this.name = "join";
			this.path = "bot.moderation.group.join";
			this.options = Collections.singletonList(
				new OptionData(OptionType.INTEGER, "group_id", lu.getText(path+".option_id"), true)
					.setMinValue(1)
			);
		}

		protected void execute(SlashCommandEvent event) {
			event.deferReply(true).queue();
			InteractionHook hook = event.getHook();

			String guildId = Objects.requireNonNull(event.getGuild()).getId();
			if (bot.getDBUtil().group.getGuildGroups(guildId).size() >= 5 ) {
				editError(event, path+".max_amount");
				return;
			}

			Integer groupId = event.optInteger("group_id");
			String masterId = bot.getDBUtil().group.getMaster(groupId);
			if (masterId == null) {
				editError(event, path+".no_group", "Group ID: `"+groupId+"`");
				return;
			}
			if (bot.getDBUtil().group.existSync(groupId, guildId) == true) {
				editError(event, path+".already_exists");
				return;
			}
			if (masterId.equals(guildId)) {
				editError(event, path+".is_master");
				return;
			}

			String groupName = bot.getDBUtil().group.getName(groupId);
			MessageEmbed embed = bot.getEmbedUtil().getEmbed(event)
				.setTitle(lu.getText(event, path+".embed_title"))
				.setDescription(lu.getText(event, path+".embed_value").replace("{group_name}", groupName).replace("{group_id}", groupId.toString()))
				.build();
			ActionRow buttons = ActionRow.of(
				Button.of(ButtonStyle.PRIMARY, "button:confirm", lu.getText(event, path+".button_confirm")),
				Button.of(ButtonStyle.DANGER, "button:abort", lu.getText(event, path+".button_abort"))
			);
			hook.editOriginalEmbeds(embed).setComponents(buttons).queue(msg -> {
				waiter.waitForEvent(
					ButtonInteractionEvent.class,
					e -> msg.getId().equals(e.getMessageId()) && (e.getComponentId().equals("button:confirm") || e.getComponentId().equals("button:abort")),
					action -> {
					EmbedBuilder embedEdit = bot.getEmbedUtil().getEmbed(event);
						if (action.getComponentId().equals("button:confirm")) {
							bot.getDBUtil().group.add(groupId, guildId);
							bot.getLogListener().onGroupJoin(event, groupId, groupName);

							embedEdit.setColor(Constants.COLOR_SUCCESS).setDescription(lu.getText(event, path+".done").replace("{group_name}", groupName));
							hook.editOriginalEmbeds(embedEdit.build()).setComponents().queue();
						} else {
							embedEdit.setColor(Constants.COLOR_FAILURE).setDescription(lu.getText(event, path+".abort"));
							hook.editOriginalEmbeds(embedEdit.build()).setComponents().queue();
						}
					},
					20,
					TimeUnit.SECONDS,
					() -> {
						hook.editOriginalEmbeds(bot.getEmbedUtil().getEmbed(event).setDescription(lu.getText(event, path+".abort")).setColor(Constants.COLOR_FAILURE).build())
							.setComponents().queue();
					}
				);
			});
		}

	}

	private class Leave extends CommandBase {

		public Leave(App bot) {
			super(bot);
			this.name = "leave";
			this.path = "bot.moderation.group.leave";
			this.options = Collections.singletonList(
				new OptionData(OptionType.INTEGER, "sync_group", lu.getText(path+".option_group"), true, true)
					.setMinValue(0)
			);
		}

		protected void execute(SlashCommandEvent event) {
			String guildId = event.getGuild().getId();
			Integer groupId = event.optInteger("sync_group");
			String masterId = bot.getDBUtil().group.getMaster(groupId);
			if (masterId == null || !bot.getDBUtil().group.existSync(groupId, guildId)) {
				createError(event, path+".no_group", "Group ID: `"+groupId.toString()+"`");
				return;
			}

			String groupName = bot.getDBUtil().group.getName(groupId);

			bot.getDBUtil().group.remove(groupId, guildId);
			bot.getLogListener().onGroupLeave(event, groupId, groupName);

			MessageEmbed embed = bot.getEmbedUtil().getEmbed(event)
				.setColor(Constants.COLOR_SUCCESS)
				.setDescription(lu.getText(event, path+".done").replace("{group_name}", groupName).replace("{group_id}", groupId.toString()))
				.build();
			event.replyEmbeds(embed).queue();
		}
		
	}

	private class Remove extends CommandBase {

		public Remove(App bot) {
			super(bot);
			this.name = "remove";
			this.path = "bot.moderation.group.remove";
			this.options = Collections.singletonList(
				new OptionData(OptionType.INTEGER, "master_group", lu.getText(path+".option_group"), true, true)
					.setMinValue(0)
			);
		}

		protected void execute(SlashCommandEvent event) {
			event.deferReply(true).queue();
			InteractionHook hook = event.getHook();

			Integer groupId = event.optInteger("master_group");
			String masterId = bot.getDBUtil().group.getMaster(groupId);
			if (masterId == null || !masterId.equals(event.getGuild().getId())) {
				editError(event, path+".no_group", "Group ID: `"+groupId.toString()+"`");
				return;
			}
			String groupName = bot.getDBUtil().group.getName(groupId);

			List<String> guildIds = bot.getDBUtil().group.getGroupGuildIds(groupId);
			if (guildIds.isEmpty()) {
				editError(event, path+".no_guilds");
				return;
			}

			MessageEmbed embed = bot.getEmbedUtil().getEmbed(event)
				.setTitle(lu.getText(event, path+".embed_title"))
				.setDescription(lu.getText(event, path+".embed_value").replace("{group_name}", groupName))
				.build();
			StringSelectMenu menu = StringSelectMenu.create("menu:remove-guild")
				.setPlaceholder("Select")
				.setMaxValues(1)
				.addOptions(guildIds.stream().map(
					guildId -> {
						String guildName = event.getJDA().getGuildById(guildId).getName();
						return SelectOption.of(String.format("%s (%s)", guildName, guildId), guildId);
					}
				).collect(Collectors.toList()))
				.build();
			
			hook.editOriginalEmbeds(embed).setActionRow(menu).queue(msg -> {
				waiter.waitForEvent(
					StringSelectInteractionEvent.class,
					e -> e.getComponentId().equals("menu:remove-guild") && e.getMessageId().equals(msg.getId()),
					actionMenu -> {
						String targetId = actionMenu.getSelectedOptions().get(0).getValue();
						Guild targetGuild = event.getJDA().getGuildById(targetId);

						bot.getDBUtil().group.remove(groupId, targetId);
						bot.getLogListener().onGroupRemove(event, targetGuild, groupId, groupName);

						MessageEmbed editEmbed = bot.getEmbedUtil().getEmbed(event)
							.setColor(Constants.COLOR_SUCCESS)
							.setDescription(lu.getText(event, path+".done").replace("{guild_name}", targetGuild.getName()).replace("{group_name}", groupName))
							.build();
						hook.editOriginalEmbeds(editEmbed).setComponents().queue();
					},
					30,
					TimeUnit.SECONDS,
					() -> {
						hook.editOriginalComponents(
							ActionRow.of(menu.createCopy().setPlaceholder(lu.getText(event, path+".timed_out")).setDisabled(true).build())
						).queue();
					}
				);
			});
		}
		
	}

	private class View extends CommandBase {

		public View(App bot) {
			super(bot);
			this.name = "view";
			this.path = "bot.moderation.group.view";
			List<OptionData> options = new ArrayList<>();
			options.add(new OptionData(OptionType.INTEGER, "master_group", lu.getText(path+".option_mastergroup"), false, true)
				.setMinValue(0));
			options.add(new OptionData(OptionType.INTEGER, "sync_group", lu.getText(path+".option_syncgroup"), false, true)
				.setMinValue(0));
			this.options = options;
		}

		protected void execute(SlashCommandEvent event) {
			String guildId = event.getGuild().getId();
			if (event.hasOption("master_group")) {
				// View master group information - name, every guild info (name, ID, member count)
				Integer groupId = event.optInteger("master_group");
				String masterId = bot.getDBUtil().group.getMaster(groupId);
				if (masterId == null || !masterId.equals(guildId)) {
					createError(event, path+".no_group", "Group ID: `"+groupId.toString()+"`");
					return;
				}

				String groupName = bot.getDBUtil().group.getName(groupId);
				List<String> groupGuildIds = bot.getDBUtil().group.getGroupGuildIds(groupId);
				Integer groupSize = groupGuildIds.size();

				EmbedBuilder builder = new EmbedBuilder(bot.getEmbedUtil().getEmbed(event))
					.setAuthor(lu.getText(event, "bot.moderation.embeds.group.title").replace("{group_name}", groupName).replace("{group_id}", groupId.toString()))
					.setDescription(lu.getText(event, path+".embed_value").replace("{guild_name}", event.getGuild().getName()).replace("{guild_id}", masterId).replace("{size}", groupSize.toString()));
				
				if (groupSize > 0) {
					String fieldLabel = lu.getText(event, path+".embed_guilds");
					StringBuffer buffer = new StringBuffer();
					String format = "%s | %s | `%s`";
					for (String groupGuildId : groupGuildIds) {
						Guild guild = event.getJDA().getGuildById(groupGuildId);
						if (guild == null) continue;
	
						String line = String.format(format, guild.getName(), guild.getMemberCount(), guild.getId());
						if (buffer.length() + line.length() + 2 > 1000) {
							builder.addField(fieldLabel, buffer.toString(), false);
							buffer.setLength(0);
							buffer.append(line+"\n");
							fieldLabel = "";
						} else {
							buffer.append(line+"\n");
						}
					}
					builder.addField(fieldLabel, buffer.toString(), false);
				}
				createReplyEmbed(event, builder.build());
			} else if (event.hasOption("sync_group")) {
				// View sync group information - name, master name/ID, guild count
				Integer groupId = event.optInteger("sync_group");
				String masterId = bot.getDBUtil().group.getMaster(groupId);
				if (masterId == null || !bot.getDBUtil().group.existSync(groupId, guildId)) {
					createError(event, path+".no_group", "Group ID: `"+groupId.toString()+"`");
					return;
				}
				
				String groupName = bot.getDBUtil().group.getName(groupId);
				String masterName = event.getJDA().getGuildById(masterId).getName();
				Integer groupSize = bot.getDBUtil().group.getGroupGuildIds(groupId).size();

				EmbedBuilder builder = new EmbedBuilder(bot.getEmbedUtil().getEmbed(event))
					.setAuthor(lu.getText(event, "bot.moderation.embeds.group.title").replace("{group_name}", groupName).replace("{group_id}", groupId.toString()))
					.setDescription(lu.getText(event, path+".embed_value").replace("{guild_name}", masterName).replace("{guild_id}", masterId).replace("{size}", groupSize.toString()));
				createReplyEmbed(event, builder.build());
			} else {
				// No options provided - reply with all groups that this guild is connected
				List<Map<String, Object>> masterGroups = bot.getDBUtil().group.getMasterGroups(guildId);
				List<Integer> syncGroupIds = bot.getDBUtil().group.getGuildGroups(guildId);

				EmbedBuilder builder = new EmbedBuilder(bot.getEmbedUtil().getEmbed(event))
					.setDescription("Group name | #ID");
				
				String fieldLabel = lu.getText(event, path+".embed_master");
				if (masterGroups.isEmpty()) {
					builder.addField(fieldLabel, lu.getText(event, path+".none"), false);
				} else {
					StringBuffer buffer = new StringBuffer();
					for (Map<String, Object> group : masterGroups) {
						buffer.append(String.format("%s | #%s\n", group.get("name").toString(), group.get("groupId").toString()));
					}
					builder.addField(fieldLabel, buffer.toString(), false);
				}

				fieldLabel = lu.getText(event, path+".embed_sync");
				if (syncGroupIds.isEmpty()) {
					builder.addField(fieldLabel, lu.getText(event, path+".none"), false);
				} else {
					StringBuffer buffer = new StringBuffer();
					for (Integer groupId : syncGroupIds) {
						buffer.append(String.format("%s | #%s\n", bot.getDBUtil().group.getName(groupId), groupId.toString()));
					}
					builder.addField(fieldLabel, buffer.toString(), false);
				}

				createReplyEmbed(event, builder.build());
			}
		}
		
	}

	private class Rename extends CommandBase {

		public Rename(App bot) {
			super(bot);
			this.name = "rename";
			this.path = "bot.moderation.group.rename";
			List<OptionData> options = new ArrayList<>();
			options.add(new OptionData(OptionType.INTEGER, "master_group", lu.getText(path+".option_group"), true, true)
				.setMinValue(0));
			options.add(new OptionData(OptionType.STRING, "name", lu.getText(path+".option_name"), true)
				.setMaxLength(120));
			this.options = options;
		}

		protected void execute(SlashCommandEvent event) {
			Integer groupId = event.optInteger("master_group");
			String masterId = bot.getDBUtil().group.getMaster(groupId);
			if (masterId == null || !masterId.equals(event.getGuild().getId())) {
				createError(event, path+".no_group", "Group ID: `"+groupId.toString()+"`");
				return;
			}

			String oldName = bot.getDBUtil().group.getName(groupId);
			String newName = event.optString("name");

			bot.getDBUtil().group.rename(groupId, newName);
			bot.getLogListener().onGroupRename(event, oldName, groupId, newName);

			MessageEmbed embed = bot.getEmbedUtil().getEmbed(event)
				.setColor(Constants.COLOR_SUCCESS)
				.setDescription(lu.getText(event, path+".done").replace("{old_name}", oldName).replace("{new_name}", newName)
					.replace("{group_id}", groupId.toString()))
				.build();
			createReplyEmbed(event, embed);
		}
		
	}

}


