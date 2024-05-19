package dev.fileeditor.votl.commands.moderation;

import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;

import dev.fileeditor.votl.App;
import dev.fileeditor.votl.base.command.CooldownScope;
import dev.fileeditor.votl.base.command.SlashCommand;
import dev.fileeditor.votl.base.command.SlashCommandEvent;
import dev.fileeditor.votl.base.waiter.EventWaiter;
import dev.fileeditor.votl.commands.CommandBase;
import dev.fileeditor.votl.objects.CmdAccessLevel;
import dev.fileeditor.votl.objects.CmdModule;
import dev.fileeditor.votl.objects.Emote;
import dev.fileeditor.votl.objects.constants.CmdCategory;
import dev.fileeditor.votl.objects.constants.Constants;

import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.MessageEmbed;
import net.dv8tion.jda.api.events.interaction.component.StringSelectInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import net.dv8tion.jda.api.interactions.components.ActionRow;
import net.dv8tion.jda.api.interactions.components.selections.SelectOption;
import net.dv8tion.jda.api.interactions.components.selections.StringSelectMenu;

public class GroupCmd extends CommandBase {
	
	private static EventWaiter waiter;

	public GroupCmd(App bot, EventWaiter waiter) {
		super(bot);
		this.name = "group";
		this.path = "bot.moderation.group";
		this.children = new SlashCommand[]{new Create(bot), new Delete(bot), new Remove(bot), new GenerateInvite(bot), new Join(bot), new Leave(bot), new Rename(bot), new Manage(bot), new View(bot)};
		this.category = CmdCategory.MODERATION;
		this.module = CmdModule.MODERATION;
		this.accessLevel = CmdAccessLevel.OPERATOR;
		GroupCmd.waiter = waiter;
	}

	@Override
	protected void execute(SlashCommandEvent event) {}

	private class Create extends SlashCommand {

		public Create(App bot) {
			this.bot = bot;
			this.lu = bot.getLocaleUtil();
			this.name = "create";
			this.path = "bot.moderation.group.create";
			this.options = List.of(
				new OptionData(OptionType.STRING, "name", lu.getText(path+".name.help"), true).setMaxLength(120),
				new OptionData(OptionType.STRING, "appeal_server", lu.getText(path+".appeal_server.help")).setRequiredLength(12, 20)
			);
			this.cooldown = 30;
			this.cooldownScope = CooldownScope.GUILD;
		}

		@Override
		protected void execute(SlashCommandEvent event) {
			event.deferReply().queue();
			long guildId = event.getGuild().getIdLong();
			if (bot.getDBUtil().group.getOwnedGroups(guildId).size() >= 3) {
				editError(event, path+".max_amount");
				return;
			}

			String groupName = event.optString("name");

			long appealGuildId = 0L;
			if (event.hasOption("appeal_server")) {
				try {
					appealGuildId = Long.parseLong(event.optString("appeal_server"));
				} catch (NumberFormatException ex) {
					editError(event, "errors.error", ex.getMessage());
					return;
				}
				if (appealGuildId != 0L && event.getJDA().getGuildById(appealGuildId) == null) {
					editError(event, "errors.error", "Unknown appeal server ID.\nReceived: "+appealGuildId);
					return;
				}
			}
			
			bot.getDBUtil().group.create(guildId, groupName, appealGuildId);
			Integer groupId = bot.getDBUtil().group.getIncrement();
			bot.getLogger().group.onCreation(event, groupId, groupName);

			editHookEmbed(event, bot.getEmbedUtil().getEmbed(Constants.COLOR_SUCCESS)
				.setDescription(
					lu.getText(event, path+".done").replace("{group_name}", groupName).replace("{group_id}", groupId.toString())
					.replace("{is_shared}", Emote.CROSS_C.getEmote())
				)
				.build()
			);
		}

	}

	private class Delete extends SlashCommand {

		public Delete(App bot) {
			this.bot = bot;
			this.lu = bot.getLocaleUtil();
			this.name = "delete";
			this.path = "bot.moderation.group.delete";
			this.options = List.of(
				new OptionData(OptionType.INTEGER, "group_owned", lu.getText(path+".group_owned.help"), true, true).setMinValue(1)
			);
			this.cooldown = 30;
			this.cooldownScope = CooldownScope.GUILD;
		}

		@Override
		protected void execute(SlashCommandEvent event) {
			event.deferReply().queue();
			Integer groupId = event.optInteger("group_owned");
			Long ownerId = bot.getDBUtil().group.getOwner(groupId);
			if (ownerId == null) {
				editError(event, path+".no_group", "Group ID: `%d`".formatted(groupId));
				return;
			}
			if (event.getGuild().getIdLong() != ownerId) {
				editError(event, path+".not_owned", "Group ID: `%d`".formatted(groupId));
				return;
			}

			String groupName = bot.getDBUtil().group.getName(groupId);

			bot.getDBUtil().group.deleteGroup(groupId);
			bot.getLogger().group.onDeletion(event, groupId, groupName);

			editHookEmbed(event, bot.getEmbedUtil().getEmbed(Constants.COLOR_SUCCESS)
				.setDescription(
					lu.getText(event, path+".done").replace("{group_name}", groupName).replace("{group_id}", groupId.toString())
				)
				.build()
			);
		}

	}

	private class Remove extends SlashCommand {

		public Remove(App bot) {
			this.bot = bot;
			this.lu = bot.getLocaleUtil();
			this.name = "remove";
			this.path = "bot.moderation.group.remove";
			this.options = List.of(
				new OptionData(OptionType.INTEGER, "group_owned", lu.getText(path+".group_owned.help"), true, true).setMinValue(0)
			);
		}

		@Override
		protected void execute(SlashCommandEvent event) {
			event.deferReply(true).queue();
			Integer groupId = event.optInteger("group_owned");
			Long ownerId = bot.getDBUtil().group.getOwner(groupId);
			if (ownerId == null) {
				editError(event, path+".no_group", "Group ID: `%d`".formatted(groupId));
				return;
			}
			if (event.getGuild().getIdLong() != ownerId) {
				editError(event, path+".not_owned", "Group ID: `%d`".formatted(groupId));
				return;
			}

			List<Guild> guilds = bot.getDBUtil().group.getGroupMembers(groupId).stream()
				.map(event.getJDA()::getGuildById)
				.filter(Objects::nonNull)
				.toList();
			if (guilds.isEmpty()) {
				editError(event, path+".no_guilds");
				return;
			}

			String groupName = bot.getDBUtil().group.getName(groupId);
			MessageEmbed embed = bot.getEmbedUtil().getEmbed()
				.setTitle(lu.getText(event, path+".embed_title"))
				.setDescription(lu.getText(event, path+".embed_value").replace("{group_name}", groupName))
				.build();
			
			StringSelectMenu menu = StringSelectMenu.create("menu:remove-guild")
				.setPlaceholder("Select")
				.setMaxValues(1)
				.addOptions(guilds.stream()
					.map(guild -> SelectOption.of("%s (%s)".formatted(guild.getName(), guild.getId()), guild.getId()))
					.limit(25)
					.toList())
				.build();
			event.getHook().editOriginalEmbeds(embed).setActionRow(menu).queue(msg -> waiter.waitForEvent(
				StringSelectInteractionEvent.class,
				e -> e.getComponentId().equals("menu:remove-guild") && e.getMessageId().equals(msg.getId()),
				actionMenu -> {
					long targetId = Long.parseLong(actionMenu.getSelectedOptions().get(0).getValue());
					Guild targetGuild = event.getJDA().getGuildById(targetId);

					bot.getDBUtil().group.remove(groupId, targetId);
					if (targetGuild != null)
						bot.getLogger().group.onGuildRemoved(event, targetGuild, groupId, groupName);

					event.getHook().editOriginalEmbeds(bot.getEmbedUtil().getEmbed(Constants.COLOR_SUCCESS)
						.setDescription(lu.getText(event, path+".done").replace("{guild_name}", Optional.of(targetGuild.getName()).orElse("*Unknown*")).replace("{group_name}", groupName))
						.build()
					).setComponents().queue();
				},
				30,
				TimeUnit.SECONDS,
				() -> {
					event.getHook().editOriginalComponents(
						ActionRow.of(menu.createCopy().setPlaceholder(lu.getText(event, "errors.timed_out")).setDisabled(true).build())
					).queue();
				}
			));
		}

	}

	private class GenerateInvite extends SlashCommand {
		public GenerateInvite(App bot) {
			this.bot = bot;
			this.lu = bot.getLocaleUtil();
			this.name = "generateinvite";
			this.path = "bot.moderation.group.generateinvite";
			this.options = List.of(
				new OptionData(OptionType.INTEGER, "group_owned", lu.getText(path+".group_owned.help"), true, true).setMinValue(0)
			);
		}

		@Override
		protected void execute(SlashCommandEvent event) {
			Integer groupId = event.optInteger("group_owned");
			Long ownerId = bot.getDBUtil().group.getOwner(groupId);
			if (ownerId == null) {
				createError(event, path+".no_group", "Group ID: `%d`".formatted(groupId));
				return;
			}
			if (event.getGuild().getIdLong() != ownerId) {
				createError(event, path+".not_owned", "Group ID: `%d`".formatted(groupId));
				return;
			}

			int newInvite = ThreadLocalRandom.current().nextInt(100_000, 1_000_000); // 100000 - 999999

			bot.getDBUtil().group.setInvite(groupId, newInvite);

			String groupName = bot.getDBUtil().group.getName(groupId);
			createReplyEmbed(event, bot.getEmbedUtil().getEmbed(Constants.COLOR_SUCCESS)
				.setDescription(lu.getText(event, path+".done").formatted(groupName, newInvite))
				.build()
			);
		}
	}

	private class Rename extends SlashCommand {

		public Rename(App bot) {
			this.bot = bot;
			this.lu = bot.getLocaleUtil();
			this.name = "rename";
			this.path = "bot.moderation.group.rename";
			this.options = List.of(
				new OptionData(OptionType.INTEGER, "group_owned", lu.getText(path+".group_owned.help"), true, true).setMinValue(0),
				new OptionData(OptionType.STRING, "name", lu.getText(path+".name.help"), true).setMaxLength(120)
			);
		}

		@Override
		protected void execute(SlashCommandEvent event) {
			Integer groupId = event.optInteger("group_owned");
			Long ownerId = bot.getDBUtil().group.getOwner(groupId);
			if (ownerId == null) {
				createError(event, path+".no_group", "Group ID: `%d`".formatted(groupId));
				return;
			}
			if (event.getGuild().getIdLong() != ownerId) {
				createError(event, path+".not_owned", "Group ID: `%d`".formatted(groupId));
				return;
			}

			String oldName = bot.getDBUtil().group.getName(groupId);
			String newName = event.optString("name");

			bot.getDBUtil().group.rename(groupId, newName);
			bot.getLogger().group.onRenamed(event, oldName, groupId, newName);

			createReplyEmbed(event, bot.getEmbedUtil().getEmbed(Constants.COLOR_SUCCESS)
				.setDescription(
					lu.getText(event, path+".done").replace("{old_name}", oldName).replace("{new_name}", newName)
					.replace("{group_id}", groupId.toString())
				)
				.build()
			);
		}

	}

	private class Manage extends SlashCommand {

		public Manage(App bot) {
			this.bot = bot;
			this.lu = bot.getLocaleUtil();
			this.name = "manage";
			this.path = "bot.moderation.group.manage";
			this.options = List.of(
				new OptionData(OptionType.INTEGER, "group_owned", lu.getText(path+".group_owned.help"), true, true).setMinValue(0),
				new OptionData(OptionType.BOOLEAN, "manage", lu.getText(path+".manage.help"), true)
			);
		}

		@Override
		protected void execute(SlashCommandEvent event) {
			event.deferReply(true).queue();
			Integer groupId = event.optInteger("group_owned");
			Long ownerId = bot.getDBUtil().group.getOwner(groupId);
			if (ownerId == null) {
				editError(event, path+".no_group", "Group ID: `%d`".formatted(groupId));
				return;
			}
			if (event.getGuild().getIdLong() != ownerId) {
				editError(event, path+".not_owned", "Group ID: `%d`".formatted(groupId));
				return;
			}

			boolean canManage = event.optBoolean("manage", false);

			List<Guild> guilds = bot.getDBUtil().group.getGroupMembers(groupId).stream()
				.map(event.getJDA()::getGuildById)
				.filter(Objects::nonNull)
				.toList();
			if (guilds.isEmpty()) {
				editError(event, path+".no_guilds");
				return;
			}

			String groupName = bot.getDBUtil().group.getName(groupId);
			MessageEmbed embed = bot.getEmbedUtil().getEmbed()
				.setTitle(lu.getText(event, path+".embed_title"))
				.setDescription(lu.getText(event, path+".embed_value").replace("{group_name}", groupName))
				.build();
			
			StringSelectMenu menu = StringSelectMenu.create("menu:select-guild")
				.setPlaceholder("Select")
				.setMaxValues(1)
				.addOptions(guilds.stream()
					.map(guild -> SelectOption.of("%s (%s)".formatted(guild.getName(), guild.getId()), guild.getId()))
					.limit(25)
					.toList())
				.build();
			event.getHook().editOriginalEmbeds(embed).setActionRow(menu).queue(msg -> waiter.waitForEvent(
				StringSelectInteractionEvent.class,
				e -> e.getComponentId().equals("menu:select-guild") && e.getMessageId().equals(msg.getId()),
				actionMenu -> {
					long targetId = Long.parseLong(actionMenu.getSelectedOptions().get(0).getValue());
					Guild targetGuild = event.getJDA().getGuildById(targetId);

					bot.getDBUtil().group.setManage(groupId, targetId, canManage);

					event.getHook().editOriginalEmbeds(bot.getEmbedUtil().getEmbed(Constants.COLOR_SUCCESS)
						.setDescription(
							lu.getText(event, path+".done").replace("{guild_name}", targetGuild.getName()).replace("{group_name}", groupName)
							.replace("{manage}", Boolean.toString(canManage))
						)
						.build()
					).setComponents().queue();
				},
				30,
				TimeUnit.SECONDS,
				() -> {
					event.getHook().editOriginalComponents(
						ActionRow.of(menu.createCopy().setPlaceholder(lu.getText(event, "errors.timed_out")).setDisabled(true).build())
					).queue();
				}
			));
		}
	}

	private class Join extends SlashCommand {
		public Join(App bot) {
			this.bot = bot;
			this.lu = bot.getLocaleUtil();
			this.name = "join";
			this.path = "bot.moderation.group.join";
			this.options = List.of(
				new OptionData(OptionType.INTEGER, "invite", lu.getText(path+".invite.help"), true)
			);
			this.cooldownScope = CooldownScope.GUILD;
			this.cooldown = 30;
		}

		@Override
		protected void execute(SlashCommandEvent event) {
			event.deferReply(true).queue();
			Integer invite = event.optInteger("invite");
			Integer groupId = bot.getDBUtil().group.getGroupByInvite(invite);
			if (groupId == null) {
				editError(event, path+".no_group");
				return;
			}

			Long ownerId = bot.getDBUtil().group.getOwner(groupId);
			if (event.getGuild().getIdLong() == ownerId) {
				editError(event, path+".failed_join", "This server is this Group's owner.\nGroup ID: `%s`".formatted(groupId));
				return;
			}
			if (bot.getDBUtil().group.isMember(groupId, event.getGuild().getIdLong())) {
				editError(event, path+".is_member", "Group ID: `%s`".formatted(groupId));
				return;
			}

			String groupName = bot.getDBUtil().group.getName(groupId);

			bot.getDBUtil().group.add(groupId, event.getGuild().getIdLong(), false);
			bot.getLogger().group.onGuildJoined(event, groupId, groupName);

			editHookEmbed(event, bot.getEmbedUtil().getEmbed(Constants.COLOR_SUCCESS)
				.setDescription(lu.getText(event, path+".done").formatted(groupName))
				.build()
			);
		}		
	}

	private class Leave extends SlashCommand {
		public Leave(App bot) {
			this.bot = bot;
			this.lu = bot.getLocaleUtil();
			this.name = "leave";
			this.path = "bot.moderation.group.leave";
			this.options = List.of(
				new OptionData(OptionType.INTEGER, "group_joined", lu.getText(path+".group_joined.help"), true, true).setMinValue(0)
			);
		}

		@Override
		protected void execute(SlashCommandEvent event) {
			event.deferReply(true).queue();
			Integer groupId = event.optInteger("group_joined");
			Long ownerId = bot.getDBUtil().group.getOwner(groupId);
			if (ownerId == null || !bot.getDBUtil().group.isMember(groupId, event.getGuild().getIdLong())) {
				createError(event, path+".no_group", "Group ID: `%s`".formatted(groupId));
				return;
			}

			String groupName = bot.getDBUtil().group.getName(groupId);

			bot.getDBUtil().group.remove(groupId, event.getGuild().getIdLong());
			bot.getLogger().group.onGuildLeft(event, groupId, groupName);

			editHookEmbed(event, bot.getEmbedUtil().getEmbed(Constants.COLOR_SUCCESS)
				.setDescription(lu.getText(event, path+".done").formatted(groupName))
				.build()
			);
		}
	}

	private class View extends SlashCommand {

		public View(App bot) {
			this.bot = bot;
			this.lu = bot.getLocaleUtil();
			this.name = "view";
			this.path = "bot.moderation.group.view";
			this.options = List.of(
				new OptionData(OptionType.INTEGER, "group_owned", lu.getText(path+".group_owned.help"), false, true).setMinValue(0),
				new OptionData(OptionType.INTEGER, "group_joined", lu.getText(path+".group_joined.help"), false, true).setMinValue(0)
			);
		}

		@Override
		protected void execute(SlashCommandEvent event) {
			long guildId = event.getGuild().getIdLong();
			if (event.hasOption("group_owned")) {
				// View owned Group information - name, every guild info (name, ID, member count)
				Integer groupId = event.optInteger("group_owned");
				Long ownerId = bot.getDBUtil().group.getOwner(groupId);
				if (ownerId == null) {
					createError(event, path+".no_group", "Group ID: `%d`".formatted(groupId));
					return;
				}
				if (event.getGuild().getIdLong() != ownerId) {
					createError(event, path+".not_owned", "Group ID: `%d`".formatted(groupId));
					return;
				}

				String groupName = bot.getDBUtil().group.getName(groupId);
				List<Long> memberIds = bot.getDBUtil().group.getGroupMembers(groupId);
				int groupSize = memberIds.size();
				String invite = Optional.ofNullable(bot.getDBUtil().group.getInvite(groupId)).map(o -> "||`"+o+"`||").orElse("-");

				EmbedBuilder builder = bot.getEmbedUtil().getEmbed()
					.setAuthor(lu.getText(event, path+".embed_title").replace("{group_name}", groupName).replace("{group_id}", groupId.toString()))
					.setDescription(
						lu.getText(event, path+".embed_value")
							.replace("{guild_name}", event.getGuild().getName())
							.replace("{guild_id}", String.valueOf(ownerId))
							.replace("{size}", Integer.toString(groupSize))
					)
					.addField(lu.getText(event, path+".embed_invite"), invite, false);
				
				if (groupSize > 0) {
					String fieldLabel = lu.getText(event, path+".embed_guilds");
					StringBuilder stringBuilder = new StringBuilder();
					String format = "%s | %s | `%s`";
					for (Long memberId : memberIds) {
						Guild guild = event.getJDA().getGuildById(memberId);
						if (guild == null) continue;
	
						String line = format.formatted(guild.getName(), guild.getMemberCount(), guild.getId());
						if (stringBuilder.length() + line.length() + 2 > 1000) {
							builder.addField(fieldLabel, stringBuilder.toString(), false);
							stringBuilder.setLength(0);
							stringBuilder.append(line).append("\n");
							fieldLabel = "";
						} else {
							stringBuilder.append(line).append("\n");
						}
					}
					builder.addField(fieldLabel, stringBuilder.toString(), false);
				}
				createReplyEmbed(event, builder.build());
			} else if (event.hasOption("group_joined")) {
				// View joined Group information - name, master name/ID, guild count
				Integer groupId = event.optInteger("group_joined");
				Long ownerId = bot.getDBUtil().group.getOwner(groupId);
				if (ownerId == null || !bot.getDBUtil().group.isMember(groupId, guildId)) {
					createError(event, path+".no_group", "Group ID: `%s`".formatted(groupId));
					return;
				}
				
				String groupName = bot.getDBUtil().group.getName(groupId);
				String masterName = event.getJDA().getGuildById(ownerId).getName();
				int groupSize = bot.getDBUtil().group.countMembers(groupId);

				EmbedBuilder builder = bot.getEmbedUtil().getEmbed()
					.setAuthor(lu.getText(event, "logger.groups.title").formatted(groupName, groupId))
					.setDescription(lu.getText(event, path+".embed_value")
						.replace("{guild_name}", masterName)
						.replace("{guild_id}", ownerId.toString())
						.replace("{size}", Integer.toString(groupSize))
					);
				createReplyEmbed(event, builder.build());
			} else {
				// No options provided - reply with all groups that this guild is connected
				List<Integer> ownedGroups = bot.getDBUtil().group.getOwnedGroups(guildId);
				List<Integer> joinedGroupIds = bot.getDBUtil().group.getGuildGroups(guildId);

				EmbedBuilder builder = bot.getEmbedUtil().getEmbed()
					.setDescription("Group name | #ID");
				
				String fieldLabel = lu.getText(event, path+".embed_owned");
				if (ownedGroups.isEmpty()) {
					builder.addField(fieldLabel, lu.getText(event, path+".none"), false);
				} else {
					StringBuilder stringBuilder = new StringBuilder();
					for (Integer groupId : ownedGroups) {
						stringBuilder.append("%s | #%s\n".formatted(bot.getDBUtil().group.getName(groupId), groupId));
					}
					builder.addField(fieldLabel, stringBuilder.toString(), false);
				}

				fieldLabel = lu.getText(event, path+".embed_member");
				if (joinedGroupIds.isEmpty()) {
					builder.addField(fieldLabel, lu.getText(event, path+".none"), false);
				} else {
					StringBuilder stringBuilder = new StringBuilder();
					for (Integer groupId : joinedGroupIds) {
						stringBuilder.append("%s | #%s\n".formatted(bot.getDBUtil().group.getName(groupId), groupId));
					}
					builder.addField(fieldLabel, stringBuilder.toString(), false);
				}

				createReplyEmbed(event, builder.build());
			}
		}

	}

}
