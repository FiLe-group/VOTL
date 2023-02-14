package votl.commands.guild;

import java.util.Collections;
import java.util.Objects;
import java.util.concurrent.TimeUnit;

import votl.App;
import votl.commands.CommandBase;
import votl.objects.CmdAccessLevel;
import votl.objects.CmdModule;
import votl.objects.command.SlashCommand;
import votl.objects.command.SlashCommandEvent;
import votl.objects.constants.CmdCategory;
import votl.objects.constants.Constants;
import votl.utils.exception.CheckException;

import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.entities.channel.ChannelType;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import net.dv8tion.jda.api.entities.channel.middleman.GuildChannel;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import net.dv8tion.jda.api.events.interaction.component.EntitySelectInteractionEvent;
import net.dv8tion.jda.api.interactions.InteractionHook;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import net.dv8tion.jda.api.interactions.components.ActionRow;
import net.dv8tion.jda.api.interactions.components.buttons.Button;
import net.dv8tion.jda.api.interactions.components.buttons.ButtonStyle;
import net.dv8tion.jda.api.interactions.components.selections.EntitySelectMenu;
import net.dv8tion.jda.api.interactions.components.selections.EntitySelectMenu.SelectTarget;

import com.jagrosh.jdautilities.commons.waiter.EventWaiter;
import com.jagrosh.jdautilities.doc.standard.CommandInfo;

@CommandInfo
(
	name = "log",
	description = "Manage guild's log settings",
	usage = "/log setup|remove|manage"
)
public class LogCmd extends CommandBase {

	private static EventWaiter waiter;
	
	public LogCmd(App bot, EventWaiter waiter) {
		super(bot);
		this.name = "log";
		this.path = "bot.guild.log";
		this.children = new SlashCommand[]{new Setup(bot), new Manage(bot)};
		this.botPermissions = new Permission[]{Permission.MANAGE_CHANNEL, Permission.VIEW_AUDIT_LOGS};
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
			} catch (IllegalStateException ex) {
				// ha ha, exception got catched
			}
			if (channel == null || channel.getType() != ChannelType.TEXT) {
				createError(event, path+".no_channel");
				return;
			}
			try {
				bot.getCheckUtil().hasPermissions(event, true, channel, new Permission[]{Permission.MESSAGE_SEND, Permission.MESSAGE_EMBED_LINKS});
			} catch (CheckException ex) {
				createReply(event, ex.getCreateData());
				return;
			}

			TextChannel tc = (TextChannel) channel;
			EmbedBuilder builder = bot.getEmbedUtil().getEmbed(event)
				.setColor(Constants.COLOR_SUCCESS);
			
			bot.getDBUtil().guild.setLogChannel(event.getGuild().getId(), tc.getId());
			tc.sendMessageEmbeds(builder.setDescription(lu.getText(event, path+".as_log")).build()).queue();
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
			InteractionHook hook = event.getHook();

			EmbedBuilder builder = bot.getEmbedUtil().getEmbed(event)
				.setTitle(lu.getText(event, path+".title"));

			String guildId = Objects.requireNonNull(event.getGuild()).getId();
			String channelId = bot.getDBUtil().guild.getLogChannel(guildId);
			if (channelId == null) {
				builder.setDescription(lu.getText(event, path+".none"));
				editHookEmbed(event, builder.build());
			} else {
				TextChannel tc = event.getJDA().getTextChannelById(channelId);
				builder.setDescription(lu.getText(event, "bot.guild.log.types.ban")+" - "+(tc != null ? tc.getAsMention() : "*not found*"));

				ActionRow buttons = ActionRow.of(
					Button.of(ButtonStyle.PRIMARY, "button:change", lu.getText(event, path+".button_change")),
					Button.of(ButtonStyle.DANGER, "button:remove", lu.getText(event, path+".button_remove"))
				);
				hook.editOriginalEmbeds(builder.build()).setComponents(buttons).queue(msg1 -> {
					waiter.waitForEvent(
						ButtonInteractionEvent.class,
						e -> msg1.getId().equals(e.getMessageId()) && (e.getComponentId().equals("button:change") || e.getComponentId().equals("button:remove")),
						actionButton -> {

							actionButton.deferEdit().queue();
							if (actionButton.getComponentId().equals("button:change")) {
								EmbedBuilder builder2 = bot.getEmbedUtil().getEmbed(event)
									.setDescription(lu.getText(event, path+".select_channel"));
								
								EntitySelectMenu menu = EntitySelectMenu.create("menu:channel", SelectTarget.CHANNEL)
									.setPlaceholder(lu.getText(event, path+".menu_channel"))
									.setRequiredRange(1, 1)
									.build();
								hook.editOriginalEmbeds(builder2.build()).setActionRow(menu).queue();
								
								waiter.waitForEvent(
									EntitySelectInteractionEvent.class,
									e -> msg1.getId().equals(e.getMessageId()) && e.getComponentId().equals("menu:channel"),
									actionMenu -> {

										actionMenu.deferEdit().queue();
										GuildChannel channel = null;
										try {
											channel = actionMenu.getMentions().getChannels().get(0);
										} catch (IllegalStateException ex) {
											// catched
										}
										if (channel == null || channel.getType() != ChannelType.TEXT) {
											hook.editOriginalEmbeds(bot.getEmbedUtil().getError(event, path+".no_channel")).setComponents().queue();
										} else {
											try {
												bot.getCheckUtil().hasPermissions(event, true, channel, new Permission[]{Permission.MESSAGE_SEND, Permission.MESSAGE_EMBED_LINKS});
												TextChannel newTc = (TextChannel) channel;
												EmbedBuilder builder3 = bot.getEmbedUtil().getEmbed(event)
													.setColor(Constants.COLOR_SUCCESS);
												
												bot.getDBUtil().guild.setLogChannel(event.getGuild().getId(), newTc.getId());
												newTc.sendMessageEmbeds(builder3.setDescription(lu.getText(event, path+".as_log")).build()).queue();
												hook.editOriginalEmbeds(builder3.setDescription(lu.getText(event, path+".done").replace("{channel}", newTc.getAsMention())).build()).setComponents().queue();
											} catch (CheckException ex) {
												hook.editOriginal(ex.getEditData()).queue();
											}
										}

									},
									30,
									TimeUnit.SECONDS,
									() -> {
										hook.editOriginalComponents(ActionRow.of(menu.asDisabled())).queue();
									}
								);
							} else {
								bot.getDBUtil().guild.setLogChannel(guildId, "NULL");
								EmbedBuilder builder2 = bot.getEmbedUtil().getEmbed(event)
									.setDescription(lu.getText(event, path+".removed"));
								hook.editOriginalEmbeds(builder2.build()).setComponents().queue();
							}

						},
						30,
						TimeUnit.SECONDS,
						() -> {
							hook.editOriginalComponents(buttons.asDisabled()).queue();
						}
					);
				});
					
					

			}
		}

	}

}
