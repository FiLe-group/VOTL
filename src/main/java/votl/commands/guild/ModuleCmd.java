package votl.commands.guild;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import javax.annotation.Nonnull;

import votl.App;
import votl.commands.CommandBase;
import votl.objects.CmdAccessLevel;
import votl.objects.CmdModule;
import votl.objects.command.SlashCommand;
import votl.objects.command.SlashCommandEvent;
import votl.objects.constants.CmdCategory;
import votl.objects.constants.Constants;

import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.entities.MessageEmbed;
import net.dv8tion.jda.api.events.interaction.component.SelectMenuInteractionEvent;
import net.dv8tion.jda.api.interactions.DiscordLocale;
import net.dv8tion.jda.api.interactions.InteractionHook;
import net.dv8tion.jda.api.interactions.components.ActionRow;
import net.dv8tion.jda.api.interactions.components.selections.SelectMenu;
import net.dv8tion.jda.api.interactions.components.selections.SelectOption;

import com.jagrosh.jdautilities.commons.waiter.EventWaiter;
import com.jagrosh.jdautilities.doc.standard.CommandInfo;

@CommandInfo(
	name = "module",
	usage = "/module <show / enable / disable>"
)
public class ModuleCmd extends CommandBase {
	
	private static EventWaiter waiter;
	
	public ModuleCmd(App bot, EventWaiter waiter) {
		super(bot);
		this.name = "module";
		this.path = "bot.guild.module";
		this.children = new SlashCommand[]{new Show(bot), new Disable(bot), new Enable(bot)};
		this.userPermissions = new Permission[]{Permission.MANAGE_SERVER};
		this.category = CmdCategory.GUILD;
		this.accessLevel = CmdAccessLevel.OWNER;
		this.mustSetup = true;
		ModuleCmd.waiter = waiter;
	}

	@Override
	protected void execute(SlashCommandEvent event) {

	}

	private class Show extends CommandBase {

		public Show(App bot) {
			super(bot);
			this.name = "show";
			this.path = "bot.guild.module.show";
		}

		@Override
		protected void execute(SlashCommandEvent event) {
			event.deferReply(true).queue(
				hook -> {
					sendReply(event, hook);
				}	
			);
		}

		@SuppressWarnings("null")
		private void sendReply(SlashCommandEvent event, InteractionHook hook) {

			String guildId = Optional.ofNullable(event.getGuild()).map(g -> g.getId()).orElse("0");
			DiscordLocale userLocale = event.getUserLocale();

			StringBuilder builder = new StringBuilder();
			List<CmdModule> disabled = getModules(guildId, false);
			for (CmdModule sModule : getModules(guildId, true, false)) {
				builder.append(format(lu.getLocalized(userLocale, sModule.getPath()), (disabled.contains(sModule) ? Constants.FAILURE : Constants.SUCCESS))).append("\n");
			}

			MessageEmbed embed = bot.getEmbedUtil().getEmbed(event)
				.setTitle(lu.getLocalized(userLocale, "bot.guild.module.show.embed.title"))
				.setDescription(lu.getLocalized(userLocale, "bot.guild.module.show.embed.value"))
				.addField(lu.getLocalized(userLocale, "bot.guild.module.show.embed.field"), builder.toString(), false)
				.build();

			hook.editOriginalEmbeds(embed).queue();
		}

		@Nonnull
		private String format(String sModule, String status) {
			return "`" + status + "` | " + sModule;
		}
	}

	private class Disable extends CommandBase {

		public Disable(App bot) {
			super(bot);
			this.name = "disable";
			this.path = "bot.guild.module.disable";
		}

		@Override
		protected void execute(SlashCommandEvent event) {
			event.deferReply(true).queue(
				hook -> {
					sendReply(event, hook);
				}
			);
		}

		@SuppressWarnings("null")
		private void sendReply(SlashCommandEvent event, InteractionHook hook) {

			String guildId = Optional.ofNullable(event.getGuild()).map(g -> g.getId()).orElse("0");
			DiscordLocale userLocale = event.getUserLocale();

			EmbedBuilder embed = bot.getEmbedUtil().getEmbed(event)
				.setTitle(lu.getLocalized(userLocale, "bot.guild.module.disable.embed_title"));

			List<CmdModule> enabled = getModules(guildId, true);
			if (enabled.isEmpty()) {
				embed.setDescription(lu.getLocalized(userLocale, "bot.guild.module.disable.none"))
					.setColor(Constants.COLOR_FAILURE);
				hook.editOriginalEmbeds(embed.build()).queue();
				return;
			}

			embed.setDescription(lu.getLocalized(userLocale, "bot.guild.module.disable.embed_value"));
			SelectMenu menu = SelectMenu.create("disable-module")
				.setPlaceholder("Select")
				.setRequiredRange(1, 1)
				.addOptions(enabled.stream().map(
					sModule -> {
						return SelectOption.of(lu.getLocalized(userLocale, sModule.getPath()), sModule.toString());
					}
				).collect(Collectors.toList()))
				.build();

			hook.editOriginalEmbeds(embed.build()).setActionRow(menu).queue(
				sendHook -> {
					waiter.waitForEvent(
						SelectMenuInteractionEvent.class,
						e -> e.getComponentId().equals("disable-module") && e.getMessageId().equals(sendHook.getId()),
						actionEvent -> {

							actionEvent.deferEdit().queue(
								actionHook -> {
									CmdModule sModule = CmdModule.valueOf(actionEvent.getSelectedOptions().get(0).getValue());
									if (bot.getDBUtil().moduleDisabled(guildId, sModule)) {
										hook.editOriginal(bot.getEmbedUtil().getError(event, "bot.guild.module.disable.already")).setComponents().queue();
										return;
									}
									bot.getDBUtil().moduleAdd(guildId, sModule);
									EmbedBuilder editEmbed = bot.getEmbedUtil().getEmbed(event)
										.setTitle(lu.getLocalized(userLocale, "bot.guild.module.disable.done").replace("{module}", lu.getLocalized(userLocale, sModule.getPath())))
										.setColor(Constants.COLOR_SUCCESS);
									hook.editOriginalEmbeds(editEmbed.build()).setComponents().queue();
								}
							);

						},
						30,
						TimeUnit.SECONDS,
						() -> {
							hook.editOriginalComponents(
								ActionRow.of(menu.createCopy().setPlaceholder(lu.getLocalized(userLocale, "bot.guild.module.enable.timed_out")).setDisabled(true).build())
							).queue();
						}
					);
				}
			);

		}
	}

	private class Enable extends CommandBase {

		public Enable(App bot) {
			super(bot);
			this.name = "enable";
			this.path = "bot.guild.module.enable";
		}

		@Override
		protected void execute(SlashCommandEvent event) {
			event.deferReply(true).queue(
				hook -> {
					sendReply(event, hook);
				}
			);
		}

		@SuppressWarnings("null")
		private void sendReply(SlashCommandEvent event, InteractionHook hook) {

			String guildId = Optional.ofNullable(event.getGuild()).map(g -> g.getId()).orElse("0");
			DiscordLocale userLocale = event.getUserLocale();

			EmbedBuilder embed = bot.getEmbedUtil().getEmbed(event)
				.setTitle(lu.getLocalized(userLocale, "bot.guild.module.enable.embed_title"));

			List<CmdModule> enabled = getModules(guildId, false);
			if (enabled.isEmpty()) {
				embed.setDescription(lu.getLocalized(userLocale, "bot.guild.module.enable.none"))
					.setColor(Constants.COLOR_FAILURE);
				hook.editOriginalEmbeds(embed.build()).queue();
				return;
			}

			embed.setDescription(lu.getLocalized(userLocale, "bot.guild.module.enable.embed_value"));
			SelectMenu menu = SelectMenu.create("enable-module")
				.setPlaceholder("Select")
				.setRequiredRange(1, 1)
				.addOptions(enabled.stream().map(
					sModule -> {
						return SelectOption.of(lu.getLocalized(userLocale, sModule.getPath()), sModule.toString());
					}
				).collect(Collectors.toList()))
				.build();

			hook.editOriginalEmbeds(embed.build()).setActionRow(menu).queue(
				sendHook -> {
					waiter.waitForEvent(
						SelectMenuInteractionEvent.class,
						e -> e.getComponentId().equals("enable-module") && e.getMessageId().equals(sendHook.getId()),
						actionEvent -> {

							actionEvent.deferEdit().queue(
								actionHook -> {
									CmdModule sModule = CmdModule.valueOf(actionEvent.getSelectedOptions().get(0).getValue());
									if (!bot.getDBUtil().moduleDisabled(guildId, sModule)) {
										hook.editOriginal(bot.getEmbedUtil().getError(event, "bot.guild.module.enable.already")).setComponents().queue();
										return;
									}
									bot.getDBUtil().moduleRemove(guildId, sModule);
									EmbedBuilder editEmbed = bot.getEmbedUtil().getEmbed(event)
										.setTitle(lu.getLocalized(userLocale, "bot.guild.module.enable.done").replace("{module}", lu.getLocalized(userLocale, sModule.getPath())))
										.setColor(Constants.COLOR_SUCCESS);
									hook.editOriginalEmbeds(editEmbed.build()).setComponents().queue();
								}
							);

						},
						10,
						TimeUnit.SECONDS,
						() -> {
							hook.editOriginalComponents(
								ActionRow.of(menu.createCopy().setPlaceholder(lu.getLocalized(userLocale, "bot.guild.module.enable.timed_out")).setDisabled(true).build())
							).queue();
						}
					);
				}
			);

		}
	}

	private List<CmdModule> getModules(String guildId, boolean on) {
		return getModules(guildId, false, on);
	}

	private List<CmdModule> getModules(String guildId, boolean all, boolean on) {
		List<CmdModule> modules = new ArrayList<CmdModule>(Arrays.asList(CmdModule.values()));
		if (all) {
			return modules;
		}

		List<CmdModule> disabled = bot.getDBUtil().modulesGet(guildId);
		if (on) {
			modules.removeAll(disabled);
			return modules;
		} else {
			return disabled;
		}
	}
}
