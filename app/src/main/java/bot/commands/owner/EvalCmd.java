package bot.commands.owner;

import bot.App;

import java.awt.Color;
import java.util.Map;

import com.jagrosh.jdautilities.command.Command;
import com.jagrosh.jdautilities.command.CommandEvent;
import com.jagrosh.jdautilities.doc.standard.CommandInfo;

import org.codehaus.groovy.runtime.powerassert.PowerAssertionError;

import groovy.lang.Binding;
import groovy.lang.GroovyShell;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.entities.TextChannel;

@CommandInfo
(
	name = "Eval",
	usage = "Eval <java code>",
	description = "Evaluates givven code.",
	requirements = {"Be the bot owner"}
)
public class EvalCmd extends Command {

	private final App bot;
	
	public EvalCmd(App bot, Category cat) {
		this.name = "eval";
		this.help = "выполняет указанный код (язык Groovy)";
		this.guildOnly = false;
		this.ownerCommand = true;
		this.category = cat;
		this.botPermissions = new Permission[]{Permission.MESSAGE_EMBED_LINKS};
		this.botMissingPermMessage = "%s, мне нужно разрешение `%s` в %s чтобы выполнить это!";
		this.bot = bot;
	}

	@Override
	protected void execute(CommandEvent event) {
		if (event.getMessage().getGuild().getSelfMember().hasPermission(event.getTextChannel(), Permission.MESSAGE_MANAGE))
			event.getMessage().delete().queue();

		if (event.getArgs().length() == 0) {
			bot.getEmbedUtil().sendError(event.getTextChannel(), event.getMember(), bot.getMsg(event.getGuild().getId(), "bot.owner.eval.no_args"));
			return;
		}

		String args = event.getArgs();
		args = args.trim();
		if ( (args.startsWith("```java") || args.startsWith("```") ) && args.endsWith("```")) {
			args = args.substring(7, args.length() - 3);
		}

		Map<String, Object> variables = Map.of(
			"bot", bot,
			"jda", event.getJDA(),
			"guild", event.getGuild(),
			"channel", event.getChannel(),
			"message", event.getMessage()
		);

		Binding binding = new Binding(variables);
		GroovyShell shell = new GroovyShell(binding);

		long startTime = System.currentTimeMillis();

		try {
			Object resp = shell.evaluate(args);
			String respString = String.valueOf(resp);

			sendEvalEmbed(event.getTextChannel(), args, respString,
				bot.getMsg(event.getGuild().getId(), "bot.owner.eval.time")
					.replace("{time}", String.valueOf(System.currentTimeMillis() - startTime))
	 			, true);
		} catch (PowerAssertionError | Exception ex) {
			sendEvalEmbed(event.getTextChannel(), args, ex.getMessage(),
				bot.getMsg(event.getGuild().getId(), "bot.owner.eval.time")
					.replace("{time}", String.valueOf(System.currentTimeMillis() - startTime))
				, false);
		}
	}

	private void sendEvalEmbed(TextChannel tc, String input, String output, String footer, boolean success) {
		String newMsg = input;

		String overflow = null;

		if (newMsg.length() > 2000) {
			overflow = newMsg.substring(1999);
			newMsg = newMsg.substring(0, 1999);
		}

		EmbedBuilder embed = bot.getEmbedUtil().getEmbed()
			.setColor(success ? Color.GREEN : Color.RED)
			.addField(bot.getMsg(tc.getGuild().getId(), "bot.owner.eval.input"), String.format(
				"```java\n"+
					"%s\n"+
					"```",
				newMsg
				), false)
			.addField(bot.getMsg(tc.getGuild().getId(), "bot.owner.eval.output"), String.format(
				"```java\n"+
					"%s\n"+
					"```",
				output
				), false)
			.setFooter(footer, null);

		tc.sendMessageEmbeds(embed.build()).queue();
		if(overflow != null)
			sendEvalEmbed(tc, input, output, footer, success);
	}
}