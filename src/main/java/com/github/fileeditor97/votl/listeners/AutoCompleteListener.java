package com.github.fileeditor97.votl.listeners;

import java.util.List;
import java.util.stream.Collectors;

import javax.annotation.Nonnull;

import com.github.fileeditor97.votl.objects.command.CommandClient;
import com.github.fileeditor97.votl.objects.command.SlashCommand;

import net.dv8tion.jda.api.events.interaction.command.CommandAutoCompleteInteractionEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import net.dv8tion.jda.api.interactions.commands.Command;

public class AutoCompleteListener extends ListenerAdapter {

	private final List<SlashCommand> cmds;

	public AutoCompleteListener(CommandClient cc) {
		cmds = cc.getSlashCommands();
	}
		
	@Override
	public void onCommandAutoCompleteInteraction(@Nonnull CommandAutoCompleteInteractionEvent event) {
		if (event.getName().equals("help") && event.getFocusedOption().getName().equals("command")) {
			String value = event.getFocusedOption().getValue().toLowerCase().split(" ")[0];
			List<Command.Choice> choices = cmds.stream()
				.filter(cmd -> cmd.getName().contains(value))
				.map(cmd -> new Command.Choice(cmd.getName(), cmd.getName()))
				.collect(Collectors.toList());
			if (choices != null) {
				event.replyChoices(choices).queue();
			}
		}
	}
}

