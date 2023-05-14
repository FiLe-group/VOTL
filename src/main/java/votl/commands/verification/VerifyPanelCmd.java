package votl.commands.verification;

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
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Role;
import net.dv8tion.jda.api.entities.channel.ChannelType;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import net.dv8tion.jda.api.entities.channel.middleman.GuildChannel;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import net.dv8tion.jda.api.interactions.components.buttons.Button;

public class VerifyPanelCmd extends CommandBase {
	
	public VerifyPanelCmd(App bot) { 
		super(bot);
		this.name = "verifypanel";
		this.path = "bot.verification.verifypanel";
		this.children = new SlashCommand[]{new Create(bot), new SetRole(bot)};
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
			this.path = "bot.verification.verifypanel.create";
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

			if (bot.getDBUtil().guild.getVerifyRole(event.getGuild().getId()) == null) {
				createError(event, path+".no_role");
				return;
			}

			Button next = Button.primary("verify", lu.getText(event, path+".continue"));

			StringBuffer buffer = new StringBuffer();
			buffer.append("**ЧТОБЫ ПОЛУЧИТЬ __ПОЛНЫЙ__ ДОСТУП К СЕРВЕРУ НАЖМИТЕ НА КНОПКУ НИЖЕ**\n\n")
				.append("___Нажимая на кнопку Вы соглашаетесь соблюдать правила:___ \n")
				.append("🔸Правила сообщества Discord - https://discord.com/guidelines \n")
				.append("🔸Пользовательское соглашение Discord - https://discord.com/terms \n")
				.append("🔸Правила дискорд-сервера Rise of the Republic в канале <#559795098410418177>");

			tc.sendMessageEmbeds(new EmbedBuilder().setColor(Constants.COLOR_DEFAULT).setDescription(buffer.toString()).build()).addActionRow(next).queue();

			createReplyEmbed(event, bot.getEmbedUtil().getEmbed(event)
				.setDescription(lu.getText(event, path+".done").replace("{channel}", tc.getAsMention()))
				.setColor(Constants.COLOR_SUCCESS)
				.build());
		}

	}

	private class SetRole extends CommandBase {

		public SetRole(App bot) {
			super(bot);
			this.name = "role";
			this.path = "bot.verification.verifypanel.role";
			this.options = Collections.singletonList(
				new OptionData(OptionType.ROLE, "role", lu.getText(path+".option_role"), true)
			);
		}

		@Override
		protected void execute(SlashCommandEvent event) {
			Guild guild = event.getGuild();
			Role role = event.optRole("role");
			if (role == null || role.isPublicRole() || role.isManaged() || !guild.getSelfMember().canInteract(role)) {
				createError(event, path+".no_role");
				return;
			}

			bot.getDBUtil().guild.setVerifyRole(guild.getId(), role.getId());

			createReplyEmbed(event, bot.getEmbedUtil().getEmbed(event)
				.setDescription(lu.getText(event, path+".done").replace("{role}", role.getAsMention()))
				.setColor(Constants.COLOR_SUCCESS)
				.build());
		}

	}

}
