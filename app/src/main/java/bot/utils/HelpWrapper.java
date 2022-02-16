package bot.utils;

import java.util.Objects;

import com.jagrosh.jdautilities.command.Command;
import com.jagrosh.jdautilities.command.CommandEvent;
import com.jagrosh.jdautilities.command.Command.Category;

import bot.App;
import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.entities.User;

public class HelpWrapper {

	private final App bot;

	protected Permission[] botPerms;

	public HelpWrapper(App bot) {
		this.bot = bot;
		//this.botPerms = new Permission[]{Permission.MESSAGE_EMBED_LINKS};
	}

	public void helpConsumer(CommandEvent event) {
		
		String text = getString(event);

		if (event.getEvent().isFromGuild()) {
			if (!event.getSelfMember().hasPermission(event.getGuildChannel(), Permission.MESSAGE_SEND)) {
				event.getMember().getUser().openPrivateChannel()
					.flatMap(channel -> channel.sendMessage(text))
					.queue();
				return;
			} else if (bot.getMessageUtil().hasArgs("dm", event.getArgs())) {
				String mention = event.getMember().getAsMention();
				event.getMember().getUser().openPrivateChannel()
					.flatMap(channel -> channel.sendMessage(text))
					.queue(
						message -> event.reply(
							bot.getMsg(event.getGuild().getId(), "bot.dm_success", mention)
						), 
						error -> event.reply(
							bot.getMsg(event.getGuild().getId(), "bot.dm_failure", mention)
						)
					);
				return;
			}
		}

		event.reply(text);
	}

	public void commandHelpConsumer(CommandEvent event) {
		// In dev...
	}

	private String getString(CommandEvent event) {
		String guildID = (event.getEvent().isFromGuild() ? event.getGuild().getId() : "0");
		String prefix = bot.getPrefix(guildID);
		String ownerID = event.getClient().getOwnerId();
		String serverInvite = event.getClient().getServerInvite();
		
		StringBuilder builder = new StringBuilder("**"+event.getSelfUser().getName()+"** commands:\n");
		Category category = null;
		for (Command command : event.getClient().getCommands()) {
			if (!command.isHidden() && (!command.isOwnerCommand() || event.isOwner())) {
				if (!Objects.equals(category, command.getCategory())) {
					category = command.getCategory();
					builder.append("\n\n  __").append(category==null ? "No Category" : category.getName()).append("__:\n");
				}
				builder.append("\n`").append(prefix).append(prefix==null?" ":"").append(command.getName())
					.append(command.getArguments()==null ? "`" : " "+command.getArguments()+"`")
					.append(" - ").append(command.getHelp());
			}
		}
		User owner = event.getJDA().getUserById(ownerID);
		if (owner!=null) {
			builder.append("\n\nFor additional help, contact **").append(owner.getName()).append("**#").append(owner.getDiscriminator());
			if(serverInvite!=null)
				builder.append(" or join ").append(serverInvite);
		}

		return builder.toString();
	}
}
