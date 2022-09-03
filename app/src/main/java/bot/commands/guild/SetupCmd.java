package bot.commands.guild;

import com.jagrosh.jdautilities.command.SlashCommand;
import com.jagrosh.jdautilities.command.SlashCommandEvent;
import com.jagrosh.jdautilities.doc.standard.CommandInfo;

import bot.App;
import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.entities.MessageEmbed;
import net.dv8tion.jda.api.utils.messages.MessageCreateData;
import net.dv8tion.jda.api.utils.messages.MessageEditData;

@CommandInfo
(
	name = "setup",
	description = "Setup menu for this server.",
	usage = "/setup",
	requirements = "Have 'Manage Server' or be an owner permission"
)
public class SetupCmd extends SlashCommand {
	
	private final App bot;

	protected Permission[] userPerms;

	public SetupCmd(App bot) {
		this.name = "setup";
		this.help = "bot.guild.setup.description";
		this.userPerms = new Permission[]{Permission.MANAGE_SERVER};
		//this.botPerms = new Permission[]{Permission.MESSAGE_EMBED_LINKS};
		this.bot = bot;
	}

	@Override
	protected void execute(SlashCommandEvent event) {

		event.deferReply(true).queue(
			hook -> {
				MessageEditData reply = getReply(event);

				hook.editOriginal(reply).queue();
			}
		);

	}

	private MessageEditData getReply(SlashCommandEvent event) {
		MessageCreateData permission = bot.getCheckUtil().lacksPermissions(event.getTextChannel(), event.getMember(), userPerms);
		if (permission != null)
			return MessageEditData.fromCreateData(permission);

		if (!bot.getDBUtil().isGuild(event.getGuild().getId())) {
			bot.getDBUtil().guildAdd(event.getGuild().getId());
			bot.getLogger().info("Added guild through setup '"+event.getGuild().getName()+"'("+event.getGuild().getId()+") to db");
		}

		MessageEmbed embed = bot.getEmbedUtil().getEmbed(event.getMember())
			.setTitle(bot.getMsg(event.getGuild().getId(), "bot.guild.setup.embed.setup_title"))
			.setDescription(bot.getMsg(event.getGuild().getId(), "bot.guild.setup.embed.setup_value"))
			.build();
		return MessageEditData.fromEmbeds(embed);
	}
}
