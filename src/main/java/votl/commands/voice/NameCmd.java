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
	name = "Name",
	description = "Sets name for your channel.",
	usage = "/name <name:String>",
	requirements = "Must have created voice channel"
)
public class NameCmd extends CommandBase {

	public NameCmd(App bot) {
		super(bot);
		this.name = "name";
		this.path = "bot.voice.name";
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
			this.path = "bot.voice.name.set";
			this.options = Collections.singletonList(
				new OptionData(OptionType.STRING, "name", lu.getText(path+".option_description"), true)
			);
		}

		@Override
		protected void execute(SlashCommandEvent event) {
			String filName = event.optString("name");
			sendReply(event, filName);
		}

	}

	private class Reset extends CommandBase {

		public Reset(App bot) {
			super(bot);
			this.name = "reset";
			this.path = "bot.voice.name.reset";
		}

		@Override
		protected void execute(SlashCommandEvent event) {
			String filName = Optional.ofNullable(
				bot.getDBUtil().guildVoiceGetName(Objects.requireNonNull(event.getGuild()).getId())
			).orElse(
				lu.getLocalized(event.getGuildLocale(), "bot.voice.listener.default_name", Objects.requireNonNull(event.getMember()).getUser().getName(), false)
			);
			sendReply(event, filName);
		}

	}

	@SuppressWarnings("null")
	private void sendReply(SlashCommandEvent event, String filName) {

		if (filName.isEmpty() || filName.length() > 100) {
			createError(event, "bot.voice.name.invalid_range");
			return;
		}

		Member member = Objects.requireNonNull(event.getMember());
		String memberId = member.getId();

		if (!bot.getDBUtil().isVoiceChannel(memberId)) {
			createError(event, "errors.no_channel");
			return;
		}

		Guild guild = Objects.requireNonNull(event.getGuild());
		DiscordLocale userLocale = event.getUserLocale();

		VoiceChannel vc = guild.getVoiceChannelById(bot.getDBUtil().channelGetChannel(memberId));
		vc.getManager().setName(filName).queue();

		if (!bot.getDBUtil().isUser(memberId)) {
			bot.getDBUtil().userAdd(memberId);
		}
		bot.getDBUtil().userSetName(memberId, filName);

		createReplyEmbed(event, 
			bot.getEmbedUtil().getEmbed(event)
				.setDescription(lu.getLocalized(userLocale, "bot.voice.name.done").replace("{value}", filName))
				.build()
		);
	}
}
