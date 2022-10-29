package bot.commands.voice;

import java.util.Collections;
import java.util.Optional;

import com.jagrosh.jdautilities.doc.standard.CommandInfo;

import bot.App;
import bot.objects.CmdAccessLevel;
import bot.objects.command.SlashCommand;
import bot.objects.command.SlashCommandEvent;
import bot.objects.constants.CmdCategory;
import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.interactions.DiscordLocale;
import net.dv8tion.jda.api.interactions.InteractionHook;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;

@CommandInfo(
	name = "SetLimit",
	description = "Sets default user limit for server's voice channels.",
	usage = "/setlimit <limit:Integer from 0 to 99>",
	requirements = "Have 'Manage server' permission"
)
public class SetLimitCmd extends SlashCommand {

	public SetLimitCmd(App bot) {
		this.name = "setlimit";
		this.helpPath = "bot.voice.setlimit.help";
		this.options = Collections.singletonList(
			new OptionData(OptionType.INTEGER, "limit", bot.getLocaleUtil().getText("bot.voice.setlimit.option_description"))
				.setRequiredRange(0, 99)
				.setRequired(true)
		);
		this.botPermissions = new Permission[]{Permission.MANAGE_SERVER};
		this.bot = bot;
		this.category = CmdCategory.VOICE;
		this.module = "voice";
		this.accessLevel = CmdAccessLevel.ADMIN;
		this.mustSetup = true;
	}

	@Override
	protected void execute(SlashCommandEvent event) {

		event.deferReply(true).queue(
			hook -> {
				try {
					Integer filLimit = event.getOption("limit", OptionMapping::getAsInt);
					sendReply(event, hook, filLimit);
				} catch (Exception e) {
					hook.editOriginal(bot.getEmbedUtil().getError(event, "errors.request_error", e.toString()));
				}
			}
		);

	}

	private void sendReply(SlashCommandEvent event, InteractionHook hook, Integer filLimit) {

		String guildId = Optional.ofNullable(event.getGuild()).map(g -> g.getId()).orElse("0");
		DiscordLocale userLocale = event.getUserLocale();

		bot.getDBUtil().guildVoiceSetLimit(guildId, filLimit);

		hook.editOriginalEmbeds(
			bot.getEmbedUtil().getEmbed(event)
				.setDescription(lu.getLocalized(userLocale, "bot.voice.setlimit.done").replace("{value}", filLimit.toString()))
				.build()
		).queue();
	}
}
