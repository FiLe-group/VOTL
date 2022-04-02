package bot.commands.voice;

import java.util.EnumSet;

import com.jagrosh.jdautilities.command.Command;
import com.jagrosh.jdautilities.command.CommandEvent;
import com.jagrosh.jdautilities.doc.standard.CommandInfo;

import bot.App;
import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.entities.MessageEmbed;
import net.dv8tion.jda.api.exceptions.InsufficientPermissionException;

@CommandInfo
(
	name = {"setup","setupVoice"},
	description = "Setup bot for using in this server.",
	usage = "{prefix}setup",
	requirements = "Have 'Manage server' permission"
)
public class SetupCmd extends Command {
	
	private final App bot;

	protected Permission[] botPerms;
	protected Permission[] userPerms;

	public SetupCmd(App bot) {
		this.name = "setup";
		this.help = "bot.voice.setup.description";
		this.category = new Category("voice");
		this.userPerms = new Permission[]{Permission.MANAGE_SERVER};
		this.botPerms = new Permission[]{Permission.MESSAGE_EMBED_LINKS, Permission.MANAGE_CHANNEL, Permission.MANAGE_ROLES, Permission.MANAGE_PERMISSIONS, Permission.VOICE_MOVE_OTHERS};
		this.bot = bot;
	}

	@Override
	protected void execute(CommandEvent event) {
		if (bot.getCheckUtil().lacksPermissions(event.getTextChannel(), event.getMember(), true, botPerms) || 
				bot.getCheckUtil().lacksPermissions(event.getTextChannel(), event.getMember(), userPerms))
			return;

		if (!bot.getDBUtil().isGuild(event.getGuild().getId())) {
			bot.getDBUtil().guildAdd(event.getGuild().getId());
			bot.getLogger().info("Added guild through setup '"+event.getGuild().getName()+"'("+event.getGuild().getId()+") to db");
		}

		try {
			event.getGuild().createCategory(bot.getMsg(event.getGuild().getId(), "bot.voice.setup.category"))
				.addRolePermissionOverride(event.getGuild().getRoleByBot(event.getSelfUser().getId()).getIdLong(),
					EnumSet.of(Permission.VIEW_CHANNEL, Permission.MANAGE_CHANNEL, Permission.MANAGE_ROLES, Permission.MANAGE_PERMISSIONS, Permission.VOICE_MOVE_OTHERS),
					null)
				.queue(
					category -> {
						try {
							category.createVoiceChannel(bot.getMsg(event.getGuild().getId(), "bot.voice.setup.channel"))
								.syncPermissionOverrides()
								.addMemberPermissionOverride(event.getSelfMember().getIdLong(),
									EnumSet.of(Permission.MANAGE_CHANNEL, Permission.MANAGE_ROLES, Permission.MANAGE_PERMISSIONS, Permission.VOICE_MOVE_OTHERS),
									null)
								.addPermissionOverride(event.getGuild().getPublicRole(), null, EnumSet.of(Permission.VOICE_SPEAK))
								.queue(
									channel -> {
										bot.getDBUtil().guildVoiceSetup(event.getGuild().getId(), category.getId(), channel.getId());
										MessageEmbed embed = bot.getEmbedUtil().getEmbed(event.getMember())
											.setDescription(bot.getMsg(event.getGuild().getId(), "bot.voice.setup.done").replace("{channel}", channel.getAsMention()))
											.build();
										event.reply(embed);
									}
								);
						} catch (InsufficientPermissionException ex) {
							bot.getEmbedUtil().sendPermError(event.getTextChannel(), event.getMember(), Permission.MANAGE_PERMISSIONS, true);
						}
					}
				);
		} catch (InsufficientPermissionException ex) {
			bot.getEmbedUtil().sendPermError(event.getTextChannel(), event.getMember(), Permission.MANAGE_PERMISSIONS, true);
			ex.printStackTrace();
		}
	}
}
