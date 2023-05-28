package votl.commands.verification;

import java.awt.Color;
import java.util.Collections;

import votl.App;
import votl.commands.CommandBase;
import votl.objects.CmdAccessLevel;
import votl.objects.CmdModule;
import votl.objects.command.SlashCommand;
import votl.objects.command.SlashCommandEvent;
import votl.objects.constants.CmdCategory;
import votl.objects.constants.Constants;

import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.entities.channel.ChannelType;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import net.dv8tion.jda.api.entities.channel.middleman.GuildChannel;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import net.dv8tion.jda.api.interactions.components.buttons.Button;

public class VerifyPanelCmd extends CommandBase {
	
	public VerifyPanelCmd(App bot) { 
		super(bot);
		this.name = "vfpanel";
		this.path = "bot.verification.vfpanel";
		this.children = new SlashCommand[]{new Create(bot), new Text(bot), new SetColor(bot)};
		this.module = CmdModule.VERIFICATION;
		this.category = CmdCategory.VERIFICATION;
		this.accessLevel = CmdAccessLevel.ADMIN;
		this.mustSetup = true;
	}

	@Override
	protected void execute(SlashCommandEvent event) {}

	private class Create extends CommandBase {
		
		public Create(App bot) {
			super(bot);
			this.name = "create";
			this.path = "bot.verification.vfpanel.create";
			this.options = Collections.singletonList(
				new OptionData(OptionType.CHANNEL, "channel", lu.getText(path+".option_channel"), true)
			);
			this.botPermissions = new Permission[]{Permission.MESSAGE_SEND, Permission.MESSAGE_EMBED_LINKS};
		}

		@Override
		protected void execute(SlashCommandEvent event) {
			GuildChannel channel = event.optGuildChannel("channel");
			if (channel == null || channel.getType() != ChannelType.TEXT) {
				createError(event, path+".no_channel", "Received: "+(channel == null ? "No channel" : channel.getType()));
				return;
			}
			TextChannel tc = (TextChannel) channel;

			if (bot.getDBUtil().verify.getVerifyRole(event.getGuild().getId()) == null) {
				createError(event, path+".no_role");
				return;
			}

			Button next = Button.primary("verify", lu.getLocalized(event.getGuildLocale(), path+".continue"));
			String text = bot.getDBUtil().verify.getPanelText(event.getGuild().getId());

			tc.sendMessageEmbeds(new EmbedBuilder().setColor(bot.getDBUtil().verify.getColor(event.getGuild().getId())).setDescription(text).build()).addActionRow(next).queue();

			createReplyEmbed(event, bot.getEmbedUtil().getEmbed(event)
				.setDescription(lu.getText(event, path+".done").replace("{channel}", tc.getAsMention()))
				.setColor(Constants.COLOR_SUCCESS)
				.build());
		}

	}

	private class Text extends CommandBase {
		
		public Text(App bot) {
			super(bot);
			this.name = "text";
			this.path = "bot.verification.vfpanel.text";
			this.options = Collections.singletonList(
				new OptionData(OptionType.STRING, "text", lu.getText(path+".option_text"), true)
			);
			this.botPermissions = new Permission[]{Permission.MESSAGE_SEND, Permission.MESSAGE_EMBED_LINKS};
		}

		@Override
		protected void execute(SlashCommandEvent event) {
			String guildId = event.getGuild().getId();
			String text = event.optString("text");

			if (!bot.getDBUtil().verify.exists(guildId)) {
				bot.getDBUtil().verify.add(guildId);
			}
			bot.getDBUtil().verify.setPanelText(guildId, text);

			createReplyEmbed(event, bot.getEmbedUtil().getEmbed(event)
				.setDescription(lu.getText(event, path+".done"))
				.setColor(Constants.COLOR_SUCCESS)
				.build());
		}

	}

	private class SetColor extends CommandBase {

		public SetColor(App bot) {
			super(bot);
			this.name = "color";
			this.path = "bot.verification.vfpanel.color";
			this.options = Collections.singletonList(
				new OptionData(OptionType.STRING, "color", lu.getText(path+".option_color"), true).setMaxLength(20)
			);
			this.botPermissions = new Permission[]{Permission.MESSAGE_SEND, Permission.MESSAGE_EMBED_LINKS};
		}

		@Override
		protected void execute(SlashCommandEvent event) {
			String guildId = event.getGuild().getId();
			String text = event.optString("color");

			if (!bot.getDBUtil().verify.exists(guildId)) {
				bot.getDBUtil().verify.add(guildId);
			}

			Color color = bot.getMessageUtil().getColor(text);
			if (color == null) {
				createError(event, path+".no_color");
				return;
			}
			bot.getDBUtil().verify.setColor(guildId, color.getRGB() & 0xFFFFFF);

			createReplyEmbed(event, bot.getEmbedUtil().getEmbed(event)
				.setDescription(lu.getText(event, path+".done").replace("{color}", "#"+Integer.toHexString(color.getRGB() & 0xFFFFFF)))
				.setColor(color)
				.build());
		}

	}



}
