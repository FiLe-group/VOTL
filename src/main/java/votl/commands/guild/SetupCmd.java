package votl.commands.guild;

import java.util.Arrays;
import java.util.Collections;
import java.util.EnumSet;
import java.util.Objects;

import votl.App;
import votl.commands.CommandBase;
import votl.objects.CmdAccessLevel;
import votl.objects.CmdModule;
import votl.objects.Emotes;
import votl.objects.command.SlashCommand;
import votl.objects.command.SlashCommandEvent;
import votl.objects.constants.CmdCategory;
import votl.objects.constants.Constants;

import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.channel.ChannelType;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import net.dv8tion.jda.api.entities.channel.middleman.GuildChannel;
import net.dv8tion.jda.api.entities.emoji.Emoji;
import net.dv8tion.jda.api.exceptions.InsufficientPermissionException;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import net.dv8tion.jda.api.interactions.components.ActionRow;
import net.dv8tion.jda.api.interactions.components.buttons.Button;

public class SetupCmd extends CommandBase {

	public SetupCmd(App bot) {
		super(bot);
		this.name = "setup";
		this.path = "bot.guild.setup";
		this.children = new SlashCommand[]{new Voice(bot), new Main(bot), new VoicePanel(bot)};
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
						.setDescription(lu.getText(event, path+".done"))
						.setColor(Constants.COLOR_SUCCESS)
						.build()
				);
				bot.getLogger().info("Added guild through setup '"+guild.getName()+"'("+guildId+") to db");
			} else {
				createReplyEmbed(event, 
					bot.getEmbedUtil().getEmbed(event)
						.setDescription(lu.getText(event, path+".exists"))
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
			this.path = "bot.guild.setup.voice";
			this.botPermissions = new Permission[]{Permission.MANAGE_CHANNEL, Permission.MANAGE_ROLES, Permission.MANAGE_PERMISSIONS, Permission.VIEW_CHANNEL, Permission.VOICE_CONNECT, Permission.VOICE_MOVE_OTHERS};
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
				guild.createCategory(lu.getText(event, path+".category"))
					.addPermissionOverride(guild.getBotRole(), Arrays.asList(getBotPermissions()), null)
					.queue(
						category -> {
							try {
								category.createVoiceChannel(lu.getText(event, path+".channel"))
									.addPermissionOverride(guild.getPublicRole(), null, EnumSet.of(Permission.VOICE_SPEAK))
									.queue(
										channel -> {
											bot.getDBUtil().guildVoice.setup(guildId, category.getId(), channel.getId());
											bot.getLogger().info("Voice setup done in guild `"+guild.getName()+"'("+guildId+")");
											editHookEmbed(event, 
												bot.getEmbedUtil().getEmbed(event)
													.setDescription(lu.getText(event, path+".done").replace("{channel}", channel.getAsMention()))
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

	private class VoicePanel extends CommandBase {
		
		public VoicePanel(App bot) {
			super(bot);
			this.name = "panel";
			this.path = "bot.guild.setup.panel";
			this.options = Collections.singletonList(
				new OptionData(OptionType.CHANNEL, "channel", lu.getText(path+".option_channel"), true)
			);
			this.botPermissions = new Permission[]{Permission.MESSAGE_SEND, Permission.MESSAGE_EMBED_LINKS};
			this.module = CmdModule.VOICE;
			this.mustSetup = true;
		}

		@Override
		protected void execute(SlashCommandEvent event) {
			GuildChannel channel = event.optGuildChannel("channel");
			if (channel == null || channel.getType() != ChannelType.TEXT) {
				createError(event, path+".no_channel", "Received: "+(channel == null ? "No channel" : channel.getType()));
			}
			TextChannel tc = (TextChannel) channel;

			Button lock = Button.danger("voicepanel-lock", lu.getLocalized(event.getGuildLocale(), path+".lock")).withEmoji(Emoji.fromUnicode("🔒"));
			Button unlock = Button.success("voicepanel-unlock", lu.getLocalized(event.getGuildLocale(), path+".unlock")).withEmoji(Emoji.fromUnicode("🔓"));
			Button ghost = Button.danger("voicepanel-ghost", lu.getLocalized(event.getGuildLocale(), path+".ghost")).withEmoji(Emoji.fromUnicode("👻"));
			Button unghost = Button.success("voicepanel-unghost", lu.getLocalized(event.getGuildLocale(), path+".unghost")).withEmoji(Emoji.fromUnicode("👁️"));
			Button name = Button.secondary("voicepanel-name", lu.getLocalized(event.getGuildLocale(), path+".name")).withEmoji(Emoji.fromUnicode("🔡"));
			Button limit = Button.secondary("voicepanel-limit", lu.getLocalized(event.getGuildLocale(), path+".limit")).withEmoji(Emoji.fromUnicode("🔢"));
			Button permit = Button.success("voicepanel-permit", lu.getLocalized(event.getGuildLocale(), path+".permit")).withEmoji(Emotes.ADDUSER.getEmoji());
			Button reject = Button.danger("voicepanel-reject", lu.getLocalized(event.getGuildLocale(), path+".reject")).withEmoji(Emotes.REMOVEUSER.getEmoji());
			Button delete = Button.danger("voicepanel-delete", lu.getLocalized(event.getGuildLocale(), path+".delete")).withEmoji(Emoji.fromUnicode("🔴"));

			ActionRow row1 = ActionRow.of(lock, unlock, ghost, unghost);
			ActionRow row2 = ActionRow.of(name, limit, permit, reject);
			ActionRow row3 = ActionRow.of(delete);
			tc.sendMessageEmbeds(
				new EmbedBuilder().setColor(Constants.COLOR_DEFAULT).setTitle(lu.getText(event, path+".embed_title"))
					.setDescription(lu.getText(event, path+".embed_value")).build()
			).addComponents(row1, row2, row3).queue();

			createReplyEmbed(event, bot.getEmbedUtil().getEmbed(event)
				.setDescription(lu.getText(event, path+".done").replace("{channel}", tc.getAsMention()))
				.setColor(Constants.COLOR_SUCCESS)
				.build());
		}
	}
}
