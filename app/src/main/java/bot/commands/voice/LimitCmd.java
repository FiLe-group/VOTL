package bot.commands.voice;

import java.util.Collections;
import java.util.Objects;
import java.util.Optional;

import javax.annotation.Nonnull;

import com.jagrosh.jdautilities.command.SlashCommand;
import com.jagrosh.jdautilities.command.SlashCommandEvent;
import com.jagrosh.jdautilities.doc.standard.CommandInfo;

import bot.App;
import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.channel.concrete.VoiceChannel;
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
		this.help = bot.getMsg("bot.voice.limit.help");
		this.category = new Category("voice");
		LimitCmd.botPerms = new Permission[]{Permission.MANAGE_CHANNEL};
		LimitCmd.bot = bot;
		this.children = new SlashCommand[]{new Set(), new Reset()};
	}

	@Override
	protected void execute(SlashCommandEvent event) {

	}

	private static class Set extends SlashCommand {

		public Set() {
			this.name = "set";
			this.help = bot.getMsg("bot.voice.limit.set.help");
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
						filLimit = event.getOption("limit", 0, OptionMapping::getAsInt);
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

		public Reset() {
			this.name = "reset";
			this.help = bot.getMsg("bot.voice.limit.reset.help");
		}

		@Override
		protected void execute(SlashCommandEvent event) {

			event.deferReply(true).queue(
				hook -> {
					Integer filLimit = Optional.ofNullable(LimitCmd.bot.getDBUtil().guildVoiceGetLimit(Objects.requireNonNull(event.getGuild()).getId())).orElse(0);

					MessageEditData reply = getReply(event, filLimit);

					hook.editOriginal(reply).queue();
				}
			);

		}
	}

	@SuppressWarnings("null")
	@Nonnull
	private static MessageEditData getReply(SlashCommandEvent event, Integer filLimit) {

		Member member = Objects.requireNonNull(event.getMember());

		MessageCreateData permission = bot.getCheckUtil().lacksPermissions(event.getTextChannel(), member, true, botPerms);
		if (permission != null)
			return MessageEditData.fromCreateData(permission);

		Guild guild = Objects.requireNonNull(event.getGuild());
		String guildId = guild.getId();
		

		if (!bot.getDBUtil().isGuild(guildId)) {
			return MessageEditData.fromCreateData(bot.getEmbedUtil().getError(event, "errors.guild_not_setup"));
		}

		String memberId = member.getId();

		if (bot.getDBUtil().isVoiceChannel(memberId)) {
			VoiceChannel vc = guild.getVoiceChannelById(bot.getDBUtil().channelGetChannel(memberId));

			vc.getManager().setUserLimit(filLimit).queue();
			
			if (!bot.getDBUtil().isUser(memberId)) {
				bot.getDBUtil().userAdd(memberId);
			}
			bot.getDBUtil().userSetLimit(memberId, filLimit);

			return MessageEditData.fromEmbeds(
				bot.getEmbedUtil().getEmbed(member)
					.setDescription(bot.getMsg(guildId, "bot.voice.limit.done").replace("{value}", filLimit.toString()))
					.build()
			);
		} else {
			return MessageEditData.fromContent(bot.getMsg(guildId, "errors.no_channel"));
		}
	}
}
