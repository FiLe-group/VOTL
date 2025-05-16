/*
 * Copyright 2016-2018 John Grosh (jagrosh) & Kaidan Gustave (TheMonitorLizard)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package dev.fileeditor.votl.base.command;

import java.sql.SQLException;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

import dev.fileeditor.votl.contracts.reflection.Reflectional;
import dev.fileeditor.votl.objects.CmdAccessLevel;
import dev.fileeditor.votl.utils.exception.CheckException;

import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.MessageEmbed;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import net.dv8tion.jda.api.events.interaction.command.CommandAutoCompleteInteractionEvent;
import net.dv8tion.jda.api.exceptions.ErrorHandler;
import net.dv8tion.jda.api.interactions.DiscordLocale;
import net.dv8tion.jda.api.interactions.InteractionContextType;
import net.dv8tion.jda.api.interactions.callbacks.IReplyCallback;
import net.dv8tion.jda.api.interactions.commands.Command;
import net.dv8tion.jda.api.interactions.commands.DefaultMemberPermissions;
import net.dv8tion.jda.api.interactions.commands.build.*;
import net.dv8tion.jda.api.requests.ErrorResponse;
import net.dv8tion.jda.api.utils.messages.MessageCreateData;
import net.dv8tion.jda.api.utils.messages.MessageEditBuilder;
import net.dv8tion.jda.api.utils.messages.MessageEditData;
import org.jetbrains.annotations.NotNull;

/**
 * <h2><b>Slash Commands In JDA-Chewtils</b></h2>
 *
 * <p>This intends to mimic the {@link Command command} with minimal breaking changes,
 * to make migration easy and smooth.</p>
 * <p>Breaking changes are documented
 * <a href="https://github.com/Chew/JDA-Chewtils/wiki/Command-to-SlashCommand-Migration">here</a>.</p>
 * {@link SlashCommand#execute(SlashCommandEvent) #execute(CommandEvent)} body:
 *
 * <pre><code> public class ExampleCmd extends SlashCommand {
 *
 *      public ExampleCmd() {
 *          this.name = "example";
 *          this.help = "gives an example of commands do";
 *      }
 *
 *      {@literal @Override}
 *      protected void execute(SlashCommandEvent event) {
 *          event.reply("Hey look! This would be the bot's reply if this was a command!").queue();
 *      }
 *
 * }</code></pre>
 *
 * Execution is with the provision of the SlashCommandEvent is performed in two steps:
 * <ul>
 *     <li>{@link SlashCommand#run(SlashCommandEvent) run} - The command runs
 *     through a series of conditionals, automatically terminating the command instance if one is not met,
 *     and possibly providing an error response.</li>
 *
 *     <li>{@link SlashCommand#execute(SlashCommandEvent) execute} - The command,
 *     now being cleared to run, executes and performs whatever lies in the abstract body method.</li>
 * </ul>
 *
 * @author Olivia (Chew)
 */
public abstract class SlashCommand extends Interaction implements Reflectional {

	/**
	 * The name of the command, allows the command to be called the formats: <br>
	 * Slash Command: {@code /<command name>}
	 */
	@NotNull
	protected String name = "null";

	/**
	 * A small help String that summarizes the function of the command, used in the default help builder,
	 * and shown in the client for Slash Commands.
	 */
	@NotNull
	protected String help = "no help available";

	/**
	 * The {@link dev.fileeditor.votl.base.command.Category Category} of the command.
	 * <br>This can perform any other checks not completed by the default conditional fields.
	 */
	protected Category category = null;

	/**
	 * {@code true} if the command may only be used in an NSFW
	 * {@link TextChannel} or DMs.
	 * {@code false} if it may be used anywhere
	 * <br>Default: {@code false}
	 */
	protected boolean nsfwOnly = false;

	/**
	 * {@code true} if the command should reply with deferred ephemeral reply.
	 * {@code false} if it should send normal deferred reply.
	 * <br>Default: {@code false}
	 */
	protected boolean ephemeralReply = false;

	/**
	 * Localization of slash command name. Allows discord to change the language of the name of slash commands in the client.<br>
	 * Example:<br>
	 *<pre><code>
	 *     public Command() {
	 *          this.name = "help"
	 *          this.nameLocalization = Map.of(DiscordLocale.GERMAN, "hilfe", DiscordLocale.RUSSIAN, "помощь");
	 *     }
	 *</code></pre>
	 */
	@NotNull
	protected Map<DiscordLocale, String> nameLocalization = new HashMap<>();

	/**
	 * Localization of slash command description. Allows discord to change the language of the description of slash commands in the client.<br>
	 * Example:<br>
	 *<pre><code>
	 *     public Command() {
	 *          this.description = "all commands"
	 *          this.descriptionLocalization = Map.of(DiscordLocale.GERMAN, "alle Befehle", DiscordLocale.RUSSIAN, "все команды");
	 *     }
	 *</code></pre>
	 */
	@NotNull
	protected Map<DiscordLocale, String> descriptionLocalization = new HashMap<>();

	/**
	 * The child commands of the command. These are used in the format {@code /<parent name>
	 * <child name>}.
	 * This is synonymous with sub commands. Additionally, sub-commands cannot have children.<br>
	 */
	protected SlashCommand[] children = new SlashCommand[0];

	/**
	 * The subcommand/child group this is associated with.
	 * Will be in format {@code /<parent name> <subcommandGroup name> <subcommand name>}.
	 * <p>
	 * <b>This only works in a child/subcommand.</b>
	 * <p>
	 * To instantiate: <code>{@literal new SubcommandGroupData(name, description)}</code><br>
	 * It's important the instantiations are the same across children if you intend to keep them in the same group.
	 * <p>
	 * Can be null, and it will not be assigned to a group.
	 */
	protected SubcommandGroupData subcommandGroup = null;

	/**
	 * An array list of OptionData.
	 * <p>
	 * <b>This is incompatible with children. You cannot have a child AND options.</b>
	 * <p>
	 * This is to specify different options for arguments and the stuff.
	 * <p>
	 * For example, to add an argument for "input", you can do this:<br>
	 * <pre><code>
	 *     OptionData data = new OptionData(OptionType.STRING, "input", "The input for the command").setRequired(true);
	 *    {@literal List<OptionData> dataList = new ArrayList<>();}
	 *     dataList.add(data);
	 *     this.options = dataList;</code></pre>
	 */
	protected List<OptionData> options = new ArrayList<>();

	/**
	 * The main body method of a {@link SlashCommand SlashCommand}.
	 * <br>This is the "response" for a successful
	 * {@link SlashCommand#run(SlashCommandEvent) #run(CommandEvent)}.
	 *
	 * @param  event
	 *         The {@link SlashCommandEvent SlashCommandEvent} that
	 *         triggered this Command
	 */
	protected abstract void execute(SlashCommandEvent event);

	/**
	 * This body is executed when an auto-complete event is received.
	 * This only ever gets executed if an auto-complete {@link #options option} is set.
	 *
	 * @param event The event to handle.
	 * @see OptionData#setAutoComplete(boolean)
	 */
	@SuppressWarnings("unused")
	public void onAutoComplete(CommandAutoCompleteInteractionEvent event) {}

	/**
	 * Runs checks for the {@link SlashCommand SlashCommand} with the
	 * given {@link SlashCommandEvent SlashCommandEvent} that called it.
	 * <br>Will terminate, and possibly respond with a failure message, if any checks fail.
	 *
	 * @param  event
	 *         The SlashCommandEvent that triggered this Command
	 */
	public final boolean run(SlashCommandEvent event) {
		// start time
		final long timeStart = System.nanoTime();
		// client 
		final CommandClient client = event.getClient();

		// check blacklist
		if (bot.getCheckUtil().isBlacklisted(event.getUser())) {
			return terminate(event, client);
		}

		// check owner command
		if (ownerCommand && !isOwner(event, client)) {
			return terminate(event, bot.getEmbedUtil().getError(event, "errors.command.not_owner"), client);
		}

		// check db and permissions
		if (event.isFromGuild() && !ownerCommand) {
			Member author = event.getMember();
			try {
				bot.getCheckUtil()
				// check access
					.hasAccess(event, author, getAccessLevel())
				// check bots perms
					.hasPermissions(event, getBotPermissions());
			} catch (CheckException ex) {
				return terminate(event, ex.getCreateData(), client);
			}
		}

		// Record time
		bot.getAppLogger().debug("SlashCommand check duration: {}ns @ {} ", System.nanoTime()-timeStart, event.getResponseNumber());
		// execute
		try {
			execute(event);
		} catch (Throwable t) {
			if (client.getListener() != null) {
				client.getListener().onSlashCommandException(event, this, t);
				return false;
			}
			// otherwise we rethrow
			throw t;
		}

		if (client.getListener() != null)
			client.getListener().onCompletedSlashCommand(event, this);

		return true;
	}

	/**
	 * Tests whether the {@link net.dv8tion.jda.api.entities.User User} who triggered this
	 * event is an owner of the bot.
	 *
	 * @param event the event that triggered the command
	 * @param client the command client for checking stuff
	 * @return {@code true} if the User is the Owner, else {@code false}
	 */
	public boolean isOwner(SlashCommandEvent event, CommandClient client) {
		return client.getOwnerIdLong() == event.getUser().getIdLong();
	}

	/**
	 * Checks if the given input represents this Command
	 *
	 * @param  input
	 *         The input to check
	 *
	 * @return {@code true} if the input is the name or an alias of the Command
	 */
	public boolean isCommandFor(String input) {
		return name.equalsIgnoreCase(input);
	}

	/**
	 * Gets the {@link dev.fileeditor.votl.base.command.SlashCommand#name SlashCommand.name} for the Command.
	 *
	 * @return The name for the Command
	 */
	@NotNull
	public String getName() {
		return name;
	}

	/**
	 * Gets the {@link dev.fileeditor.votl.base.command.SlashCommand#help SlashCommand.help} for the Command.
	 *
	 * @return The help for the Command
	 */
	@NotNull
	public String getHelp() {
		return help;
	}

	/**
	 * Gets the {@link dev.fileeditor.votl.base.command.SlashCommand#category SlashCommand.category} for the Command.
	 *
	 * @return The category for the Command
	 */
	public Category getCategory() {
		return category;
	}

	/**
	 *
	 * @return If deferred reply will be ephemeral.
	 */
	public boolean isEphemeralReply() {
		return ephemeralReply;
	}

	/**
	 * Gets the subcommand data associated with this subcommand.
	 *
	 * @return subcommand data
	 */
	public SubcommandGroupData getSubcommandGroup() {
		return subcommandGroup;
	}

	/**
	 * Gets the options associated with this command.
	 *
	 * @return the OptionData array for options
	 */
	public List<OptionData> getOptions() {
		return options;
	}

	/**
	 * Builds CommandData for the SlashCommand upsert.
	 * This code is executed when we need to upsert the command.
	 * <p>
	 * Useful for manual upserting.
	 *
	 * @return the built command data
	 */
	public CommandData buildCommandData() {
		// Set attributes
		this.help = lu.getText(getHelpPath());
		this.descriptionLocalization = lu.getFullLocaleMap(getHelpPath(), getHelp());

		// Register middlewares
		registerThrottleMiddleware();
		if (cooldown > 0) {
			middlewares.add("cooldown");
		}

		// Make the command data
		SlashCommandData data = Commands.slash(getName(), getHelp());

		// Add options and localizations
		if (!getOptions().isEmpty()) {
			getOptions().forEach(option -> {
				option.setNameLocalizations(lu.getFullLocaleMap("%s.%s.name".formatted(getPath(), option.getName()), option.getName()));
				option.setDescriptionLocalizations(lu.getFullLocaleMap("%s.%s.help".formatted(getPath(), option.getName()), option.getDescription()));
			});
			data.addOptions(getOptions());
		}

		// Check name localizations
		if (!getNameLocalization().isEmpty()) {
			//Add localizations
			data.setNameLocalizations(getNameLocalization());
		}
		// Check description localizations
		if (!getDescriptionLocalization().isEmpty()) {
			//Add localizations
			data.setDescriptionLocalizations(getDescriptionLocalization());
		}
		// Add if NSFW command
		if (nsfwOnly) {
			data.setNSFW(true);
		}
		// Add AccessLevel if ownerCommand
		if (ownerCommand) {
			this.accessLevel = CmdAccessLevel.DEV;
		}

		// Check for children
		if (children.length != 0) {
			// Temporary map for easy group storage
			Map<String, SubcommandGroupData> groupData = new HashMap<>();
			for (SlashCommand child : children) {
				// Inherit
				if (child.userPermissions.length == 0) {
					child.userPermissions = getUserPermissions();
				}
				if (child.botPermissions.length == 0) {
					child.botPermissions = getBotPermissions();
				}
				if (Objects.equals(child.getAccessLevel().getLevel(), CmdAccessLevel.ALL.getLevel())) {
					child.accessLevel = getAccessLevel();
				}
				if (child.module == null) {
					child.module = getModule();
				}
				// Set attributes
				child.help = lu.getText(child.getHelpPath());
				child.descriptionLocalization = lu.getFullLocaleMap(child.getHelpPath(), child.getHelp());
				
				// Create subcommand data
				SubcommandData subcommandData = new SubcommandData(child.getName(), child.getHelp());
				
				// Add options and check localizations
				if (!child.getOptions().isEmpty()) {
					child.getOptions().forEach(option -> {
						option.setNameLocalizations(lu.getFullLocaleMap("%s.%s.name".formatted(child.getPath(), option.getName()), option.getName()));
						option.setDescriptionLocalizations(lu.getFullLocaleMap("%s.%s.help".formatted(child.getPath(), option.getName()), option.getDescription()));
					});
					subcommandData.addOptions(child.getOptions());
				}

				// Check child name localizations
				if (!child.getNameLocalization().isEmpty()) {
					//Add localizations
					subcommandData.setNameLocalizations(child.getNameLocalization());
				}
				// Check child description localizations
				if (!child.getDescriptionLocalization().isEmpty()) {
					//Add localizations
					subcommandData.setDescriptionLocalizations(child.getDescriptionLocalization());
				}

				// If there's a subcommand group
				if (child.getSubcommandGroup() != null) {
					SubcommandGroupData group = child.getSubcommandGroup();

					SubcommandGroupData newData = groupData.getOrDefault(group.getName(), group)
						.addSubcommands(subcommandData);

					groupData.put(group.getName(), newData);
				}
				// Just add to the command
				else {
					data.addSubcommands(subcommandData);
				}
			}
			if (!groupData.isEmpty())
				data.addSubcommandGroups(groupData.values());
		}

		if (getAccessLevel().isLowerThan(CmdAccessLevel.ADMIN))
			data.setDefaultPermissions(DefaultMemberPermissions.enabledFor(this.getUserPermissions()));
		else
			data.setDefaultPermissions(DefaultMemberPermissions.DISABLED);

		data.setContexts(this.guildOnly ? Set.of(InteractionContextType.GUILD) : Set.of(InteractionContextType.GUILD, InteractionContextType.BOT_DM));

		return data;
	}

	/**
	 * Gets the {@link SlashCommand#children Command.children} for the Command.
	 *
	 * @return The children for the Command
	 */
	public SlashCommand[] getChildren() {
		return children;
	}

	private boolean terminate(SlashCommandEvent event, @NotNull MessageEmbed embed, CommandClient client) {
		return terminate(event, MessageCreateData.fromEmbeds(embed), client);
	}

	private boolean terminate(SlashCommandEvent event, MessageCreateData message, CommandClient client) {
		if (message != null)
			event.reply(message)
				.setEphemeral(true)
				.queue(null, failure -> new ErrorHandler().ignore(ErrorResponse.UNKNOWN_INTERACTION));
		if (client.getListener() != null)
			client.getListener().onTerminatedSlashCommand(event, this);
		return false;
	}

	private boolean terminate(SlashCommandEvent event, CommandClient client) {
		if (client.getListener() != null)
			client.getListener().onTerminatedSlashCommand(event, this);
		return false;
	}

	/**
	 * Gets the specified localizations of slash command names.
	 * @return Slash command name localizations.
	 */
	@NotNull
	public Map<DiscordLocale, String> getNameLocalization() {
		return nameLocalization;
	}

	/**
	 * Gets the specified localizations of slash command descriptions.
	 * @return Slash command description localizations.
	 */
	@NotNull
	public Map<DiscordLocale, String> getDescriptionLocalization() {
		return descriptionLocalization;
	}

	/**
	 * Checks if this Command can only be used in a {@link net.dv8tion.jda.api.entities.Guild Guild}.
	 *
	 * @return {@code true} if this Command can only be used in a Guild, else {@code false} if it can
	 *         be used outside of one
	 */
	public boolean isGuildOnly() {
		return guildOnly;
	}

	// Edit Message(String or MED) and Embed
	protected void editMsg(IReplyCallback event, @NotNull String msg) {
		event.getHook().editOriginal(msg).queue();
	}

	protected void editMsg(IReplyCallback event, @NotNull MessageEditData data) {
		event.getHook().editOriginal(data).queue();
	}

	protected void editEmbed(IReplyCallback event, @NotNull MessageEmbed... embeds) {
		event.getHook().editOriginalEmbeds(embeds).queue();
	}

	// Edit Error
	protected void editError(IReplyCallback event, @NotNull MessageEditData data) {
		event.getHook().editOriginal(data)
			.setComponents()
			.queue(msg -> {
				if (!msg.isEphemeral())
					msg.delete().queueAfter(20, TimeUnit.SECONDS, null, ignoreRest);
			});
	}

	protected void editError(IReplyCallback event, @NotNull MessageEmbed embed) {
		editError(event, new MessageEditBuilder()
			.setContent(lu.getText(event, "misc.temp_msg"))
			.setEmbeds(embed)
			.build()
		);
	}

	protected void editError(IReplyCallback event, @NotNull String path) {
		editError(event, path, null);
	}

	protected void editError(IReplyCallback event, @NotNull String path, String reason) {
		editError(event, bot.getEmbedUtil().getError(event, path, reason));
	}

	protected void editErrorOther(IReplyCallback event, String details) {
		editError(event, bot.getEmbedUtil().getError(event, "errors.error", details));
	}

	protected void editErrorUnknown(IReplyCallback event, String details) {
		editError(event, bot.getEmbedUtil().getError(event, "errors.unknown", details));
	}

	protected void editErrorDatabase(IReplyCallback event, Exception exception, String details) {
		if (exception instanceof SQLException ex) {
			editError(event, bot.getEmbedUtil().getError(event, "errors.database", "%s: %s".formatted(ex.getErrorCode(), details)));
		} else {
			editError(event, bot.getEmbedUtil().getError(event, "errors.database", "%s\n> %s".formatted(details, exception.getMessage())));
		}
	}

	protected void editErrorLimit(IReplyCallback event, String name, int maximum) {
		editError(event, bot.getEmbedUtil().getError(event, "errors.db_limit", "> Maximum *%s*: %d".formatted(name, maximum)));
	}

	// PermError
	protected void editPermError(IReplyCallback event, Permission perm, boolean self) {
		editError(event, MessageEditData.fromCreateData(bot.getEmbedUtil().createPermError(event, perm, self)));
	}


	protected void ignoreExc(RunnableExc runnable) {
		try {
			runnable.run();
		} catch (SQLException ignored) {}
	}

	@FunctionalInterface protected interface RunnableExc { void run() throws SQLException; }

	protected static final Consumer<Throwable> ignoreRest = ignored -> {
		// Nothing to see here
		// Ignore everything
	};
}
