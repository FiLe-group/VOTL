package bot.commands.voice;

import java.util.Collections;
import java.util.Objects;
import java.util.Optional;

import com.jagrosh.jdautilities.command.SlashCommand;
import com.jagrosh.jdautilities.command.SlashCommandEvent;
import com.jagrosh.jdautilities.doc.standard.CommandInfo;

import bot.App;
import bot.objects.CmdAccessLevel;
import bot.objects.constants.CmdCategory;
import bot.utils.exception.CheckException;
import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.entities.Member;
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
	
	private final App bot;
	
	private static final boolean mustSetup = true;
	private static final String MODULE = "voice";
	private static final CmdAccessLevel ACCESS_LEVEL = CmdAccessLevel.ADMIN;

	protected static Permission[] userPerms = new Permission[0];
	protected static Permission[] botPerms = new Permission[0];

	public SetLimitCmd(App bot) {
		this.name = "setlimit";
		this.help = bot.getMsg("bot.voice.setlimit.help");
		this.category = CmdCategory.VOICE;
		SetLimitCmd.userPerms = new Permission[]{Permission.MANAGE_SERVER};
		this.options = Collections.singletonList(
			new OptionData(OptionType.INTEGER, "limit", bot.getMsg("bot.voice.setlimit.option_description"))
				.setRequiredRange(0, 99)
				.setRequired(true)
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

		Member member = Objects.requireNonNull(event.getMember());
		String guildId = Optional.ofNullable(event.getGuild()).map(g -> g.getId()).orElse("0");

		bot.getDBUtil().guildVoiceSetLimit(guildId, filLimit);

		hook.editOriginalEmbeds(
			bot.getEmbedUtil().getEmbed(event)
				.setDescription(bot.getMsg(guildId, "bot.voice.setlimit.done").replace("{value}", filLimit.toString()))
				.build()
		).queue();
	}
}
