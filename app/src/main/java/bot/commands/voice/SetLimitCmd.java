package bot.commands.voice;

import java.util.Collections;

import com.jagrosh.jdautilities.command.SlashCommand;
import com.jagrosh.jdautilities.command.SlashCommandEvent;
import com.jagrosh.jdautilities.doc.standard.CommandInfo;

import bot.App;
import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import net.dv8tion.jda.api.utils.messages.MessageCreateData;
import net.dv8tion.jda.api.utils.messages.MessageEditData;

@CommandInfo(
	name = "SetLimit",
	description = "Sets default user limit for server's voice channels.",
	usage = "/setlimit <limit:Integer from 0 to 99>",
	requirements = "Have 'Manage server' permission"
)
public class SetLimitCmd extends SlashCommand {
	
	private final App bot;

	protected Permission[] userPerms;

	public SetLimitCmd(App bot) {
		this.name = "setlimit";
		this.help = bot.getMsg("0", "bot.voice.setlimit.description");
		this.category = new Category("voice");
		this.userPerms = new Permission[]{Permission.MANAGE_SERVER};
		this.options = Collections.singletonList(
			new OptionData(OptionType.INTEGER, "limit", bot.getMsg("0", "bot.voice.setlimit.option_description"))
				.setRequiredRange(0, 99)
				.setRequired(true)
		);
		this.bot = bot;
	}

	@Override
	protected void execute(SlashCommandEvent event) {

		event.deferReply(true).queue(
			hook -> {
				Integer filLimit;
				MessageEditData reply;
				try {
					filLimit = event.getOption("limit").getAsInt();
					reply = getReply(event, filLimit);
				} catch (Exception e) {
					reply = MessageEditData.fromCreateData(bot.getEmbedUtil().getError(event, "errors.request_error", e.toString()));
				}

				hook.editOriginal(reply).queue();
			}
		);

	}

	private MessageEditData getReply(SlashCommandEvent event, Integer filLimit) {
		MessageCreateData permission = bot.getCheckUtil().lacksPermissions(event.getTextChannel(), event.getMember(), userPerms);
		if (permission != null)
			return MessageEditData.fromCreateData(permission);

		if (!bot.getDBUtil().isGuild(event.getGuild().getId())) {
			return MessageEditData.fromCreateData(bot.getEmbedUtil().getError(event, "errors.guild_not_setup"));
		}

		bot.getDBUtil().guildVoiceSetLimit(event.getGuild().getId(), filLimit);

		return MessageEditData.fromEmbeds(
			bot.getEmbedUtil().getEmbed(event.getMember())
				.setDescription(bot.getMsg(event.getGuild().getId(), "bot.voice.setlimit.done").replace("{value}", filLimit.toString()))
				.build()
		);
	}
}
