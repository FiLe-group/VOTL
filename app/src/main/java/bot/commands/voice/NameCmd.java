package bot.commands.voice;

import java.util.Collections;
import java.util.Objects;
import java.util.Optional;

import com.jagrosh.jdautilities.command.SlashCommand;
import com.jagrosh.jdautilities.command.SlashCommandEvent;
import com.jagrosh.jdautilities.doc.standard.CommandInfo;

import bot.App;
import bot.objects.CmdAccessLevel;
import bot.objects.constants.CmdCategory;
import bot.utils.exception.CheckException;
import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.channel.concrete.VoiceChannel;
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
	
	private static App bot;
	
	private static final boolean mustSetup = true;
	private static final String MODULE = "voice";
	private static final CmdAccessLevel ACCESS_LEVEL = CmdAccessLevel.ALL;

	protected static Permission[] userPerms = new Permission[0];
	protected static Permission[] botPerms = new Permission[0];

	public NameCmd(App bot) {
		this.name = "name";
		this.help = bot.getMsg("bot.voice.name.help");
		this.category = CmdCategory.VOICE;
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
					try {
						// check access
						bot.getCheckUtil().hasAccess(event, ACCESS_LEVEL)
						// check module enabled
							.moduleEnabled(event, MODULE)
						// check user perms
							.hasPermissions(event.getTextChannel(), event.getMember(), userPerms)
						// check bots perms
							.hasPermissions(event.getTextChannel(), event.getMember(), true, botPerms);
						// check setup
						if (mustSetup) {
							bot.getCheckUtil().guildExists(event);
						}
					} catch (CheckException ex) {
						hook.editOriginal(ex.getEditData()).queue();
						return;
					}

					String filName = event.getOption("name", "", OptionMapping::getAsString).trim();
					sendReply(event, hook, filName);
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
					try {
						// check access
						bot.getCheckUtil().hasAccess(event, ACCESS_LEVEL)
						// check module enabled
							.moduleEnabled(event, MODULE)
						// check user perms
							.hasPermissions(event.getTextChannel(), event.getMember(), userPerms)
						// check bots perms
							.hasPermissions(event.getTextChannel(), event.getMember(), true, botPerms);
						// check setup
						if (mustSetup) {
							bot.getCheckUtil().guildExists(event);
						}
					} catch (CheckException ex) {
						hook.editOriginal(ex.getEditData()).queue();
						return;
					}

					String guildId = Optional.ofNullable(event.getGuild()).map(g -> g.getId()).orElse("0");
					String filName = Optional.ofNullable(
						bot.getDBUtil().guildVoiceGetName(guildId)
					).orElse(
						bot.getMsg(guildId, "bot.voice.listener.default_name", Objects.requireNonNull(event.getMember()).getUser().getName(), false)
					);

					sendReply(event, hook, filName);
				}
			);

		}
	}

	@SuppressWarnings("null")
	private static void sendReply(SlashCommandEvent event, InteractionHook hook, String filName) {

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
		String guildId = guild.getId();

		VoiceChannel vc = guild.getVoiceChannelById(bot.getDBUtil().channelGetChannel(memberId));
		vc.getManager().setName(filName).queue();

		if (!bot.getDBUtil().isUser(memberId)) {
			bot.getDBUtil().userAdd(memberId);
		}
		bot.getDBUtil().userSetName(memberId, filName);

		hook.editOriginalEmbeds(
			bot.getEmbedUtil().getEmbed(member)
				.setDescription(bot.getMsg(guildId, "bot.voice.name.done").replace("{value}", filName))
				.build()
		).queue();
	}
}
