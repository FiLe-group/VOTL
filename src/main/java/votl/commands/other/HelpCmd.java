package votl.commands.other;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;

import votl.App;
import votl.commands.CommandBase;
import votl.objects.command.SlashCommand;
import votl.objects.command.SlashCommandEvent;
import votl.objects.constants.CmdCategory;
import votl.objects.constants.Constants;

import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.interactions.DiscordLocale;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;

import com.jagrosh.jdautilities.doc.standard.CommandInfo;

@CommandInfo
(
	name = "help",
	description = "shows help menu",
	usage = "/help [show?][category:]"
)
public class HelpCmd extends CommandBase {

	public HelpCmd(App bot) {
		super(bot);
		this.name = "help";
		this.path = "bot.help";
		List<OptionData> options = new ArrayList<>();
		options.add(new OptionData(OptionType.BOOLEAN, "show", lu.getText("misc.show_description")));
		options.add(new OptionData(OptionType.STRING, "category", lu.getText(path+".category_info.help"))
			.addChoice("Voice", "voice")
			.addChoice("Guild", "guild")
			.addChoice("Owner", "owner")
			.addChoice("Webhook", "webhook")
			.addChoice("Other", "other"));
		options.add(new OptionData(OptionType.STRING, "command", lu.getText(path+".command_info.help"), false, true)
			.setRequiredLength(3, 20));
		this.options = options;
		this.category = CmdCategory.OTHER;
		this.guildOnly = false;
	}

	@Override
	protected void execute(SlashCommandEvent event) {
		event.deferReply(event.isFromGuild() ? !event.optBoolean("show", false) : false).queue();

		String findCmd = event.optString("command");
				
		if (findCmd != null) {
			sendCommandHelp(event, findCmd.split(" ")[0].toLowerCase());
		} else {
			String filCat = event.optString("category");
			sendHelp(event, filCat);
		}
	}

	@SuppressWarnings("null")
	private void sendCommandHelp(SlashCommandEvent event, String findCmd) {
		DiscordLocale userLocale = event.getUserLocale();

		SlashCommand command = null;
		for (SlashCommand cmd : event.getClient().getSlashCommands()) {
			if (cmd.getName().equals(findCmd)) {
				command = cmd;
				break;
			}
		}

		if (command == null) {
			editError(event, "bot.help.command_info.no_command", "Requested: "+findCmd);
		} else {
			EmbedBuilder builder = null;
			if (event.isFromGuild()) {
				builder = bot.getEmbedUtil().getEmbed(event);
			} else {
				builder = bot.getEmbedUtil().getEmbed();
			}

			builder.setTitle(lu.getLocalized(userLocale, "bot.help.command_info.title").replace("{command}", command.getName()))
				.setDescription(lu.getLocalized(userLocale, "bot.help.command_info.value")
					.replace("{category}", Optional.ofNullable(command.getCategory())
						.map(cat -> lu.getLocalized(userLocale, "bot.help.command_menu.categories."+cat.getName())).orElse(Constants.NONE))
					.replace("{owner}", command.isOwnerCommand() ? Constants.SUCCESS : Constants.FAILURE)
					.replace("{guild}", command.isGuildOnly() ? Constants.SUCCESS : Constants.FAILURE)
					.replace("{module}", Optional.ofNullable(command.getModule()).map(mod -> lu.getLocalized(userLocale, mod.getPath())).orElse(Constants.NONE)))
				.addField(lu.getLocalized(userLocale, "bot.help.command_info.help_title"), lu.getLocalized(userLocale, command.getHelpPath()), false)
				.addField(lu.getLocalized(userLocale, "bot.help.command_info.usage_title"), lu.getLocalized(userLocale, "bot.help.command_info.usage_value")
					.replace("{command_usage}", lu.getLocalized(userLocale, command.getUsagePath())), false);
			
			editHookEmbed(event, builder.build());
		}
		
	}

	@SuppressWarnings("null")
	private void sendHelp(SlashCommandEvent event, String filCat) {

		DiscordLocale userLocale = event.getUserLocale();
		String prefix = "/";
		EmbedBuilder builder = null;

		if (event.isFromGuild()) {
			builder = bot.getEmbedUtil().getEmbed(event);
		} else {
			builder = bot.getEmbedUtil().getEmbed();
		}

		builder.setTitle(lu.getLocalized(userLocale, "bot.help.command_menu.title"))
			.setDescription(lu.getLocalized(userLocale, "bot.help.command_menu.description.command_value"));

		Category category = null;
		String fieldTitle = "";
		StringBuilder fieldValue = new StringBuilder();
		List<SlashCommand> commands = (
			filCat == null ? 
			event.getClient().getSlashCommands() : 
			event.getClient().getSlashCommands().stream().filter(cmd -> cmd.getCategory().getName().contentEquals(filCat)).collect(Collectors.toList())
		);
		for (SlashCommand command : commands) {
			if (!command.isHidden() && (!command.isOwnerCommand() || bot.getCheckUtil().isOwner(event.getClient(), event.getUser()))) {
				if (!Objects.equals(category, command.getCategory())) {
					if (category != null) {
						builder.addField(fieldTitle, fieldValue.toString(), false);
					}
					category = command.getCategory();
					fieldTitle = lu.getLocalized(userLocale, "bot.help.command_menu.categories."+category.getName());
					fieldValue = new StringBuilder();
				}
				fieldValue.append("`").append(prefix).append(prefix==null?" ":"").append(command.getName())
					.append(command.getArguments()==null ? "`" : " "+command.getArguments()+"`")
					.append(" - ").append(command.getDescriptionLocalization().get(userLocale))
					// REMAKE to support CommandBase and getLocalized help
					.append("\n");
			}
		}
		if (category != null) {
			builder.addField(fieldTitle, fieldValue.toString(), false);
		}

		User owner = Optional.ofNullable(event.getClient().getOwnerId()).map(id -> event.getJDA().getUserById(id)).orElse(null);

		if (owner != null) {
			fieldTitle = lu.getLocalized(userLocale, "bot.help.command_menu.description.support_title");
			fieldValue = new StringBuilder()
				.append(lu.getLocalized(userLocale, "bot.help.command_menu.description.support_value").replace("{owner_name}", owner.getAsTag()));
			builder.addField(fieldTitle, fieldValue.toString(), false);
		}
		
		editHookEmbed(event, builder.build());
	}
}
