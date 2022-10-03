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
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import net.dv8tion.jda.api.utils.messages.MessageCreateData;
import net.dv8tion.jda.api.utils.messages.MessageEditData;

@CommandInfo(
	name = "SetName",
	description = "Sets default name for custom's voice channels.",
	usage = "/setname <name:String from 1 to 100>",
	requirements = "Have 'Manage server' permission"
)
public class SetNameCmd extends SlashCommand {
	
	private final App bot;

	protected Permission[] userPerms;

	public SetNameCmd(App bot) {
		this.name = "setname";
		this.help = bot.getMsg("bot.voice.setname.description");
		this.category = new Category("voice");
		this.userPerms = new Permission[]{Permission.MANAGE_SERVER};
		this.options = Collections.singletonList(
			new OptionData(OptionType.STRING, "name", bot.getMsg("bot.voice.setname.option_description"))
				.setRequired(true)
		);
		this.bot = bot;
	}

	@Override
	protected void execute(SlashCommandEvent event) {

		event.deferReply(true).queue(
			hook -> {
				String filName = event.getOption("name", "Default name", OptionMapping::getAsString).trim();
				MessageEditData reply = getReply(event, filName);

				hook.editOriginal(reply).queue();
			}
		);

	}

	@Nonnull
	private MessageEditData getReply(SlashCommandEvent event, String filName) {

		Member member = Objects.requireNonNull(event.getMember());

		MessageCreateData permission = bot.getCheckUtil().lacksPermissions(event.getTextChannel(), member, userPerms);
		if (permission != null)
			return MessageEditData.fromCreateData(permission);

		String guildId = Optional.ofNullable(event.getGuild()).map(g -> g.getId()).orElse("0");

		if (!bot.getDBUtil().isGuild(guildId)) {
			return MessageEditData.fromCreateData(bot.getEmbedUtil().getError(event, "errors.guild_not_setup"));
		}

		if (filName.isEmpty() || filName.length() > 100) {
			return MessageEditData.fromCreateData(bot.getEmbedUtil().getError(event, "bot.voice.setname.invalid_range"));
		}

		bot.getDBUtil().guildVoiceSetName(guildId, filName);

		return MessageEditData.fromEmbeds(
			bot.getEmbedUtil().getEmbed(member)
				.setDescription(bot.getMsg(guildId, "bot.voice.setname.done").replace("{value}", filName))
				.build()
		);
	}
}
