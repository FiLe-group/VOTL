package bot.objects;

import java.util.HashMap;
import java.util.Map;

import javax.annotation.Nonnull;

import com.jagrosh.jdautilities.command.CommandClient;
import com.jagrosh.jdautilities.command.SlashCommand;
import com.jagrosh.jdautilities.command.SlashCommandEvent;

import bot.App;
import bot.utils.exception.CheckException;
import net.dv8tion.jda.api.entities.channel.ChannelType;
import net.dv8tion.jda.api.interactions.DiscordLocale;
import net.dv8tion.jda.api.interactions.commands.DefaultMemberPermissions;
import net.dv8tion.jda.api.interactions.commands.build.CommandData;
import net.dv8tion.jda.api.interactions.commands.build.Commands;
import net.dv8tion.jda.api.interactions.commands.build.SlashCommandData;
import net.dv8tion.jda.api.interactions.commands.build.SubcommandData;
import net.dv8tion.jda.api.interactions.commands.build.SubcommandGroupData;
import net.dv8tion.jda.api.utils.messages.MessageCreateData;
import net.dv8tion.jda.api.utils.messages.MessageEditData;

/**
 * Custom {@link com.jagrosh.jdautilities.command.SlashCommand SlashCommand} implementation.
 */
public abstract class CommandBase extends SlashCommand {

	/**
	 * The child commands of the command. These are used in the format {@code /<parent name>
	 * <child name>}.
	 * This is synonymous with sub commands. Additionally, sub-commands cannot have children.<br>
	 */
	protected CommandBase[] children = new CommandBase[0];

	/**
	 * @deprecated Use {@link CommandBase#helpPath helpPath} instead!
	 */
	protected String help = "";

	/**
	 * @deprecated
	 */
	protected String[] aliases = new String[0];

	/**
	 * @deprecated
	 */
	protected String arguments = null;

	/**
	 * @deprecated
	 */
	protected boolean hidden = false;

	/**
	 * Path to the command's help String. Must by set, otherwise 
	 */
	protected String helpPath = "misc.unknown";

	/**
	 * Gets the {@link bot.commands.OtherCmdBase#helpPath OtherCmdBase.helpPath} for the Command.
	 *
	 * @return The path for command's help String.
	 */
	public String getHelpPath()
	{
		return helpPath;
	}

	protected App bot = null;
	
	protected boolean mustSetup = false;

	protected String module = null;

	protected CmdAccessLevel accessLevel = CmdAccessLevel.ALL;

	public final void run(SlashCommandEvent event) {
		// client 
		final CommandClient client = event.getClient();

		// check owner command
		if (ownerCommand && (!isOwner(event, client))) {
			terminate(event, bot.getEmbedUtil().getError(event, "errors.command.not_owner"), client);
			return;
		}

		if (event.getChannelType() != ChannelType.PRIVATE) {
			try {
				// check access
				bot.getCheckUtil().hasAccess(event, accessLevel)
				// check module enabled
					.moduleEnabled(event, module)
				// check user perms
					.hasPermissions(event, userPermissions)
				// check bots perms
					.hasPermissions(event, true, botPermissions)
				// check setup
					.guildExists(event, mustSetup);
			} catch (CheckException ex) {
				terminate(event, ex.getCreateData(), client);
				return;
			}

			// nsfw check
			if (nsfwOnly && event.getChannelType() == ChannelType.TEXT && !event.getTextChannel().isNSFW())
			{
				terminate(event, bot.getEmbedUtil().getError(event, "errors.command.nsfw"), client);
				return;
			}
		}

		// cooldown check, ignoring owner
		if (cooldown > 0 && !(isOwner(event, client))) {
			String key = getCooldownKey(event);
			int remaining = client.getRemainingCooldown(key);
			if (remaining > 0) {
				terminate(event, getCooldownErrorEmbed(event, remaining, client), client);
				return;
			} else {
				client.applyCooldown(key, cooldown);
			}
		}

		// run
		try {
			execute(event);
		} catch(Throwable t) {
			if(client.getListener() != null)
			{
				client.getListener().onSlashCommandException(event, this, t);
				return;
			}
			// otherwise we rethrow
			throw t;
		}

		if(client.getListener() != null)
			client.getListener().onCompletedSlashCommand(event, this);
		
	}

	/**
	 * Builds CommandData for the SlashCommand upsert.
	 * This code is executed when we need to upsert the command.
	 *
	 * Useful for manual upserting.
	 *
	 * @return the built command data
	 */
	@SuppressWarnings("null")
	public CommandData buildCommandData() {
		// Set attributes
		this.help = bot.getMsg(helpPath);
		this.descriptionLocalization = bot.getFullLocaleMap(helpPath);

		// Make the command data
		SlashCommandData data = Commands.slash(getName(), help);
		if (!getOptions().isEmpty())
		{
			data.addOptions(getOptions());
		}

		//Check name localizations
		if (!getNameLocalization().isEmpty())
		{
			//Add localizations
			data.setNameLocalizations(getNameLocalization());
		}
		//Check description localizations
		if (!getDescriptionLocalization().isEmpty())
		{
			//Add localizations
			data.setDescriptionLocalizations(getDescriptionLocalization());
		}

		// Check for children
		if (children.length != 0)
		{
			// Temporary map for easy group storage
			Map<String, SubcommandGroupData> groupData = new HashMap<>();
			for (CommandBase child : children)
			{
				// Create subcommand data
				SubcommandData subcommandData = new SubcommandData(child.getName(), bot.getMsg(child.getHelpPath()));
				// Add options
				if (!child.getOptions().isEmpty())
				{
					subcommandData.addOptions(child.getOptions());
				}

				//Check child name localizations
				if (!child.getNameLocalization().isEmpty())
				{
					//Add localizations
					subcommandData.setNameLocalizations(child.getNameLocalization());
				}
				//Check child description localizations
				if (!child.getDescriptionLocalization().isEmpty())
				{
					//Add localizations
					subcommandData.setDescriptionLocalizations(child.getDescriptionLocalization());
				}

				// If there's a subcommand group
				if (child.getSubcommandGroup() != null)
				{
					SubcommandGroupData group = child.getSubcommandGroup();

					SubcommandGroupData newData = groupData.getOrDefault(group.getName(), group)
						.addSubcommands(subcommandData);

					groupData.put(group.getName(), newData);
				}
				// Just add to the command
				else
				{
					data.addSubcommands(subcommandData);
				}
			}
			if (!groupData.isEmpty())
				data.addSubcommandGroups(groupData.values());
		}

		if (this.getUserPermissions() == null)
			data.setDefaultPermissions(DefaultMemberPermissions.DISABLED);
		else
			data.setDefaultPermissions(DefaultMemberPermissions.enabledFor(this.getUserPermissions()));

		data.setGuildOnly(this.guildOnly);

		return data;
	}

	@SuppressWarnings("null")
	private MessageCreateData getCooldownErrorEmbed(SlashCommandEvent event, int remaining, CommandClient client) {
		if (remaining <= 0)
			return null;
		
		DiscordLocale userLocale = event.getUserLocale();
		StringBuilder front = new StringBuilder(bot.getLocalized(
			userLocale, "errors.cooldown.cooldown_left").replace("{time}", Integer.toString(remaining)
		));
		CCooldownScope cs = CCooldownScope.fromOriginal(cooldownScope);
		if (cs.equals(CCooldownScope.USER))
			{}
		else if (cs.equals(CCooldownScope.USER_GUILD) && event.getGuild()==null)
			front.append(" " + bot.getLocalized(userLocale, CCooldownScope.USER_CHANNEL.errorPath));
		else if (cs.equals(CCooldownScope.GUILD) && event.getGuild()==null)
			front.append(" " + bot.getLocalized(userLocale, CCooldownScope.CHANNEL.errorPath));
		else
			front.append(" " + bot.getLocalized(userLocale, cs.errorPath));

		return MessageCreateData.fromContent(front.append("!").toString());
	}

	private void terminate(SlashCommandEvent event, @Nonnull MessageEditData message, CommandClient client) {
		terminate(event, MessageCreateData.fromEditData(message), client);
	}

	private void terminate(SlashCommandEvent event, MessageCreateData message, CommandClient client) {
		if (message != null)
			event.reply(message).setEphemeral(true).queue();
		if (event.getClient().getListener() != null)
			client.getListener().onTerminatedSlashCommand(event, this);
	}
}
