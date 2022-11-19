package votl.commands.guild;

import java.util.Collections;
import java.util.Objects;

import votl.App;
import votl.commands.CommandBase;
import votl.objects.CmdAccessLevel;
import votl.objects.command.SlashCommand;
import votl.objects.command.SlashCommandEvent;
import votl.objects.constants.CmdCategory;
import votl.objects.constants.Constants;

import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.interactions.DiscordLocale;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import net.dv8tion.jda.api.interactions.commands.build.SubcommandGroupData;

import com.jagrosh.jdautilities.doc.standard.CommandInfo;

@CommandInfo(
	name = "access",
	usage = "/access <show / add / remove>"
)
public class AccessCmd extends CommandBase {

	public AccessCmd(App bot) {
		super(bot);
		this.name = "access";
		this.path = "bot.guild.access";
		this.children = new SlashCommand[]{new AddMod(bot), new AddAdmin(bot),
			new View(bot), new RemoveMod(bot), new RemoveAdmin(bot)};
		this.category = CmdCategory.GUILD;
		this.accessLevel = CmdAccessLevel.ADMIN;
		this.mustSetup = true;
	}

	@Override
	protected void execute(SlashCommandEvent event) {
		
	}

	private class AddMod extends CommandBase {

		public AddMod(App bot) {
			super(bot);
			this.name = "mod";
			this.path = "bot.guild.access.add.mod";
			this.options = Collections.singletonList(
				new OptionData(OptionType.USER, "member", lu.getText("bot.guild.access.add.option_user"), true)
			);
			this.subcommandGroup = new SubcommandGroupData("add", lu.getText("bot.guild.access.add.help"));
		}

		@Override
		protected void execute(SlashCommandEvent event) {
			event.deferReply(true).queue();

			Member targetMember = event.optMember("member");
			if (targetMember == null) {
				editError(event, "bot.guild.access.add.no_member");
			} else {
				Member author = Objects.requireNonNull(event.getMember());
				Guild guild = Objects.requireNonNull(event.getGuild());
				String guildId = guild.getId();

				guild.retrieveMember(targetMember).queue(
					member -> {
						if (member.equals(author) || member.getUser().isBot()) {
							editError(event, "bot.guild.access.add.not_self");
							return;
						}
						if (bot.getCheckUtil().getAccessLevel(event.getClient(), member).getLevel() >= bot.getCheckUtil().getAccessLevel(event.getClient(), author).getLevel()) {
							editError(event, "bot.guild.access.add.is_higher");
							return;
						}
						String access = bot.getDBUtil().hasAccess(guildId, member.getId());
						if (access != null && access.equals("mod")) {
							editError(event, "bot.guild.access.add.mod.already");
							return;
						}
						if (access != null && access.equals("admin")) {
							bot.getDBUtil().accessChange(guildId, member.getId(), false);
						} else {
							bot.getDBUtil().accessAdd(guildId, member.getId(), false);
						}

						EmbedBuilder embed = bot.getEmbedUtil().getEmbed(event)
							.setDescription(lu.getText(event, "bot.guild.access.add.mod.done").replace("{member}", member.getAsMention()))
							.setColor(Constants.COLOR_SUCCESS);
						editHookEmbed(event, embed.build());
					},
					error -> {
						// remake to specify error response - not in this guild or user does not exist
						// add errors - UNKNOWN_MEMBER & UNKNOWN_USER
						editError(event, "bot.guild.access.add.no_member");
					}
				);
			}
		}

	}

	private class AddAdmin extends CommandBase {

		public AddAdmin(App bot) {
			super(bot);
			this.name = "admin";
			this.path = "bot.guild.access.add.admin";
			this.options = Collections.singletonList(
				new OptionData(OptionType.USER, "member", lu.getText("bot.guild.access.add.option_user"), true)
			);
			this.subcommandGroup = new SubcommandGroupData("add", lu.getText("bot.guild.access.add.help"));
			this.accessLevel = CmdAccessLevel.OWNER;
		}

		@Override
		protected void execute(SlashCommandEvent event) {
			event.deferReply(true).queue();

			Member targetMember = event.optMember("member");
			if (targetMember == null) {
				editError(event, "bot.guild.access.add.no_member");
			} else {
				Member author = Objects.requireNonNull(event.getMember());
				Guild guild = Objects.requireNonNull(event.getGuild());
				String guildId = guild.getId();

				guild.retrieveMember(targetMember).queue(
					member -> {
						if (member.equals(author) || member.getUser().isBot()) {
							editError(event, "bot.guild.access.add.not_self");
							return;
						}
						if (bot.getCheckUtil().getAccessLevel(event.getClient(), member).getLevel() >= bot.getCheckUtil().getAccessLevel(event.getClient(), author).getLevel()) {
							editError(event, "bot.guild.access.add.is_higher");
							return;
						}
						String access = bot.getDBUtil().hasAccess(guildId, member.getId());
						if (access != null && access.equals("admin")) {
							editError(event, "bot.guild.access.add.admin.already");
							return;
						}
						if (access != null && access.equals("mod")) {
							bot.getDBUtil().accessChange(guildId, member.getId(), true);
						} else {
							bot.getDBUtil().accessAdd(guildId, member.getId(), true);
						}

						EmbedBuilder embed = bot.getEmbedUtil().getEmbed(event)
							.setDescription(lu.getText(event, "bot.guild.access.add.admin.done").replace("{member}", member.getAsMention()))
							.setColor(Constants.COLOR_SUCCESS);
							editHookEmbed(event, embed.build());

					}, 
					failure -> {
						editError(event, "bot.guild.access.add.no_member");
					}
				);
			}
		}

	}

	private class View extends CommandBase {

		public View(App bot) {
			super(bot);
			this.name = "view";
			this.path = "bot.guild.access.view";
		}

		@SuppressWarnings("null")
		@Override
		protected void execute(SlashCommandEvent event) {
			event.deferReply(true).queue();

			Guild guild = Objects.requireNonNull(event.getGuild());
			String guildId = guild.getId();
			DiscordLocale userLocale = event.getUserLocale();
			
			String[] modsId = bot.getDBUtil().accessModGet(guildId).toArray(new String[0]);
			String[] adminsId = bot.getDBUtil().accessAdminGet(guildId).toArray(new String[0]);

			EmbedBuilder embedBuilder = bot.getEmbedUtil().getEmbed(event)
				.setTitle(lu.getLocalized(userLocale, "bot.guild.access.view.embed.title"));

			if (adminsId.length == 0 && modsId.length == 0) {
				editHookEmbed(event, 
					embedBuilder.setDescription(
						lu.getLocalized(userLocale, "bot.guild.access.view.embed.none_found")
					).build()
				);
				return;
			}

			StringBuilder sb = new StringBuilder();
			// Admins
			sb.append(lu.getLocalized(userLocale, "bot.guild.access.view.embed.admin")).append("\n");

			guild.retrieveMembersByIds(false, adminsId).onSuccess(
				admins -> {
					if (admins.isEmpty()) {
						sb.append(lu.getLocalized(userLocale, "bot.guild.access.view.embed.none")).append("\n");
					}
					for (Member admin : admins) {
						sb.append("> " + admin.getAsMention()).append(" (`"+admin.getUser().getAsTag()+"`, "+admin.getId()+")").append("\n");
					}
					sb.append("\n");
					// Mods
					sb.append(lu.getLocalized(userLocale, "bot.guild.access.view.embed.mod")).append("\n");

					guild.retrieveMembersByIds(false, modsId).onSuccess(
						mods -> {
							if (mods.isEmpty()) {
								sb.append(lu.getLocalized(userLocale, "bot.guild.access.view.embed.none"));
							} else {
								for (Member mod : mods) {
									sb.append("> " + mod.getAsMention()).append(" (`"+mod.getUser().getAsTag()+"`, "+mod.getId()+")").append("\n");
								}
							}
							embedBuilder.setDescription(sb);
							editHookEmbed(event, embedBuilder.build());
							guild.pruneMemberCache();
						}
					);
					
				}
			);
		}

	}

	private class RemoveMod extends CommandBase {

		public RemoveMod(App bot) {
			super(bot);
			this.name = "mod";
			this.path = "bot.guild.access.remove.mod";
			this.options = Collections.singletonList(
				new OptionData(OptionType.USER, "member", lu.getText("bot.guild.access.remove.option_user"), true)
			);
			this.subcommandGroup = new SubcommandGroupData("remove", lu.getText("bot.guild.access.remove.help"));
		}

		@Override
		protected void execute(SlashCommandEvent event) {
			event.deferReply(true).queue();

			Member targetMember = event.optMember("member");
			if (targetMember == null) {
				editError(event, "bot.guild.access.add.no_member");
			} else {
				Member author = Objects.requireNonNull(event.getMember());
				Guild guild = Objects.requireNonNull(event.getGuild());
				String guildId = guild.getId();

				guild.retrieveMember(targetMember).queue(
					member -> {
						if (member.equals(author) || member.getUser().isBot()) {
							editError(event, "bot.guild.access.remove.not_self");
							return;
						}
						if (bot.getCheckUtil().getAccessLevel(event.getClient(), member).getLevel() >= bot.getCheckUtil().getAccessLevel(event.getClient(), author).getLevel()) {
							editError(event, "bot.guild.access.remove.is_higher");
							return;
						}
						String access = bot.getDBUtil().hasAccess(guildId, member.getId());
						if (access == null || access.equals("admin")) {
							editError(event, "bot.guild.access.remove.mod.has_no_access");
							return;
						}
						bot.getDBUtil().accessRemove(guildId, member.getId());

						EmbedBuilder embed = bot.getEmbedUtil().getEmbed(event)
							.setDescription(lu.getText(event, "bot.guild.access.remove.mod.done").replace("{member}", member.getAsMention()))
							.setColor(Constants.COLOR_SUCCESS);
							editHookEmbed(event, embed.build());

					}, 
					failure -> {
						editError(event, "bot.guild.access.remove.no_member");
					}
				);
			}
		}

	}

	private class RemoveAdmin extends CommandBase {

		public RemoveAdmin(App bot) {
			super(bot);
			this.name = "admin";
			this.path = "bot.guild.access.remove.admin";
			this.options = Collections.singletonList(
				new OptionData(OptionType.USER, "member", lu.getText("bot.guild.access.remove.option_user"), true)
			);
			this.subcommandGroup = new SubcommandGroupData("remove", lu.getText("bot.guild.access.remove.help"));
			this.accessLevel = CmdAccessLevel.OWNER;
		}

		@Override
		protected void execute(SlashCommandEvent event) {
			event.deferReply(true).queue();

			Member targetMember = event.optMember("member");
			if (targetMember == null) {
				editError(event, "bot.guild.access.add.no_member");
			} else {
				Member author = Objects.requireNonNull(event.getMember());
				Guild guild = Objects.requireNonNull(event.getGuild());
				String guildId = guild.getId();

				guild.retrieveMember(targetMember).queue(
					member -> {
						if (member.equals(author) || member.getUser().isBot()) {
							editError(event, "bot.guild.access.remove.not_self");
							return;
						}
						if (bot.getCheckUtil().getAccessLevel(event.getClient(), member).getLevel() >= bot.getCheckUtil().getAccessLevel(event.getClient(), author).getLevel()) {
							editError(event, "bot.guild.access.remove.is_higher");
							return;
						}
						String access = bot.getDBUtil().hasAccess(guildId, member.getId());
						if (access == null || access.equals("mod")) {
							editError(event, "bot.guild.access.remove.admin.has_no_access");
							return;
						}
						bot.getDBUtil().accessRemove(guildId, member.getId());

						EmbedBuilder embed = bot.getEmbedUtil().getEmbed(event)
							.setDescription(lu.getText(event, "bot.guild.access.remove.admin.done").replace("{member}", member.getAsMention()))
							.setColor(Constants.COLOR_SUCCESS);
							editHookEmbed(event, embed.build());

					}, 
					failure -> {
						editError(event, "bot.guild.access.remove.no_member");
					}
				);
			}
		}

	}
}
