package dev.fileeditor.votl.commands.owner;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

import dev.fileeditor.votl.base.command.SlashCommand;
import dev.fileeditor.votl.base.command.SlashCommandEvent;
import dev.fileeditor.votl.objects.AccessPermission;
import dev.fileeditor.votl.objects.constants.CmdCategory;
import dev.fileeditor.votl.objects.constants.Constants;
import dev.fileeditor.votl.utils.message.MessageUtil;

import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.entities.MessageEmbed;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import net.dv8tion.jda.api.utils.FileUpload;
import net.dv8tion.jda.api.utils.messages.MessageEditBuilder;

import org.codehaus.groovy.runtime.powerassert.PowerAssertionError;

import groovy.lang.Binding;
import groovy.lang.GroovyShell;

public class EvalCmd extends SlashCommand {
	private static final int OUTPUT_LIMIT = 1000;

	public EvalCmd() {
		this.name = "eval";
		this.path = "bot.owner.eval";
		this.options = List.of(
			new OptionData(OptionType.STRING, "code", lu.getText(path+".code.help"), true) 
		);
		this.category = CmdCategory.OWNER;
		this.requiredPermission = AccessPermission.DEV;
		this.ephemeral = true;
	}

	@Override
	protected void execute(SlashCommandEvent event) {
		String args = event.optString("code");
		if (args == null) {
			return;
		}
		args = args.trim();
		if (args.startsWith("```") && args.endsWith("```")) {
			args = args.substring(3, args.length() - 3);
			if (args.startsWith("java")) {
				args = args.substring(4);
			}
		}

		Map<String, Object> variables = Map.of(
			"bot", bot,
			"event", event,
			"jda", event.getJDA(),
			"guild", (event.getGuild() != null ? event.getGuild() : "null"),
			"client", event.getClient()
		);

		Binding binding = new Binding(variables);
		GroovyShell shell = new GroovyShell(binding);

		long startTime = System.currentTimeMillis();

		String reply;
		boolean success;
		try {
			reply = String.valueOf(shell.evaluate(args));
			success = true;
		} catch (PowerAssertionError | Exception ex) {
			reply = ex.getMessage();
			success = false;
		}

		String footer = lu.getText(event, "bot.owner.eval.time", String.valueOf(System.currentTimeMillis() - startTime));

		MessageEditBuilder builder = new MessageEditBuilder()
			.setEmbeds(formatEvalEmbed(event, args, reply, footer, success));
		if (reply != null && reply.length() > OUTPUT_LIMIT) {
			builder.setFiles(FileUpload.fromData(reply.getBytes(StandardCharsets.UTF_8), "output.txt"));
		}
		editMsg(event, builder.build());
	}

	private MessageEmbed formatEvalEmbed(SlashCommandEvent event, String input, String output, String footer, boolean success) {
		boolean attached = output != null && output.length() > OUTPUT_LIMIT;

		EmbedBuilder embed = bot.getEmbedUtil().getEmbed()
			.setColor(success ? Constants.COLOR_SUCCESS : Constants.COLOR_FAILURE)
			.addField(lu.getText(event, "bot.owner.eval.input"),
				"```groovy\n"+MessageUtil.limitString(input, 1000)+"\n```",
				false)
			.addField(lu.getText(event, "bot.owner.eval.output"),
				attached
					? lu.getText(event, "bot.owner.eval.attached")
					: "```groovy\n"+MessageUtil.limitString(output, 1000)+"\n```",
				false)
			.setFooter(footer, null);

		return embed.build();
	}
}