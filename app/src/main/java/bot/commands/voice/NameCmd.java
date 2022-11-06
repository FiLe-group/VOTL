package bot.commands.voice;

import java.util.Collections;
import java.util.Objects;
import java.util.Optional;

import com.jagrosh.jdautilities.doc.standard.CommandInfo;

import bot.App;
import bot.objects.CmdModule;
import bot.objects.command.SlashCommand;
import bot.objects.command.SlashCommandEvent;
import bot.objects.constants.CmdCategory;
import bot.utils.message.LocaleUtil;
import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.channel.concrete.VoiceChannel;
import net.dv8tion.jda.api.interactions.DiscordLocale;
import net.dv8tion.jda.api.interactions.InteractionHook;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;

@CommandInfo(
	name = "Name",
	description = "Sets name for your channel.",
	usage = "/name <name:String>",
	requirements = "Must have created voice channel"
)
public class NameCmd extends SlashCommand {

	public NameCmd(App bot) {
		this.name = "name";
		this.helpPath = "bot.voice.name.help";
		this.children = new SlashCommand[]{new Set(bot.getLocaleUtil()), new Reset()};
		this.botPermissions = new Permission[]{Permission.MANAGE_CHANNEL};
		this.bot = bot;
		this.category = CmdCategory.VOICE;
		this.module = CmdModule.VOICE;
		this.mustSetup = true;
	}

	@Override
	protected void execute(SlashCommandEvent event) {

	}

	private class Set extends SlashCommand {

		public Set(LocaleUtil lu) {
			this.name = "set";
			this.helpPath = "bot.voice.name.set.help";
			this.options = Collections.singletonList(
				new OptionData(OptionType.STRING, "name", lu.getText("bot.voice.name.set.option_description"))
					.setRequired(true)
			);
		}

		@Override
		protected void execute(SlashCommandEvent event) {

			event.deferReply(true).queue(
				hook -> {
					String filName = event.getOption("name", "", OptionMapping::getAsString).trim();
					sendReply(event, hook, filName);
				}
			);

		}
	}

	private class Reset extends SlashCommand {

		public Reset() {
			this.name = "reset";
			this.helpPath = "bot.voice.name.reset.help";
		}

		@Override
		protected void execute(SlashCommandEvent event) {

			event.deferReply(true).queue(
				hook -> {
					String guildId = Optional.ofNullable(event.getGuild()).map(g -> g.getId()).orElse("0");
					String filName = Optional.ofNullable(
						bot.getDBUtil().guildVoiceGetName(guildId)
					).orElse(
						lu.getLocalized(event.getGuildLocale(), "bot.voice.listener.default_name", Objects.requireNonNull(event.getMember()).getUser().getName(), false)
					);

					sendReply(event, hook, filName);
				}
			);

		}
	}

	@SuppressWarnings("null")
	private void sendReply(SlashCommandEvent event, InteractionHook hook, String filName) {

		if (filName.isEmpty() || filName.length() > 100) {
			hook.editOriginal(bot.getEmbedUtil().getError(event, "bot.voice.name.invalid_range")).queue();
			return;
		}

		Member member = Objects.requireNonNull(event.getMember());
		String memberId = member.getId();

		if (!bot.getDBUtil().isVoiceChannel(memberId)) {
			hook.editOriginal(bot.getEmbedUtil().getError(event, "errors.no_channel")).queue();
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

		hook.editOriginalEmbeds(
			bot.getEmbedUtil().getEmbed(event)
				.setDescription(lu.getLocalized(userLocale, "bot.voice.name.done").replace("{value}", filName))
				.build()
		).queue();
	}
}
