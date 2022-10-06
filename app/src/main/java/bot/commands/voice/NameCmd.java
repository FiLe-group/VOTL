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
		this.help = bot.getMsg("bot.voice.name.help");
		this.category = new Category("voice");
		NameCmd.botPerms = new Permission[]{Permission.MANAGE_CHANNEL};
		NameCmd.bot = bot;
		this.children = new SlashCommand[]{new Set(), new Reset()};
	}

	@Override
	protected void execute(SlashCommandEvent event) {

	}

	private static class Set extends SlashCommand {

		public Set() {
			this.name = "set";
			this.help = bot.getMsg("bot.voice.name.set.help");
			this.options = Collections.singletonList(
				new OptionData(OptionType.STRING, "name", bot.getMsg("bot.voice.name.set.option_description"))
					.setRequired(true)
			);
		}

		@Override
		protected void execute(SlashCommandEvent event) {

			event.deferReply(true).queue(
				hook -> {
					String filName = event.getOption("name", "", OptionMapping::getAsString).trim();

					MessageEditData reply = getReply(event, filName);

					hook.editOriginal(reply).queue();
				}
			);

		}
	}

	private static class Reset extends SlashCommand {

		public Reset() {
			this.name = "reset";
			this.help = bot.getMsg("bot.voice.name.reset.help");
		}

		@Override
		protected void execute(SlashCommandEvent event) {

			event.deferReply(true).queue(
				hook -> {
					String guildId = Optional.ofNullable(event.getGuild()).map(g -> g.getId()).orElse("0");
					String filName = Optional.ofNullable(
						bot.getDBUtil().guildVoiceGetName(guildId)
					).orElse(
						bot.getMsg(guildId, "bot.voice.listener.default_name", Objects.requireNonNull(event.getMember()).getUser().getName(), false)
					);

					MessageEditData reply = getReply(event, filName);

					hook.editOriginal(reply).queue();
				}
			);

		}
	}

	@SuppressWarnings("null")
	@Nonnull
	private static MessageEditData getReply(SlashCommandEvent event, String filName) {

		Member member = Objects.requireNonNull(event.getMember());

		MessageCreateData permission = bot.getCheckUtil().lacksPermissions(event.getTextChannel(), member, true, botPerms);
		if (permission != null)
			return MessageEditData.fromCreateData(permission);

		Guild guild = Objects.requireNonNull(event.getGuild());
		String guildId = guild.getId();

		if (!bot.getDBUtil().isGuild(guildId)) {
			return MessageEditData.fromCreateData(bot.getEmbedUtil().getError(event, "errors.guild_not_setup"));
		}

		if (filName.isEmpty() || filName.length() > 100) {
			return MessageEditData.fromCreateData(bot.getEmbedUtil().getError(event, "bot.voice.name.invalid_range"));
		}

		String memberId = member.getId();

		if (bot.getDBUtil().isVoiceChannel(memberId)) {
			VoiceChannel vc = guild.getVoiceChannelById(bot.getDBUtil().channelGetChannel(memberId));

			vc.getManager().setName(filName).queue();

			if (!bot.getDBUtil().isUser(memberId)) {
				bot.getDBUtil().userAdd(memberId);
			}
			bot.getDBUtil().userSetName(memberId, filName);

			return MessageEditData.fromEmbeds(
				bot.getEmbedUtil().getEmbed(member)
					.setDescription(bot.getMsg(guildId, "bot.voice.name.done").replace("{value}", filName))
					.build()
			);
		} else {
			return MessageEditData.fromContent(bot.getMsg(guildId, "errors.no_channel"));
		}
	}
}
