package dev.fileeditor.votl.commands.guild;

import java.util.Collections;
import java.util.concurrent.TimeUnit;

import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.entities.MessageEmbed;
import net.dv8tion.jda.api.entities.channel.ChannelType;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import net.dv8tion.jda.api.entities.channel.middleman.GuildChannel;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import net.dv8tion.jda.api.events.interaction.component.EntitySelectInteractionEvent;
import net.dv8tion.jda.api.events.interaction.component.StringSelectInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import net.dv8tion.jda.api.interactions.components.ActionRow;
import net.dv8tion.jda.api.interactions.components.buttons.Button;
import net.dv8tion.jda.api.interactions.components.buttons.ButtonStyle;
import net.dv8tion.jda.api.interactions.components.selections.EntitySelectMenu;
import net.dv8tion.jda.api.interactions.components.selections.EntitySelectMenu.SelectTarget;
import net.dv8tion.jda.api.interactions.components.selections.StringSelectMenu;

import com.jagrosh.jdautilities.commons.waiter.EventWaiter;

import dev.fileeditor.votl.App;
import dev.fileeditor.votl.commands.CommandBase;
import dev.fileeditor.votl.objects.CmdAccessLevel;
import dev.fileeditor.votl.objects.CmdModule;
import dev.fileeditor.votl.objects.command.SlashCommand;
import dev.fileeditor.votl.objects.command.SlashCommandEvent;
import dev.fileeditor.votl.objects.constants.CmdCategory;
import dev.fileeditor.votl.objects.constants.Constants;
import dev.fileeditor.votl.utils.exception.CheckException;

public class LogCmd extends CommandBase {

	private static EventWaiter waiter;
	
	public LogCmd(App bot, EventWaiter waiter) {
		super(bot);
		this.name = "log";
		this.path = "bot.guild.log";
		this.children = new SlashCommand[]{new Setup(bot), new Manage(bot)};
		this.botPermissions = new Permission[]{Permission.MANAGE_CHANNEL, Permission.VIEW_AUDIT_LOGS, Permission.MESSAGE_SEND};
		this.category = CmdCategory.GUILD;
		this.module = CmdModule.MODERATION;
		this.accessLevel = CmdAccessLevel.ADMIN;
		this.mustSetup = true;
		LogCmd.waiter = waiter;
	}

	@Override
	protected void execute(SlashCommandEvent event) {

	}

	private class Setup extends CommandBase {

		public Setup(App bot) {
			super(bot);
			this.name = "setup";
			this.path = "bot.guild.log.setup";
			this.options = Collections.singletonList(
				new OptionData(OptionType.CHANNEL, "channel", lu.getText(path+".option_channel"), true)
			);
		}

		@Override
		protected void execute(SlashCommandEvent event) {
			GuildChannel channel = null;
			try {
				channel = event.optGuildChannel("channel");
			} catch (IllegalStateException ex) {}
			if (channel == null || channel.getType() != ChannelType.TEXT) {
				createError(event, path+".no_channel");
				return;
			}
			try {
				bot.getCheckUtil().hasPermissions(event, event.getGuild(), event.getMember(), true, channel,
					new Permission[]{Permission.MESSAGE_SEND, Permission.MESSAGE_EMBED_LINKS});
			} catch (CheckException ex) {
				createReply(event, ex.getCreateData());
				return;
			}

			TextChannel tc = (TextChannel) channel;
			EmbedBuilder builder = bot.getEmbedUtil().getEmbed(event)
				.setColor(Constants.COLOR_SUCCESS);
			
			bot.getDBUtil().guild.setModLogChannel(event.getGuild().getId(), tc.getId());
			bot.getDBUtil().guild.setGroupLogChannel(event.getGuild().getId(), tc.getId());
			bot.getDBUtil().guild.setVerifyLogChannel(event.getGuild().getId(), tc.getId());
			tc.sendMessageEmbeds(builder.setDescription(lu.getLocalized(event.getGuildLocale(), path+".as_log")).build()).queue();
			createReplyEmbed(event, builder.setDescription(lu.getText(event, path+".done").replace("{channel}", tc.getAsMention())).build());
		}

	}

	private class Manage extends CommandBase {

		public Manage(App bot) {
			super(bot);
			this.name = "manage";
			this.path = "bot.guild.log.manage";
		}

		@Override
		protected void execute(SlashCommandEvent event) {
			event.deferReply(true).queue();

			EmbedBuilder builder = bot.getEmbedUtil().getEmbed(event)
				.setTitle(lu.getText(event, path+".title"));

			String guildId = event.getGuild().getId();
			String modChannelId = bot.getDBUtil().guild.getModLogChannel(guildId);
			String groupChannelId = bot.getDBUtil().guild.getGroupLogChannel(guildId);
			String verifyChannelId = bot.getDBUtil().guild.getVerifyLogChannel(guildId);
			if (modChannelId == null || groupChannelId == null || verifyChannelId == null) { 
				builder.setDescription(lu.getText(event, path+".none"));
				editHookEmbed(event, builder.build());
				return;
			}

			TextChannel modtc = event.getJDA().getTextChannelById(modChannelId);
			TextChannel grouptc = event.getJDA().getTextChannelById(groupChannelId);
			TextChannel verifytc = event.getJDA().getTextChannelById(verifyChannelId);
			builder.appendDescription(lu.getText(event, "bot.guild.log.types.moderation")+" - "+(modtc != null ? modtc.getAsMention() : "*not found*")+"\n")
				.appendDescription(lu.getText(event, "bot.guild.log.types.group")+" - "+(grouptc != null ? grouptc.getAsMention() : "*not found*")+"\n")
				.appendDescription(lu.getText(event, "bot.guild.log.types.verify")+" - "+(verifytc != null ? grouptc.getAsMention() : "*not found*"));

			ActionRow buttons = ActionRow.of(
				Button.of(ButtonStyle.PRIMARY, "button:change", lu.getText(event, path+".button_change")),
				Button.of(ButtonStyle.DANGER, "button:remove", lu.getText(event, path+".button_remove"))
			);
			event.getHook().editOriginalEmbeds(builder.build()).setComponents(buttons).queue(msg -> {
				waiter.waitForEvent(
					ButtonInteractionEvent.class,
					e -> msg.getId().equals(e.getMessageId()) && (e.getComponentId().equals("button:change") || e.getComponentId().equals("button:remove")),
					buttonEvent -> buttonPressedAction(buttonEvent, event, msg.getId()),
					30,
					TimeUnit.SECONDS,
					() -> event.getHook().editOriginalComponents(buttons.asDisabled()).queue()
				);
			});
		}

		private void buttonPressedAction(ButtonInteractionEvent interaction, SlashCommandEvent event, String msgId) {
			interaction.deferEdit().queue();
			String guildId = event.getGuild().getId();
			String buttonPressed = interaction.getComponentId();

			if (buttonPressed.equals("button:remove")) {
				bot.getDBUtil().guild.setModLogChannel(guildId, "NULL");
				bot.getDBUtil().guild.setGroupLogChannel(guildId, "NULL");
				bot.getDBUtil().guild.setVerifyLogChannel(guildId, "NULL");
				MessageEmbed embed = bot.getEmbedUtil().getEmbed(event)
					.setDescription(lu.getText(event, path+".removed"))
					.build();
				event.getHook().editOriginalEmbeds(embed).setComponents().queue();
			} else {
				StringSelectMenu menu = StringSelectMenu.create("menu:log_type")
					.setPlaceholder(lu.getText(event, path+".menu_type"))
					.addOption(lu.getText(event, "bot.guild.log.types.moderation"), "moderation")
					.addOption(lu.getText(event, "bot.guild.log.types.group"), "group")
					.addOption(lu.getText(event, "bot.guild.log.types.verify"), "verify")
					.build();
					event.getHook().editOriginalComponents(ActionRow.of(menu)).queue();

				waiter.waitForEvent(
					StringSelectInteractionEvent.class,
					e -> msgId.equals(e.getMessageId()) && e.getComponentId().equals("menu:log_type"),
					selectEvent -> typeSelected(selectEvent, event, msgId),
					30,
					TimeUnit.SECONDS,
					() -> event.getHook().editOriginalComponents(ActionRow.of(
						menu.createCopy().setPlaceholder(lu.getText(event, path+".timed_out")).setDisabled(true).build())
					).queue()
				);
			}
		}

		private void typeSelected(StringSelectInteractionEvent interaction, SlashCommandEvent event, String msgId) {
			interaction.deferEdit().queue();
			String selectedType = interaction.getComponentId();

			MessageEmbed embed = bot.getEmbedUtil().getEmbed(event)
				.setDescription(lu.getText(event, path+".select_channel").replace("{type}", lu.getText(event,"bot.guild.log.types."+selectedType)))
				.build();
			
			EntitySelectMenu menu = EntitySelectMenu.create("menu:channel", SelectTarget.CHANNEL)
				.setPlaceholder(lu.getText(event, path+".menu_channel"))
				.setRequiredRange(1, 1)
				.build();
			event.getHook().editOriginalEmbeds(embed).setActionRow(menu).queue();
			
			waiter.waitForEvent(
				EntitySelectInteractionEvent.class,
				e -> msgId.equals(e.getMessageId()) && e.getComponentId().equals("menu:channel"),
				selectEvent -> channelSelected(selectEvent, event, msgId, selectedType),
				30,
				TimeUnit.SECONDS,
				() -> event.getHook().editOriginalComponents(ActionRow.of(
					menu.createCopy().setPlaceholder(lu.getText(event, path+".timed_out")).setDisabled(true).build())
				).queue()
			);
		}

		private void channelSelected(EntitySelectInteractionEvent interaction, SlashCommandEvent event, String msgId, String selectedType) {
			interaction.deferEdit().queue();

			GuildChannel channel = null;
			try {
				channel = interaction.getMentions().getChannels().get(0);
			} catch (IllegalStateException ex) {}
			
			if (channel == null || !channel.getType().equals(ChannelType.TEXT)) {
				event.getHook().editOriginalEmbeds(bot.getEmbedUtil().getError(event, path+".no_channel")).setComponents().queue();
				return;
			}
			try {
				bot.getCheckUtil().hasPermissions(event, event.getGuild(), event.getMember(), true, channel,
					new Permission[]{Permission.MESSAGE_SEND, Permission.MESSAGE_EMBED_LINKS});
			} catch (CheckException ex) {
				event.getHook().editOriginal(ex.getEditData()).queue();
			}

			TextChannel newTc = (TextChannel) channel;
			EmbedBuilder builder = bot.getEmbedUtil().getEmbed(event)
				.setColor(Constants.COLOR_SUCCESS);
			
			if (selectedType.equals("moderation")) {
				bot.getDBUtil().guild.setModLogChannel(event.getGuild().getId(), newTc.getId());
			} else if (selectedType.equals("group")) {
				bot.getDBUtil().guild.setGroupLogChannel(event.getGuild().getId(), newTc.getId());
			} else if (selectedType.equals("verify")) {
				bot.getDBUtil().guild.setVerifyLogChannel(event.getGuild().getId(), newTc.getId());
			}
			newTc.sendMessageEmbeds(builder.setDescription(lu.getLocalized(event.getGuildLocale(), path+".as_log")
				.replace("{type}", lu.getLocalized(event.getGuildLocale(),"bot.guild.log.types."+selectedType))).build()).queue();
			event.getHook().editOriginalEmbeds(builder.setDescription(lu.getText(event, path+".done")
				.replace("{channel}", newTc.getAsMention())).build()).setComponents().queue();
		}

	}

}
