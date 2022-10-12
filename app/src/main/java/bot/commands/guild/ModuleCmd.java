package bot.commands.guild;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import javax.annotation.Nonnull;

import com.jagrosh.jdautilities.command.SlashCommand;
import com.jagrosh.jdautilities.command.SlashCommandEvent;
import com.jagrosh.jdautilities.commons.waiter.EventWaiter;
import com.jagrosh.jdautilities.doc.standard.CommandInfo;

import bot.App;
import bot.objects.constants.Constants;
import bot.utils.exception.CheckException;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.entities.MessageEmbed;
import net.dv8tion.jda.api.events.interaction.component.SelectMenuInteractionEvent;
import net.dv8tion.jda.api.interactions.InteractionHook;
import net.dv8tion.jda.api.interactions.components.ActionRow;
import net.dv8tion.jda.api.interactions.components.selections.SelectMenu;
import net.dv8tion.jda.api.interactions.components.selections.SelectOption;

@CommandInfo(
	name = "module",
	usage = "/module <show / enable / disable>"
)
public class ModuleCmd extends SlashCommand {
	
	private static App bot;
	private static EventWaiter waiter;

	protected static Permission[] userPerms;
	
	public ModuleCmd(App bot, EventWaiter waiter) {
		this.name = "module";
		this.help = bot.getMsg("bot.guild.module.help");
		this.category = new Category("guild");
		ModuleCmd.userPerms = new Permission[]{Permission.MANAGE_SERVER};
		ModuleCmd.bot = bot;
		ModuleCmd.waiter = waiter;
		this.children = new SlashCommand[]{new Show(), new Disable(), new Enable()};
	}

	@Override
	protected void execute(SlashCommandEvent event) {

	}

	private static class Show extends SlashCommand {

		public Show() {
			this.name = "show";
			this.help = bot.getMsg("bot.guild.module.show.help");
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

			try {
				bot.getCheckUtil().hasPermissions(event.getTextChannel(), event.getMember(), userPerms)
					.guildExists(event, guildId);
			} catch (CheckException ex) {
				hook.editOriginal(ex.getEditData()).queue();
				return;
			}

			StringBuilder builder = new StringBuilder();
			List<String> disabled = getModules(guildId, false);
			for (String module : getModules(guildId, true, false)) {
				builder.append(format(bot.getMsg(guildId, "modules."+module), (disabled.contains(module) ? Constants.FAILURE : Constants.SUCCESS))).append("\n");
			}

			MessageEmbed embed = bot.getEmbedUtil().getEmbed(event.getMember())
				.setTitle(bot.getMsg(guildId, "bot.guild.module.show.embed.title"))
				.setDescription(bot.getMsg(guildId, "bot.guild.module.show.embed.value"))
				.addField(bot.getMsg(guildId, "bot.guild.module.show.embed.field"), builder.toString(), false)
				.build();

			hook.editOriginalEmbeds(embed).queue();
		}

		@Nonnull
		private String format(String module, String status) {
			return "`" + status + "` | " + module;
		}
	}

	private static class Disable extends SlashCommand {

		public Disable() {
			this.name = "disable";
			this.help = bot.getMsg("bot.guild.module.disable.help");
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

			try {
				bot.getCheckUtil().hasPermissions(event.getTextChannel(), event.getMember(), userPerms)
					.guildExists(event, guildId);
			} catch (CheckException ex) {
				hook.editOriginal(ex.getEditData()).queue();
				return;
			}

			EmbedBuilder embed = bot.getEmbedUtil().getEmbed(event.getMember())
				.setTitle(bot.getMsg(guildId, "bot.guild.module.disable.embed_title"));

			List<String> enabled = getModules(guildId, true);
			if (enabled.isEmpty()) {
				embed.setDescription(bot.getMsg(guildId, "bot.guild.module.disable.none"))
					.setColor(Constants.COLOR_FAILURE);
				hook.editOriginalEmbeds(embed.build()).queue();
			} else {

				embed.setDescription(bot.getMsg(guildId, "bot.guild.module.disable.embed_value"));
				SelectMenu menu = SelectMenu.create("disable-module")
					.setPlaceholder("Select")
					.setRequiredRange(1, 1)
					.addOptions(enabled.stream().map(
						module -> {
						return SelectOption.of(bot.getMsg(guildId, "modules."+module), module);
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
										String module = actionEvent.getSelectedOptions().get(0).getValue();
									if (bot.getDBUtil().moduleDisabled(guildId, module)) {
										hook.editOriginal(bot.getEmbedUtil().getError(event, "bot.guild.module.disable.already")).setComponents().queue();
										return;
									}
										bot.getDBUtil().moduleAdd(guildId, module);
										EmbedBuilder editEmbed = bot.getEmbedUtil().getEmbed(event.getMember())
										.setTitle(bot.getMsg(guildId, "bot.guild.module.disable.done").replace("{module}", bot.getMsg(guildId, "modules."+module)))
											.setColor(Constants.COLOR_SUCCESS);
										hook.editOriginalEmbeds(editEmbed.build()).setComponents().queue();
									}
								);

							},
							30,
							TimeUnit.SECONDS,
							() -> {
								hook.editOriginalComponents(
									ActionRow.of(menu.createCopy().setPlaceholder(bot.getMsg(guildId, "bot.guild.module.enable.timed_out")).setDisabled(true).build())
								).queue();
							}
						);
					}
				);

			}

		}
	}

	private static class Enable extends SlashCommand {

		public Enable() {
			this.name = "enable";
			this.help = bot.getMsg("bot.guild.module.enable.help");
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

			try {
				bot.getCheckUtil().hasPermissions(event.getTextChannel(), event.getMember(), userPerms)
					.guildExists(event, guildId);
			} catch (CheckException ex) {
				hook.editOriginal(ex.getEditData()).queue();
				return;
			}

			EmbedBuilder embed = bot.getEmbedUtil().getEmbed(event.getMember())
				.setTitle(bot.getMsg(guildId, "bot.guild.module.enable.embed_title"));

			List<String> enabled = getModules(guildId, false);
			if (enabled.isEmpty()) {
				embed.setDescription(bot.getMsg(guildId, "bot.guild.module.enable.none"))
					.setColor(Constants.COLOR_FAILURE);
				hook.editOriginalEmbeds(embed.build()).queue();
			} else {

				embed.setDescription(bot.getMsg(guildId, "bot.guild.module.enable.embed_value"));
				SelectMenu menu = SelectMenu.create("enable-module")
					.setPlaceholder("Select")
					.setRequiredRange(1, 1)
					.addOptions(enabled.stream().map(
						module -> {
						return SelectOption.of(bot.getMsg(guildId, "modules."+module), module);
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
										String module = actionEvent.getSelectedOptions().get(0).getValue();
									if (!bot.getDBUtil().moduleDisabled(guildId, module)) {
										hook.editOriginal(bot.getEmbedUtil().getError(event, "bot.guild.module.enable.already")).setComponents().queue();
										return;
									}
										bot.getDBUtil().moduleRemove(guildId, module);
										EmbedBuilder editEmbed = bot.getEmbedUtil().getEmbed(event.getMember())
										.setTitle(bot.getMsg(guildId, "bot.guild.module.enable.done").replace("{module}", bot.getMsg(guildId, "modules."+module)))
											.setColor(Constants.COLOR_SUCCESS);
										hook.editOriginalEmbeds(editEmbed.build()).setComponents().queue();
									}
								);

							},
							10,
							TimeUnit.SECONDS,
							() -> {
								hook.editOriginalComponents(
									ActionRow.of(menu.createCopy().setPlaceholder(bot.getMsg(guildId, "bot.guild.module.enable.timed_out")).setDisabled(true).build())
								).queue();
							}
						);
					}
				);
				
			}

		}
	}

	private static List<String> getModules(String guildId, boolean on) {
		return getModules(guildId, false, on);
	}

	private static List<String> getModules(String guildId, boolean all, boolean on) {
		List<String> modules = new ArrayList<>(bot.getAllModules());
		if (all) {
			return modules;
		}

		List<String> disabled = bot.getDBUtil().modulesGet(guildId);
		if (on) {
			modules.removeAll(disabled);
			return modules;
		} else {
			return disabled;
		}
	}
}
