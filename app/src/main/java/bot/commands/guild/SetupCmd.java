package bot.commands.guild;

import java.util.Arrays;
import java.util.EnumSet;
import java.util.Objects;

import com.jagrosh.jdautilities.command.SlashCommand;
import com.jagrosh.jdautilities.command.SlashCommandEvent;
import com.jagrosh.jdautilities.doc.standard.CommandInfo;

import bot.App;
import bot.utils.exception.CheckException;
import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.exceptions.InsufficientPermissionException;
import net.dv8tion.jda.api.interactions.InteractionHook;

@CommandInfo
(
	name = "setup",
	description = "Setup menu for this server.",
	usage = "/setup <select>",
	requirements = "Have 'Manage Server' permission"
)
public class SetupCmd extends SlashCommand {

	private static App bot;

	protected static Permission[] userPerms;

	public SetupCmd(App bot) {
		this.name = "setup";
		this.help = bot.getMsg("bot.guild.setup.help");;
		this.category = new Category("guild");
		SetupCmd.userPerms = new Permission[]{Permission.MANAGE_SERVER};
		SetupCmd.bot = bot;
		this.children = new SlashCommand[]{new Voice(), new Main()};
	}

	@Override
	protected void execute(SlashCommandEvent event) {

	}

	private static class Main extends SlashCommand {
		
		public Main() {
			this.name = "main";
			this.help = bot.getMsg("bot.guild.setup.main.help");
		}

		@Override
		protected void execute(SlashCommandEvent event) {
			event.deferReply(true).queue(
				hook -> {
					
					try {
						bot.getCheckUtil().hasPermissions(event.getTextChannel(), event.getMember(), userPerms);
					} catch (CheckException ex) {
						hook.editOriginal(ex.getEditData()).queue();
						return;
					}

					Guild guild = Objects.requireNonNull(event.getGuild());
					String guildId = guild.getId();

					if (bot.getDBUtil().guildAdd(guildId, false)) {
						hook.editOriginalEmbeds(
							bot.getEmbedUtil().getEmbed(event.getMember())
								.setDescription(bot.getMsg(guildId, "bot.guild.setup.main.done"))
								.build()
						).queue();
						bot.getLogger().info("Added guild (inc. -) through setup '"+guild.getName()+"'("+guildId+") to db");
					} else {
						hook.editOriginalEmbeds(
							bot.getEmbedUtil().getEmbed(event.getMember())
								.setDescription(bot.getMsg(guildId, "bot.guild.setup.main.exists"))
								.build()
						).queue();
					}
					
				}
			);
		}
	}

	private static class Voice extends SlashCommand {

		protected Permission[] botPerms;

		public Voice() {
			this.name = "voice";
			this.help = bot.getMsg("bot.guild.setup.voice");
			this.botPerms = new Permission[]{Permission.MANAGE_CHANNEL, Permission.MANAGE_ROLES, Permission.MANAGE_PERMISSIONS, Permission.VIEW_CHANNEL, Permission.VOICE_CONNECT, Permission.VOICE_MOVE_OTHERS}; // Permission.MESSAGE_EMBED_LINKS
		}

		@Override
		protected void execute(SlashCommandEvent event) {
			event.deferReply(true).queue(
				hook -> {
					sendReply(event, hook);
				}
			);
		}

		@SuppressWarnings("null")
		private void sendReply(SlashCommandEvent event, InteractionHook hook) {

			try {
				bot.getCheckUtil().hasPermissions(event.getTextChannel(), event.getMember(), true, botPerms)
					.hasPermissions(event.getTextChannel(), event.getMember(), userPerms);
			} catch (CheckException ex) {
				hook.editOriginal(ex.getEditData()).queue();
				return;
			}

			Guild guild = Objects.requireNonNull(event.getGuild());
			String guildId = guild.getId();

			if (bot.getDBUtil().guildAdd(guildId, true)) {
				bot.getLogger().info("Added guild (inc. voice) through setup '"+guild.getName()+"'("+guildId+") to db");
			}

			try {
				guild.createCategory(bot.getMsg(guildId, "bot.voice.setup.category"))
					.addPermissionOverride(guild.getBotRole(), Arrays.asList(botPerms), null)
					.queue(
						category -> {
							try {
								category.createVoiceChannel(bot.getMsg(guildId, "bot.voice.setup.channel"))
									.addPermissionOverride(guild.getPublicRole(), null, EnumSet.of(Permission.VOICE_SPEAK))
									.queue(
										channel -> {
											bot.getDBUtil().guildVoiceSetup(guildId, category.getId(), channel.getId());
											hook.editOriginalEmbeds(
												bot.getEmbedUtil().getEmbed(event.getMember())
													.setDescription(bot.getMsg(guildId, "bot.voice.setup.done").replace("{channel}", channel.getAsMention()))
													.build()
											).queue();
										}
									);
							} catch (InsufficientPermissionException ex) {
								hook.editOriginal(
									bot.getEmbedUtil().getPermError(event.getTextChannel(), event.getMember(), ex.getPermission(), true)
								).queue();
							}
						}
					);
			} catch (InsufficientPermissionException ex) {
				hook.editOriginal(
					bot.getEmbedUtil().getPermError(event.getTextChannel(), event.getMember(), ex.getPermission(), true)
				).queue();
				ex.printStackTrace();
			}
			
		}

	}

}
