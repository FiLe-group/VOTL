package votl.commands.voice;

import java.util.Collections;
import java.util.Optional;

import votl.App;
import votl.commands.CommandBase;
import votl.objects.CmdAccessLevel;
import votl.objects.CmdModule;
import votl.objects.command.SlashCommandEvent;
import votl.objects.constants.CmdCategory;

import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.interactions.DiscordLocale;
import net.dv8tion.jda.api.interactions.InteractionHook;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;

import com.jagrosh.jdautilities.doc.standard.CommandInfo;

@CommandInfo(
	name = "SetLimit",
	description = "Sets default user limit for server's voice channels.",
	usage = "/setlimit <limit:Integer from 0 to 99>",
	requirements = "Have 'Manage server' permission"
)
public class SetLimitCmd extends CommandBase {

	public SetLimitCmd(App bot) {
		super(bot);
		this.name = "setlimit";
		this.path = "bot.voice.setlimit";
		this.options = Collections.singletonList(
			new OptionData(OptionType.INTEGER, "limit", lu.getText(path+".option_description"), true)
				.setRequiredRange(0, 99)
		);
		this.botPermissions = new Permission[]{Permission.MANAGE_SERVER};
		this.category = CmdCategory.VOICE;
		this.module = CmdModule.VOICE;
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
