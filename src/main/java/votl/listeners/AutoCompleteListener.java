package votl.listeners;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import javax.annotation.Nonnull;

import votl.objects.command.CommandClient;
import votl.objects.command.SlashCommand;
import votl.utils.database.DBUtil;
import net.dv8tion.jda.api.events.interaction.command.CommandAutoCompleteInteractionEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import net.dv8tion.jda.api.interactions.commands.Command;

public class AutoCompleteListener extends ListenerAdapter {

	private final List<SlashCommand> cmds;
	private final List<String> groupMasterCmds = List.of("group delete", "group remove", "group rename", "group view");
	private final List<String> groupSyncCmds = List.of("group leave", "group view");

	private DBUtil db;

	public AutoCompleteListener(CommandClient cc, DBUtil dbutil) {
		cmds = cc.getSlashCommands();
		db = dbutil;
	}
		
	@Override
	public void onCommandAutoCompleteInteraction(@Nonnull CommandAutoCompleteInteractionEvent event) {
		if (event.getName().equals("help") && event.getFocusedOption().getName().equals("command")) {
			String value = event.getFocusedOption().getValue().toLowerCase().split(" ")[0];
			List<Command.Choice> choices = cmds.stream()
				.filter(cmd -> cmd.getName().contains(value))
				.map(cmd -> new Command.Choice(cmd.getName(), cmd.getName()))
				.collect(Collectors.toList());
			if (choices.size() > 25) {
				choices.subList(25, choices.size()).clear();
			}
			event.replyChoices(choices).queue();
		}
		else if (groupMasterCmds.contains(event.getFullCommandName()) && event.getFocusedOption().getName().equals("master_group")) {
			List<Map<String, Object>> groups = db.group.getMasterGroups(event.getGuild().getId());
			if (!groups.isEmpty()) {
				List<Command.Choice> choices = groups.stream()
					.map(map -> {
						String groupName = (String) map.get("name");
						Integer groupId = (Integer) map.get("groupId");
						return new Command.Choice(String.format("%s (ID: %s)", groupName, groupId), groupId);
					})
					.collect(Collectors.toList());
				event.replyChoices(choices).queue();
			} else {
				event.replyChoices(Collections.emptyList()).queue();
			}
		}
		else if (groupSyncCmds.contains(event.getFullCommandName()) && event.getFocusedOption().getName().equals("sync_group")) {
			List<Integer> groupIds = db.group.getGuildGroups(event.getGuild().getId());
			if (!groupIds.isEmpty()) {
				List<Command.Choice> choices = groupIds.stream()
					.map(groupId -> {
						String groupName = db.group.getName(groupId);
						return new Command.Choice(String.format("%s (ID: %s)", groupName, groupId), groupId);
					})
					.collect(Collectors.toList());
				event.replyChoices(choices).queue();
			} else {
				event.replyChoices(Collections.emptyList()).queue();
			}
		}
	}
}

