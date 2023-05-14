package votl.commands.verification;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

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
		this.children = new SlashCommand[]{new Create(bot), new Main(bot), new Instructions(bot), new Link(bot)};
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

			Button next = Button.primary("verify", lu.getText(event, path+".continue"));
			String text = bot.getDBUtil().verify.getPanelText(event.getGuild().getId());

			tc.sendMessageEmbeds(new EmbedBuilder().setColor(Constants.COLOR_DEFAULT).setDescription(text).build()).addActionRow(next).queue();

			createReplyEmbed(event, bot.getEmbedUtil().getEmbed(event)
				.setDescription(lu.getText(event, path+".done").replace("{channel}", tc.getAsMention()))
				.setColor(Constants.COLOR_SUCCESS)
				.build());
		}

	}

	private class Main extends CommandBase {
		
		public Main(App bot) {
			super(bot);
			this.name = "main";
			this.path = "bot.verification.vfpanel.main";
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

	private class Instructions extends CommandBase {
		
		public Instructions(App bot) {
			super(bot);
			this.name = "instruct";
			this.path = "bot.verification.vfpanel.instruct";
			List<OptionData> options = new ArrayList<OptionData>();
			options.add(new OptionData(OptionType.STRING, "description", lu.getText(path+".option_text")));
			options.add(new OptionData(OptionType.STRING, "instructions", lu.getText(path+".option_text")));
			this.options = options;
			this.botPermissions = new Permission[]{Permission.MESSAGE_SEND, Permission.MESSAGE_EMBED_LINKS};
		}

		@Override
		protected void execute(SlashCommandEvent event) {
			String guildId = event.getGuild().getId();

			if (!bot.getDBUtil().verify.exists(guildId)) {
				bot.getDBUtil().verify.add(guildId);
			}
			
			String description = event.optString("description");
			if (description != null) {
				bot.getDBUtil().verify.setInstructionText(guildId, description);
			}
			String instructions = event.optString("instructions");
			if (instructions != null) {
				bot.getDBUtil().verify.setInstructionField(guildId, instructions);
			}

			createReplyEmbed(event, bot.getEmbedUtil().getEmbed(event)
				.setDescription(lu.getText(event, path+".done"))
				.setColor(Constants.COLOR_SUCCESS)
				.build());
		}

	}

	private class Link extends CommandBase {
		
		public Link(App bot) {
			super(bot);
			this.name = "link";
			this.path = "bot.verification.vfpanel.link";
			this.options = Collections.singletonList(
				new OptionData(OptionType.STRING, "text", lu.getText(path+".option_text"), true).setMaxLength(500)
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
			bot.getDBUtil().verify.setVerificationLink(guildId, text);

			createReplyEmbed(event, bot.getEmbedUtil().getEmbed(event)
				.setDescription(lu.getText(event, path+".done").replace("{link}", text))
				.setColor(Constants.COLOR_SUCCESS)
				.build());
		}

	}

}
