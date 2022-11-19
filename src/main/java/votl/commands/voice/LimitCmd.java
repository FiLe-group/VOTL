package votl.commands.voice;

import java.util.Collections;
import java.util.Objects;
import java.util.Optional;

import votl.App;
import votl.commands.CommandBase;
import votl.objects.CmdModule;
import votl.objects.command.SlashCommand;
import votl.objects.command.SlashCommandEvent;
import votl.objects.constants.CmdCategory;

import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.channel.concrete.VoiceChannel;
import net.dv8tion.jda.api.interactions.DiscordLocale;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;

import com.jagrosh.jdautilities.doc.standard.CommandInfo;

@CommandInfo(
	name = "Limit",
	description = "Sets limit for your channel.",
	usage = "/limit <limit:Integer from 0 to 99>",
	requirements = "Must have created voice channel"
)
public class LimitCmd extends CommandBase {

	public LimitCmd(App bot) {
		super(bot);
		this.name = "limit";
		this.path = "bot.voice.limit";
		this.children = new SlashCommand[]{new Set(bot), new Reset(bot)};
		this.botPermissions = new Permission[]{Permission.MANAGE_CHANNEL};
		this.category = CmdCategory.VOICE;
		this.module = CmdModule.VOICE;
		this.mustSetup = true;
	}

	@Override
	protected void execute(SlashCommandEvent event) {

	}

	private class Set extends CommandBase {

		public Set(App bot) {
			super(bot);
			this.name = "set";
			this.path = "bot.voice.limit.set";
			this.options = Collections.singletonList(
				new OptionData(OptionType.INTEGER, "limit", lu.getText(path+".option_description"), true)
					.setRequiredRange(0, 99)
			);
		}

		@Override
		protected void execute(SlashCommandEvent event) {
			Integer filLimit = event.optInteger("limit");
			sendReply(event, filLimit);
		}

	}

	private class Reset extends CommandBase {

		public Reset(App bot) {
			super(bot);
			this.name = "reset";
			this.path = "bot.voice.limit.reset";
		}

		@Override
		protected void execute(SlashCommandEvent event) {
			Integer filLimit = Optional.ofNullable(bot.getDBUtil().guildVoiceGetLimit(Objects.requireNonNull(event.getGuild()).getId())).orElse(0);
			sendReply(event, filLimit);
		}

	}

	@SuppressWarnings("null")
	private void sendReply(SlashCommandEvent event, Integer filLimit) {

		Member member = Objects.requireNonNull(event.getMember());
		String memberId = member.getId();

		if (!bot.getDBUtil().isVoiceChannel(memberId)) {
			createError(event, "errors.no_channel");
			return;
		}

		Guild guild = Objects.requireNonNull(event.getGuild());
		DiscordLocale userLocale = event.getUserLocale();

		VoiceChannel vc = guild.getVoiceChannelById(bot.getDBUtil().channelGetChannel(memberId));
		vc.getManager().setUserLimit(filLimit).queue();
		
		if (!bot.getDBUtil().isUser(memberId)) {
			bot.getDBUtil().userAdd(memberId);
		}
		bot.getDBUtil().userSetLimit(memberId, filLimit);

		createReplyEmbed(event, 
			bot.getEmbedUtil().getEmbed(event)
				.setDescription(lu.getLocalized(userLocale, "bot.voice.limit.done").replace("{value}", filLimit.toString()))
				.build()
		);
	}
}
