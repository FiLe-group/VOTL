package bot.commands.voice;

import java.util.Collections;

import com.jagrosh.jdautilities.command.SlashCommand;
import com.jagrosh.jdautilities.command.SlashCommandEvent;
import com.jagrosh.jdautilities.doc.standard.CommandInfo;

import bot.App;
import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.entities.channel.concrete.VoiceChannel;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;
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
	
	private static App bot;

	protected static Permission[] botPerms;

	public NameCmd(App bot) {
		this.name = "name";
		this.category = new Category("voice");
		NameCmd.botPerms = new Permission[]{Permission.MANAGE_CHANNEL};
		this.children = new SlashCommand[]{new Set(bot), new Reset(bot)};
		NameCmd.bot = bot;
	}

	@Override
	protected void execute(SlashCommandEvent event) {

	}

	private static class Set extends SlashCommand {

		public Set(App bot) {
			this.name = "set";
			this.help = bot.getMsg("bot.voice.name.set.description");
			this.options = Collections.singletonList(
				new OptionData(OptionType.STRING, "name", bot.getMsg("bot.voice.name.set.option_description"))
					.setRequired(true)
			);
		}

		@Override
		protected void execute(SlashCommandEvent event) {

			event.deferReply(true).queue(
				hook -> {
					String filName = event.getOption("name", OptionMapping::getAsString).trim();

					MessageEditData reply = getReply(event, filName);

					hook.editOriginal(reply).queue();
				}
			);

		}
	}

	private static class Reset extends SlashCommand {

		public Reset(App bot) {
			this.name = "reset";
			this.help = bot.getMsg("bot.voice.name.reset.description");
		}

		@Override
		protected void execute(SlashCommandEvent event) {

			event.deferReply(true).queue(
				hook -> {
					String filName = bot.getDBUtil().guildVoiceGetName(event.getGuild().getId());
					if (filName == null) {
						filName = bot.getMsg(event.getGuild().getId(), "bot.voice.listener.default_name", event.getMember().getUser().getName(), false);
					}

					MessageEditData reply = getReply(event, filName);

					hook.editOriginal(reply).queue();
				}
			);

		}
	}

	private static MessageEditData getReply(SlashCommandEvent event, String filName) {
		MessageCreateData permission = bot.getCheckUtil().lacksPermissions(event.getTextChannel(), event.getMember(), true, botPerms);
		if (permission != null)
			return MessageEditData.fromCreateData(permission);

		if (!bot.getDBUtil().isGuild(event.getGuild().getId())) {
			return MessageEditData.fromCreateData(bot.getEmbedUtil().getError(event, "errors.guild_not_setup"));
		}

		if (filName.isEmpty() || filName.length() > 100) {
			return MessageEditData.fromCreateData(bot.getEmbedUtil().getError(event, "bot.voice.name.invalid_range"));
		}

		String memberId = event.getMember().getId();

		if (bot.getDBUtil().isVoiceChannel(memberId)) {
			VoiceChannel vc = event.getGuild().getVoiceChannelById(bot.getDBUtil().channelGetChannel(memberId));

			vc.getManager().setName(filName).queue();

			if (!bot.getDBUtil().isUser(memberId)) {
				bot.getDBUtil().userAdd(memberId);
			}
			bot.getDBUtil().userSetName(memberId, filName);

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
