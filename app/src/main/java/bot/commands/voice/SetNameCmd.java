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
	name = "SetName",
	description = "Sets default name for custom's voice channels.",
	usage = "/setname <name:String from 1 to 100>",
	requirements = "Have 'Manage server' permission"
)
public class SetNameCmd extends SlashCommand {
	
	private final App bot;
	
	private static final boolean mustSetup = true;
	private static final String MODULE = "voice";
	private static final CmdAccessLevel ACCESS_LEVEL = CmdAccessLevel.ADMIN;

	protected static Permission[] userPerms = new Permission[0];
	protected static Permission[] botPerms = new Permission[0];

	public SetNameCmd(App bot) {
		this.name = "setname";
		this.help = bot.getMsg("bot.voice.setname.help");
		this.category = CmdCategory.VOICE;
		SetNameCmd.userPerms = new Permission[]{Permission.MANAGE_SERVER};
		this.options = Collections.singletonList(
			new OptionData(OptionType.STRING, "name", bot.getMsg("bot.voice.setname.option_description"))
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
						.hasPermissions(event.getTextChannel(), event.getMember(), userPerms)
					// check bots perms
						.hasPermissions(event.getTextChannel(), event.getMember(), true, botPerms);
					// check setup
					if (mustSetup) {
						bot.getCheckUtil().guildExists(event);
					}
				} catch (CheckException ex) {
					hook.editOriginal(ex.getEditData()).queue();
					return;
				}

				String filName = event.getOption("name", "Default name", OptionMapping::getAsString).trim();
				sendReply(event, hook, filName);
			}
		);

	}

	private void sendReply(SlashCommandEvent event, InteractionHook hook, String filName) {

		if (filName.isEmpty() || filName.length() > 100) {
			hook.editOriginal(bot.getEmbedUtil().getError(event, "bot.voice.setname.invalid_range")).queue();
			return;
		}

		Member member = Objects.requireNonNull(event.getMember());
		String guildId = Optional.ofNullable(event.getGuild()).map(g -> g.getId()).orElse("0");

		bot.getDBUtil().guildVoiceSetName(guildId, filName);

		hook.editOriginalEmbeds(
			bot.getEmbedUtil().getEmbed(member)
				.setDescription(bot.getMsg(guildId, "bot.voice.setname.done").replace("{value}", filName))
				.build()
		).queue();
	}
}
