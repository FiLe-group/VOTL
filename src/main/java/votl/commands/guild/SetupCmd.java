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
			Guild guild = Objects.requireNonNull(event.getGuild());
			String guildId = guild.getId();

			if (bot.getDBUtil().guild.add(guildId)) {
				createReplyEmbed(event, 
					bot.getEmbedUtil().getEmbed(event)
						.setDescription(lu.getText(event, "bot.guild.setup.main.done"))
						.setColor(Constants.COLOR_SUCCESS)
						.build()
				);
				bot.getLogger().info("Added guild through setup '"+guild.getName()+"'("+guildId+") to db");
			} else {
				createReplyEmbed(event, 
					bot.getEmbedUtil().getEmbed(event)
						.setDescription(lu.getText(event, "bot.guild.setup.main.exists"))
						.setColor(Constants.COLOR_WARNING)
						.build()
				);
			}
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
			event.deferReply(true).queue();

			Guild guild = Objects.requireNonNull(event.getGuild());
			String guildId = guild.getId();

			if (bot.getDBUtil().guild.add(guildId)) {
				bot.getLogger().info("Added guild through setup '"+guild.getName()+"'("+guildId+") to db");
			}

			try {
				guild.createCategory(lu.getText(event, "bot.voice.setup.category"))
					.addPermissionOverride(guild.getBotRole(), Arrays.asList(getBotPermissions()), null)
					.queue(
						category -> {
							try {
								category.createVoiceChannel(lu.getText(event, "bot.voice.setup.channel"))
									.addPermissionOverride(guild.getPublicRole(), null, EnumSet.of(Permission.VOICE_SPEAK))
									.queue(
										channel -> {
											bot.getDBUtil().guildVoice.setup(guildId, category.getId(), channel.getId());
											bot.getLogger().info("Voice setup done in guild `"+guild.getName()+"'("+guildId+")");
											editHookEmbed(event, 
												bot.getEmbedUtil().getEmbed(event)
													.setDescription(lu.getText(event, "bot.voice.setup.done").replace("{channel}", channel.getAsMention()))
													.setColor(Constants.COLOR_SUCCESS)
													.build()
											);
										}
									);
							} catch (InsufficientPermissionException ex) {
								editPermError(event, event.getMember(), ex.getPermission(), true);
							}
						}
					);
			} catch (InsufficientPermissionException ex) {
				editPermError(event, event.getMember(), ex.getPermission(), true);
				ex.printStackTrace();
			}
		}

	}
}
