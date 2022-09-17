package bot.commands.voice;

import java.util.Collections;

import com.jagrosh.jdautilities.command.SlashCommand;
import com.jagrosh.jdautilities.command.SlashCommandEvent;
import com.jagrosh.jdautilities.doc.standard.CommandInfo;

import bot.App;
import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.entities.VoiceChannel;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;
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
	
	private static App bot;

	protected static Permission[] botPerms;

	public LimitCmd(App bot) {
		this.name = "limit";
		this.category = new Category("voice");
		LimitCmd.botPerms = new Permission[]{Permission.MANAGE_CHANNEL};
		this.children = new SlashCommand[]{new Set(bot), new Reset(bot)};
		LimitCmd.bot = bot;
	}

	@Override
	protected void execute(SlashCommandEvent event) {

	}

	private static class Set extends SlashCommand {

		public Set(App bot) {
			this.name = "set";
			this.help = bot.getMsg("bot.voice.limit.set.description");
			this.options = Collections.singletonList(
				new OptionData(OptionType.INTEGER, "limit", bot.getMsg("bot.voice.limit.set.option_description"))
					.setRequiredRange(0, 99)
					.setRequired(true)
			);
		}

		@Override
		protected void execute(SlashCommandEvent event) {

			event.deferReply(true).queue(
				hook -> {
					Integer filLimit;
					MessageEditData reply;
					try {
						filLimit = event.getOption("limit", OptionMapping::getAsInt);
						reply = getReply(event, filLimit);
					} catch (Exception e) {
						reply = MessageEditData.fromCreateData(LimitCmd.bot.getEmbedUtil().getError(event, "errors.request_error", e.toString()));
					}

					hook.editOriginal(reply).queue();
				}
			);

		}
	}

	private static class Reset extends SlashCommand {

		public Reset(App bot) {
			this.name = "reset";
			this.help = bot.getMsg("bot.voice.limit.reset.description");
		}

		@Override
		protected void execute(SlashCommandEvent event) {

			event.deferReply(true).queue(
				hook -> {
					Integer filLimit = LimitCmd.bot.getDBUtil().guildVoiceGetLimit(event.getGuild().getId());
					if (filLimit == null) {
						filLimit = 0;
					}

					MessageEditData reply = getReply(event, filLimit);

					hook.editOriginal(reply).queue();
				}
			);

		}
	}

	private static MessageEditData getReply(SlashCommandEvent event, Integer filLimit) {
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
