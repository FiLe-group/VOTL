package dev.fileeditor.votl.listeners;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

import net.dv8tion.jda.api.events.interaction.command.CommandAutoCompleteInteractionEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import net.dv8tion.jda.api.interactions.commands.Command.Choice;

import dev.fileeditor.votl.base.command.CommandClient;
import dev.fileeditor.votl.base.command.SlashCommand;
import dev.fileeditor.votl.utils.database.DBUtil;
import dev.fileeditor.votl.utils.message.MessageUtil;
import org.jetbrains.annotations.NotNull;

public class AutoCompleteListener extends ListenerAdapter {

	private final List<SlashCommand> commands;

	private final DBUtil db;

	public AutoCompleteListener(CommandClient cc, DBUtil dbutil) {
		commands = cc.getSlashCommands();
		db = dbutil;
	}
		
	@Override
	public void onCommandAutoCompleteInteraction(@NotNull CommandAutoCompleteInteractionEvent event) {
		String cmdName = event.getFullCommandName();
		String focusedOption = event.getFocusedOption().getName();
		if (cmdName.equals("help") && focusedOption.equals("command")) {
			String value = event.getFocusedOption().getValue().toLowerCase().split(" ")[0];
			List<Choice> choices = commands.stream()
				.filter(cmd -> cmd.getName().contains(value))
				.map(cmd -> new Choice(cmd.getName(), cmd.getName()))
				.collect(Collectors.toList());
			if (choices.size() > 25) {
				choices.subList(25, choices.size()).clear();
			}
			event.replyChoices(choices).queue();
		}
		else if (focusedOption.equals("group_owned")) {
			assert event.getGuild() != null;
			List<Integer> groupIds = db.group.getOwnedGroups(event.getGuild().getIdLong());
			if (groupIds.isEmpty()) {
				event.replyChoices(Collections.emptyList()).queue();
			} else {
				List<Choice> choices = groupIds.stream()
					.map(groupId -> {
						String groupName = db.group.getName(groupId);
						return new Choice("%s (ID: %s)".formatted(groupName, groupId), groupId);
					})
					.collect(Collectors.toList());
				event.replyChoices(choices).queue();
			}
		}
		else if (focusedOption.equals("group_joined")) {
			assert event.getGuild() != null;
			List<Integer> groupIds = db.group.getGuildGroups(event.getGuild().getIdLong());
			if (groupIds.isEmpty()) {
				event.replyChoices(Collections.emptyList()).queue();
			} else {
				List<Choice> choices = groupIds.stream()
					.map(groupId -> {
						String groupName = db.group.getName(groupId);
						return new Choice("%s (ID: %s)".formatted(groupName, groupId), groupId);
					})
					.collect(Collectors.toList());
				event.replyChoices(choices).queue();
			}
		}
		else if (focusedOption.equals("group")) {
			assert event.getGuild() != null;
			List<Integer> groupIds = new ArrayList<>();
			groupIds.addAll(db.group.getOwnedGroups(event.getGuild().getIdLong()));
			groupIds.addAll(db.group.getManagedGroups(event.getGuild().getIdLong()));
			if (groupIds.isEmpty()) {
				event.replyChoices(Collections.emptyList()).queue();
			} else {
				List<Choice> choices = groupIds.stream()
					.map(groupId -> {
						String groupName = db.group.getName(groupId);
						return new Choice("%s (ID: %s)".formatted(groupName, groupId), groupId);
					})
					.toList();
				event.replyChoices(choices).queue();
			}
		}
		else if (focusedOption.equals("panel_id")) {
			assert event.getGuild() != null;
			String value = event.getFocusedOption().getValue();
			long guildId = event.getGuild().getIdLong();
			if (value.isBlank()) {
				// if input is blank, show max 25 choices
				List<Choice> choices = db.ticketPanels.getPanelsText(guildId)
					.entrySet()
					.stream()
					.map(panel -> new Choice("%s | %s".formatted(panel.getKey(), MessageUtil.limitString(panel.getValue(), 90)), panel.getKey()))
					.toList();
				event.replyChoices(choices).queue();
				return;
			}

			Integer id = null;
			try {
				id = Integer.valueOf(value);
			} catch(NumberFormatException ignored) {}
			if (id != null) {
				// if able to convert input to Integer
				String title = db.ticketPanels.getPanelTitle(id);
				if (title != null) {
					// if found panel with matching ID
					event.replyChoice("%s | %s".formatted(id, MessageUtil.limitString(title, 80)), id).queue();
					return;
				}
			}
			event.replyChoices(Collections.emptyList()).queue();
		}
		else if (focusedOption.equals("tag_id")) {
			assert event.getGuild() != null;
			String value = event.getFocusedOption().getValue();
			long guildId = event.getGuild().getIdLong();
			if (value.isBlank()) {
				// if input is blank, show max 25 choices
				List<Choice> choices = db.ticketTags.getTagsText(guildId).entrySet().stream()
					.map(panel -> new Choice("%s | %s".formatted(panel.getKey(), panel.getValue()), panel.getKey()))
					.collect(Collectors.toList());
				event.replyChoices(choices).queue();
				return;
			}

			Integer id = null;
			try {
				id = Integer.valueOf(value);
			} catch(NumberFormatException ignored) {}
			if (id != null) {
				// if able to convert input to Integer
				String title = db.ticketTags.getTagText(id);
				if (title != null) {
					// if found panel with matching ID
					event.replyChoice("%s - %s".formatted(id, title), id).queue();
					return;
				}
			}
			event.replyChoices(Collections.emptyList()).queue();
		}
	}
}

