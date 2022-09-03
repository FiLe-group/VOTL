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
	name = "Name",
	description = "Sets name for your channel.",
	usage = "/name <name:String>",
	requirements = "Must have created voice channel"
)
public class NameCmd extends SlashCommand {
	
	private final App bot;

	protected Permission[] botPerms;

	public NameCmd(App bot) {
		this.name = "name";
		this.help = bot.getMsg("0", "bot.voice.name.description");
		this.category = new Category("voice");
		this.botPerms = new Permission[]{Permission.MANAGE_CHANNEL};
		this.options = Collections.singletonList(
			new OptionData(OptionType.STRING, "name", bot.getMsg("0", "bot.voice.name.option_description"))
				.setRequired(true)
		);
		this.bot = bot;
	}

	@Override
	protected void execute(SlashCommandEvent event) {

		event.deferReply(true).queue(
			hook -> {
				String filName = event.getOption("name").getAsString();
				MessageEditData reply = getReply(event, filName);

				hook.editOriginal(reply).queue();
			}
		);

	}

	private MessageEditData getReply(SlashCommandEvent event, String filName) {
		MessageCreateData permission = bot.getCheckUtil().lacksPermissions(event.getTextChannel(), event.getMember(), true, botPerms);
		if (permission != null)
			return MessageEditData.fromCreateData(permission);

		if (!bot.getDBUtil().isGuild(event.getGuild().getId())) {
			return MessageEditData.fromCreateData(bot.getEmbedUtil().getError(event, "errors.guild_not_setup"));
		}

		if (bot.getDBUtil().isVoiceChannel(event.getMember().getId())) {
			VoiceChannel vc = event.getGuild().getVoiceChannelById(bot.getDBUtil().channelGetChannel(event.getMember().getId()));

			vc.getManager().setName(filName).queue();

			if (!bot.getDBUtil().isUser(event.getMember().getId())) {
				bot.getDBUtil().userAdd(event.getMember().getId());
			}
			bot.getDBUtil().userSetName(event.getMember().getId(), filName);

			return MessageEditData.fromEmbeds(
				bot.getEmbedUtil().getEmbed(event.getMember())
					.setDescription(bot.getMsg(event.getGuild().getId(), "bot.voice.name.done").replace("{value}", filName))
					.build()
			);
		} else {
			return MessageEditData.fromContent(bot.getMsg(event.getGuild().getId(), "bot.voice.name.no_channel"));
		}
	}
}
