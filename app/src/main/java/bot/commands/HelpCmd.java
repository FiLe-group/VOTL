package bot.commands;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;

import com.jagrosh.jdautilities.command.SlashCommand;
import com.jagrosh.jdautilities.command.SlashCommandEvent;
import com.jagrosh.jdautilities.doc.standard.CommandInfo;

import bot.App;
import bot.objects.constants.CmdCategory;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.interactions.InteractionHook;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;

@CommandInfo
(
	name = "help",
	description = "shows help menu",
	usage = "/help [show?][category:]"
)
public class HelpCmd extends SlashCommand {
	
	private final App bot;

	public HelpCmd(App bot) {
		this.name = "help";
		this.help = bot.getMsg("bot.help.help");
		this.guildOnly = false;
		this.category = CmdCategory.OTHER;

		List<OptionData> options = new ArrayList<>();
		options.add(new OptionData(OptionType.BOOLEAN, "show", bot.getMsg("misc.show_description")));
		options.add(new OptionData(OptionType.STRING, "category", bot.getMsg("bot.help.category_info.help"))
			.addChoice("Voice", "voice")
			.addChoice("Guild", "guild")
			.addChoice("Owner", "owner")
			.addChoice("Webhook", "webhook")
			.addChoice("Other", "other")
		);
		this.options = options;

		this.bot = bot;
	}

	@Override
	protected void execute(SlashCommandEvent event) {

		event.deferReply(event.isFromGuild() ? !event.getOption("show", false, OptionMapping::getAsBoolean) : false).queue(
			hook -> {
				String filCat = event.getOption("category", null, OptionMapping::getAsString);
				
				sendReply(event, hook, filCat);
			}
		);
	}

	/* private MessageEmbed getCommandHelpEmbed(SlashCommandEvent event) {
		// in dev
	} */

	@SuppressWarnings("null")
	private void sendReply(SlashCommandEvent event, InteractionHook hook, String filCat) {

		String guildId = Optional.ofNullable(event.getGuild()).map(g -> g.getId()).orElse("0");
		String prefix = "/";
		EmbedBuilder builder = null;

		if (event.isFromGuild()) {
			builder = bot.getEmbedUtil().getEmbed(event.getMember());
		} else {
			builder = bot.getEmbedUtil().getEmbed();
		}

		builder.setTitle(bot.getMsg(guildId, "bot.help.command_menu.title"))
			.setDescription(bot.getMsg(guildId, "bot.help.command_menu.description.command_value"));

		Category category = null;
		String fieldTitle = "";
		StringBuilder fieldValue = new StringBuilder();
		List<SlashCommand> commands = (
			filCat == null ? 
			event.getClient().getSlashCommands() : 
			event.getClient().getSlashCommands().stream().filter(cmd -> cmd.getCategory().getName().contentEquals(filCat)).collect(Collectors.toList())
		);
		for (SlashCommand command : commands) {
			if (!command.isHidden() && (!command.isOwnerCommand() || bot.getCheckUtil().isOwner(event, event.getUser()))) {
				if (!Objects.equals(category, command.getCategory())) {
					if (category != null) {
						builder.addField(fieldTitle, fieldValue.toString(), false);
					}
					category = command.getCategory();
					fieldTitle = bot.getMsg(guildId, "bot.help.command_menu.categories."+category.getName());
					fieldValue = new StringBuilder();
				}
				fieldValue.append("`").append(prefix).append(prefix==null?" ":"").append(command.getName())
					.append(command.getArguments()==null ? "`" : " "+command.getArguments()+"`")
					.append(" - ").append(command.getHelp())
					.append("\n");
			}
		}
		if (category != null) {
			builder.addField(fieldTitle, fieldValue.toString(), false);
		}

		User owner = Optional.ofNullable(event.getClient().getOwnerId()).map(id -> event.getJDA().getUserById(id)).orElse(null);

		if (owner != null) {
			fieldTitle = bot.getMsg(guildId, "bot.help.command_menu.description.support_title");
			fieldValue = new StringBuilder()
				.append(bot.getMsg(guildId, "bot.help.command_menu.description.support_value").replace("{owner_name}", owner.getAsTag()));
			builder.addField(fieldTitle, fieldValue.toString(), false);
		}
		
		hook.editOriginalEmbeds(builder.build()).queue();
	}
}
