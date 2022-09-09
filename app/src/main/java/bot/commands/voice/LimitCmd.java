package bot.commands.voice;

import java.util.Collections;

import com.jagrosh.jdautilities.command.SlashCommand;
import com.jagrosh.jdautilities.command.SlashCommandEvent;
import com.jagrosh.jdautilities.doc.standard.CommandInfo;

import bot.App;
import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.entities.VoiceChannel;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import net.dv8tion.jda.api.utils.messages.MessageCreateData;
import net.dv8tion.jda.api.utils.messages.MessageEditData;

@CommandInfo(
	name = "Limit",
	description = "Sets limit for your channel.",
	usage = "/limit <limit:Integer from 0 to 99>",
	requirements = "Must have created voice channel"
)
public class LimitCmd extends SlashCommand {
	
	private final App bot;

	protected Permission[] botPerms;

	public LimitCmd(App bot) {
		this.name = "limit";
		this.help = bot.getMsg("0", "bot.voice.limit.description");
		this.category = new Category("voice");
		this.botPerms = new Permission[]{Permission.MANAGE_CHANNEL};
		this.options = Collections.singletonList(
			new OptionData(OptionType.INTEGER, "limit", bot.getMsg("0", "bot.voice.limit.option_description"))
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
		MessageCreateData permission = bot.getCheckUtil().lacksPermissions(event.getTextChannel(), event.getMember(), true, botPerms);
		if (permission != null)
			return MessageEditData.fromCreateData(permission);

		if (!bot.getDBUtil().isGuild(event.getGuild().getId())) {
			return MessageEditData.fromCreateData(bot.getEmbedUtil().getError(event, "errors.guild_not_setup"));
		}

		String memberId = event.getMember().getId();

		if (bot.getDBUtil().isVoiceChannel(memberId)) {
			VoiceChannel vc = event.getGuild().getVoiceChannelById(bot.getDBUtil().channelGetChannel(memberId));

			vc.getManager().setUserLimit(filLimit).queue();
			
			if (!bot.getDBUtil().isUser(memberId)) {
				bot.getDBUtil().userAdd(memberId);
			}
			bot.getDBUtil().userSetLimit(memberId, filLimit);

			return MessageEditData.fromEmbeds(
				bot.getEmbedUtil().getEmbed(event.getMember())
					.setDescription(bot.getMsg(event.getGuild().getId(), "bot.voice.limit.done").replace("{value}", filLimit.toString()))
					.build()
			);
		} else {
			return MessageEditData.fromContent(bot.getMsg(event.getGuild().getId(), "bot.voice.limit.no_channel"));
		}
	}
}
