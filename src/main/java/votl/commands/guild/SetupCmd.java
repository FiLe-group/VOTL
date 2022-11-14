package votl.commands.guild;

import java.util.Arrays;
import java.util.EnumSet;
import java.util.Objects;

import votl.App;
import votl.commands.CommandBase;
import votl.objects.CmdAccessLevel;
import votl.objects.CmdModule;
import votl.objects.command.SlashCommand;
import votl.objects.command.SlashCommandEvent;
import votl.objects.constants.CmdCategory;
import votl.objects.constants.Constants;

import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.exceptions.InsufficientPermissionException;
import net.dv8tion.jda.api.interactions.DiscordLocale;
import net.dv8tion.jda.api.interactions.InteractionHook;

import com.jagrosh.jdautilities.doc.standard.CommandInfo;

@CommandInfo
(
	name = "setup",
	description = "Setup menu for this server.",
	usage = "/setup <main / voice>",
	requirements = "Have 'Manage Server' permission"
)
public class SetupCmd extends CommandBase {

	public SetupCmd(App bot) {
		super(bot);
		this.name = "setup";
		this.path = "bot.guild.setup";
		this.children = new SlashCommand[]{new Voice(bot), new Main(bot)};
		this.userPermissions = new Permission[]{Permission.MANAGE_SERVER};
		this.category = CmdCategory.GUILD;
		this.accessLevel = CmdAccessLevel.ADMIN;
	}

	@Override
	protected void execute(SlashCommandEvent event) {

	}

	private class Main extends CommandBase {
		
		public Main(App bot) {
			super(bot);
			this.name = "main";
			this.path = "bot.guild.setup.main";
		}

		@Override
		protected void execute(SlashCommandEvent event) {
			event.deferReply(true).queue(
				hook -> {
					Guild guild = Objects.requireNonNull(event.getGuild());
					String guildId = guild.getId();
					DiscordLocale userLocale = event.getUserLocale();

					if (bot.getDBUtil().guildAdd(guildId)) {
						hook.editOriginalEmbeds(
							bot.getEmbedUtil().getEmbed(event)
								.setDescription(lu.getLocalized(userLocale, "bot.guild.setup.main.done"))
								.setColor(Constants.COLOR_SUCCESS)
								.build()
						).queue();
						bot.getLogger().info("Added guild through setup '"+guild.getName()+"'("+guildId+") to db");
					} else {
						hook.editOriginalEmbeds(
							bot.getEmbedUtil().getEmbed(event)
								.setDescription(lu.getLocalized(userLocale, "bot.guild.setup.main.exists"))
								.setColor(Constants.COLOR_WARNING)
								.build()
						).queue();
					}
					
				}
			);
		}
	}

	private class Voice extends CommandBase {

		public Voice(App bot) {
			super(bot);
			this.name = "voice";
			this.path = "bot.guild.setup";
			this.botPermissions = new Permission[]{Permission.MANAGE_CHANNEL, Permission.MANAGE_ROLES, Permission.MANAGE_PERMISSIONS, Permission.VIEW_CHANNEL, Permission.VOICE_CONNECT, Permission.VOICE_MOVE_OTHERS}; // Permission.MESSAGE_EMBED_LINKS
			this.module = CmdModule.VOICE;
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

			Guild guild = Objects.requireNonNull(event.getGuild());
			String guildId = guild.getId();
			DiscordLocale userLocale = event.getUserLocale();

			if (bot.getDBUtil().guildAdd(guildId)) {
				bot.getLogger().info("Added guild through setup '"+guild.getName()+"'("+guildId+") to db");
			}

			try {
				guild.createCategory(lu.getLocalized(userLocale, "bot.voice.setup.category"))
					.addPermissionOverride(guild.getBotRole(), Arrays.asList(getBotPermissions()), null)
					.queue(
						category -> {
							try {
								category.createVoiceChannel(lu.getLocalized(userLocale, "bot.voice.setup.channel"))
									.addPermissionOverride(guild.getPublicRole(), null, EnumSet.of(Permission.VOICE_SPEAK))
									.queue(
										channel -> {
											bot.getDBUtil().guildVoiceSetup(guildId, category.getId(), channel.getId());
											bot.getLogger().info("Voice setup done in guild `"+guild.getName()+"'("+guildId+")");
											hook.editOriginalEmbeds(
												bot.getEmbedUtil().getEmbed(event)
													.setDescription(lu.getLocalized(userLocale, "bot.voice.setup.done").replace("{channel}", channel.getAsMention()))
													.setColor(Constants.COLOR_SUCCESS)
													.build()
											).queue();
										}
									);
							} catch (InsufficientPermissionException ex) {
								hook.editOriginal(
									bot.getEmbedUtil().getPermError(event, event.getMember(), ex.getPermission(), true)
								).queue();
							}
						}
					);
			} catch (InsufficientPermissionException ex) {
				hook.editOriginal(
					bot.getEmbedUtil().getPermError(event, event.getMember(), ex.getPermission(), true)
				).queue();
				ex.printStackTrace();
			}
			
		}

	}

}
