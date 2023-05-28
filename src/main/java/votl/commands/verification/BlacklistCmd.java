package votl.commands.verification;

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
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.Role;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;

public class BlacklistCmd extends CommandBase {
	
	public BlacklistCmd(App bot) {
		super(bot);
		this.name = "blacklist";
		this.path = "bot.verification.blacklist";
		this.children = new SlashCommand[]{new Add(bot), new View(bot)};
		this.module = CmdModule.VERIFICATION;
		this.category = CmdCategory.VERIFICATION;
		this.accessLevel = CmdAccessLevel.MOD;
		this.mustSetup = true;
	}

	@Override
	protected void execute(SlashCommandEvent event) {}

	private class Add extends CommandBase {

		public Add(App bot) {
			super(bot);
			this.name = "add";
			this.path = "bot.verification.blacklist.add";
			this.options = List.of(
				new OptionData(OptionType.USER, "user", lu.getText(path+".option_user"), true),
				new OptionData(OptionType.STRING, "reason", lu.getText(path+".option_reason")).setMaxLength(200));
			this.botPermissions = new Permission[]{Permission.MANAGE_ROLES};
		}

		@Override
		protected void execute(SlashCommandEvent event) {
			Member member = event.optMember("user");
			Guild guild = event.getGuild();
			if (member == null || !guild.getSelfMember().canInteract(member)) {
				createError(event, path+".no_user");
				return;
			}

			String roleId = bot.getDBUtil().verify.getVerifyRole(guild.getId());
			if (roleId == null) {
				createError(event, path+".not_setup");
				return;
			}
			Role role = guild.getRoleById(roleId);
			if (role == null) {
				createError(event, "errors.unknown", "Role not found by ID: "+roleId);
				return;
			}

			String reason = event.optString("reason", lu.getLocalized(event.getGuildLocale(), path+".no_reason"));

			guild.removeRoleFromMember(member, role).reason("Blacklisted by "+event.getUser().getAsTag()+" | "+reason).queue(
				success -> {
					StringBuffer buffer = new StringBuffer(lu.getText(event, path+".done_role")+"\n\n");

					if (bot.getDBUtil().verify.blacklistUser(guild.getId(), member.getId())) {
						bot.getLogListener().onBlacklist(member, guild, reason);
						buffer.append(lu.getText(event, path+".done_added"));
						event.replyEmbeds(new EmbedBuilder().setColor(Constants.COLOR_SUCCESS).setDescription(buffer.toString()).build()).queue();
					} else {
						buffer.append(lu.getText(event, path+".failed_to_add"));
						event.replyEmbeds(new EmbedBuilder().setColor(Constants.COLOR_FAILURE).setDescription(buffer.toString()).build()).queue();
					}
				},
				failure -> {
					createError(event, "bot.verification.failed_role");
				});
			
		}

	}

	private class View extends CommandBase {

		public View(App bot) {
			super(bot);
			this.name = "view";
			this.path = "bot.verification.blacklist.view";
		}

		@Override
		protected void execute(SlashCommandEvent event) {
			List<String> ids = bot.getDBUtil().verify.getBlacklist(event.getGuild().getId());
			if (ids.size() == 0) {
				createError(event, path+".not_found");
				return;
			}

			EmbedBuilder builder = bot.getEmbedUtil().getEmbed(event);
			String title = lu.getUserText(event, path+".embed_title");
			StringBuffer buffer = new StringBuffer();
			for (String id : ids) {
				buffer.append("<@"+id+"> | `"+id+"`\n");
				if (buffer.length() > 900) {
					builder.addField(title, buffer.toString(), false);
					title = "";
					buffer = new StringBuffer();
				}
			}
			builder.addField(title, buffer.toString(), false);

			createReplyEmbed(event, builder.build());
		}
	}

}
