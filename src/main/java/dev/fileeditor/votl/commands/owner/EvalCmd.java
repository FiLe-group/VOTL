package dev.fileeditor.votl.commands.owner;

import java.util.Collections;
import java.util.Map;

import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.entities.MessageEmbed;
import net.dv8tion.jda.api.interactions.DiscordLocale;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;

import org.codehaus.groovy.runtime.powerassert.PowerAssertionError;

import dev.fileeditor.votl.App;
import dev.fileeditor.votl.commands.CommandBase;
import dev.fileeditor.votl.objects.command.SlashCommandEvent;
import dev.fileeditor.votl.objects.constants.CmdCategory;
import dev.fileeditor.votl.objects.constants.Constants;
import groovy.lang.Binding;
import groovy.lang.GroovyShell;

public class EvalCmd extends CommandBase {
	
	public EvalCmd(App bot) {
		super(bot);
		this.name = "eval";
		this.path = "bot.owner.eval";
		this.options = Collections.singletonList(
			new OptionData(OptionType.STRING, "code", lu.getText(path+".code_description"), true) 
			// Я блять ненавижу эту штуку
			// Нужно переделовать через modals, но для этого нужно вначале получить комманду от пользователя
			// позже выслать форму для заполения и только потом обработать ее
			// ............пиздец
		);
		this.category = CmdCategory.OWNER;
		this.ownerCommand = true;
		this.guildOnly = false;
	}

	@Override
	protected void execute(SlashCommandEvent event) {
		event.deferReply(true).queue();

		DiscordLocale userLocale = event.getUserLocale();

		String args = event.optString("code");
		if (args == null) {
			return;
		}
		args = args.trim();
		if (args.startsWith("```") && args.endsWith("```")) {
			if (args.startsWith("```java")) {
				args = args.substring(4);
			}
			args = args.substring(3, args.length() - 3);
		}

		Map<String, Object> variables = Map.of(
			"bot", bot,
			"event", event,
			"hook", event.getHook(),
			"jda", event.getJDA(),
			"guild", (event.isFromGuild() ? event.getGuild() : null),
			"channel", event.getChannel(),
			"client", event.getClient()
		);

		Binding binding = new Binding(variables);
		GroovyShell shell = new GroovyShell(binding);

		long startTime = System.currentTimeMillis();

		try {
			Object resp = shell.evaluate(args);
			String respString = String.valueOf(resp);

			editHookEmbed(event, formatEvalEmbed(userLocale, args, respString,
				lu.getLocalized(userLocale, "bot.owner.eval.time")
					.replace("{time}", String.valueOf(System.currentTimeMillis() - startTime))
	 			, true));
		} catch (PowerAssertionError | Exception ex) {
			editHookEmbed(event,formatEvalEmbed(userLocale, args, ex.getMessage(),
				lu.getLocalized(userLocale, "bot.owner.eval.time")
					.replace("{time}", String.valueOf(System.currentTimeMillis() - startTime))
				, false));
		}
	}

	private MessageEmbed formatEvalEmbed(DiscordLocale locale, String input, String output, String footer, boolean success) {		
		EmbedBuilder embed = bot.getEmbedUtil().getEmbed()
			.setColor(success ? Constants.COLOR_SUCCESS : Constants.COLOR_FAILURE)
			.addField(lu.getLocalized(locale, "bot.owner.eval.input"), String.format(
				"```java\n"+
					"%s\n"+
					"```",
				input
				), false)
			.addField(lu.getLocalized(locale, "bot.owner.eval.output"), String.format(
				"```java\n"+
					"%s\n"+
					"```",
				output
				), false)
			.setFooter(footer, null);

		return embed.build();
	}
}