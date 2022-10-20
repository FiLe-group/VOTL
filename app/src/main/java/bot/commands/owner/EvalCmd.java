package bot.commands.owner;

import bot.App;
import bot.objects.CmdAccessLevel;
import bot.objects.constants.CmdCategory;
import bot.objects.constants.Constants;
import bot.utils.exception.CheckException;

import java.util.Collections;
import java.util.Map;
import java.util.Optional;

import com.jagrosh.jdautilities.command.SlashCommand;
import com.jagrosh.jdautilities.command.SlashCommandEvent;
import com.jagrosh.jdautilities.doc.standard.CommandInfo;

import org.codehaus.groovy.runtime.powerassert.PowerAssertionError;

import groovy.lang.Binding;
import groovy.lang.GroovyShell;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.entities.MessageEmbed;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import net.dv8tion.jda.api.interactions.InteractionHook;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;

@CommandInfo
(
	name = "Eval",
	usage = "/eval <code:>",
	description = "Evaluates givven code.",
	requirements = {"Be the bot's owner", "Have fck knowledge of what your're doing with it"}
)
public class EvalCmd extends SlashCommand {

	private final App bot;

	private static final boolean mustSetup = false;
	private static final String MODULE = null;
	private static final CmdAccessLevel ACCESS_LEVEL = CmdAccessLevel.DEV;

	protected static Permission[] userPerms = new Permission[0];
	protected static Permission[] botPerms = new Permission[0];
	
	public EvalCmd(App bot) {
		this.name = "eval";
		this.help = bot.getMsg("bot.owner.eval.help");
		this.ownerCommand = true;
		this.category = CmdCategory.OWNER;
		this.options = Collections.singletonList(
			new OptionData(OptionType.STRING, "code", bot.getMsg("bot.owner.eval.code_description")) 
				.setRequired(true)
			// Я блять ненавижу эту штуку
			// Нужно переделовать через modals, но для этого нужно вначале получить комманду от пользователя
			// позже выслать форму для заполения и только потом обработать ее
			// ............пиздец
		);
		this.bot = bot;
	}

	@Override
	protected void execute(SlashCommandEvent event) {

		event.deferReply(true).queue(
			hook -> {
				try {
					// check access
					bot.getCheckUtil().hasAccess(event, ACCESS_LEVEL)
					// check module enabled
						.moduleEnabled(event, MODULE)
					// check user perms
						.hasPermissions(event, userPerms)
					// check bots perms
						.hasPermissions(event, true, botPerms);
					// check setup
					if (mustSetup) {
						bot.getCheckUtil().guildExists(event, mustSetup);
					}
				} catch (CheckException ex) {
					hook.editOriginal(ex.getEditData()).queue();
					return;
				}

				sendEvalEmbed(event, hook);
			}
		);

	}

	private void sendEvalEmbed(SlashCommandEvent event, InteractionHook hook) {

		String args = event.getOption("code", "", OptionMapping::getAsString).trim();
		if (args.startsWith("```") && args.endsWith("```")) {
			if (args.startsWith("```java")) {
				args = args.substring(4);
			}
			args = args.substring(3, args.length() - 3);
		}

		Map<String, Object> variables = Map.of(
			"bot", bot,
			"event", event,
			"jda", event.getJDA(),
			"guild", (event.isFromGuild() ? event.getGuild() : null),
			"channel", event.getChannel(),
			"client", event.getClient()
		);

		Binding binding = new Binding(variables);
		GroovyShell shell = new GroovyShell(binding);

		long startTime = System.currentTimeMillis();

		String guildId = Optional.ofNullable(event.getGuild()).map(g -> g.getId()).orElse("0");

		try {
			Object resp = shell.evaluate(args);
			String respString = String.valueOf(resp);

			hook.editOriginalEmbeds(formatEvalEmbed(event.getTextChannel(), args, respString,
				bot.getMsg(guildId, "bot.owner.eval.time")
					.replace("{time}", String.valueOf(System.currentTimeMillis() - startTime))
	 			, true)).queue();
		} catch (PowerAssertionError | Exception ex) {
			hook.editOriginalEmbeds(formatEvalEmbed(event.getTextChannel(), args, ex.getMessage(),
				bot.getMsg(guildId, "bot.owner.eval.time")
					.replace("{time}", String.valueOf(System.currentTimeMillis() - startTime))
				, false)).queue();
		}
	}

	@SuppressWarnings("null")
	private MessageEmbed formatEvalEmbed(TextChannel tc, String input, String output, String footer, boolean success) {		
		EmbedBuilder embed = bot.getEmbedUtil().getEmbed()
			.setColor(success ? Constants.COLOR_SUCCESS : Constants.COLOR_FAILURE)
			.addField(bot.getMsg(tc.getGuild().getId(), "bot.owner.eval.input"), String.format(
				"```java\n"+
					"%s\n"+
					"```",
				input
				), false)
			.addField(bot.getMsg(tc.getGuild().getId(), "bot.owner.eval.output"), String.format(
				"```java\n"+
					"%s\n"+
					"```",
				output
				), false)
			.setFooter(footer, null);

		return embed.build();
	}
}