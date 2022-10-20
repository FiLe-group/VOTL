package bot.commands.guild;

import java.util.Collections;
import java.util.Objects;

import javax.annotation.Nonnull;

import com.jagrosh.jdautilities.command.SlashCommand;
import com.jagrosh.jdautilities.command.SlashCommandEvent;
import com.jagrosh.jdautilities.doc.standard.CommandInfo;

import bot.App;
import bot.objects.CmdAccessLevel;
import bot.objects.constants.CmdCategory;
import bot.objects.constants.Constants;
import bot.utils.exception.CheckException;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.interactions.InteractionHook;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import net.dv8tion.jda.api.interactions.commands.build.SubcommandGroupData;

@CommandInfo(
	name = "access",
	usage = "/access <show / add / remove>"
)
public class AccessCmd extends SlashCommand {
	
	private static App bot;

	private static final boolean mustSetup = true;
	private static final String MODULE = null;
	private static final CmdAccessLevel ACCESS_LEVEL = CmdAccessLevel.ADMIN;
	private static final CmdAccessLevel ACCESS_LEVEL2 = CmdAccessLevel.OWNER;

	protected static Permission[] userPerms = new Permission[0];
	protected static Permission[] botPerms = new Permission[0];

	public AccessCmd(App bot) {
		this.name = "access";
		this.help = bot.getMsg("bot.guild.access.help");
		this.category = CmdCategory.GUILD;
		AccessCmd.bot = bot;
		this.children = new SlashCommand[]{new AddMod(), new AddAdmin(), new View(), new RemoveMod(), new RemoveAdmin()};
	}

	@Override
	protected void execute(SlashCommandEvent event) {
		
	}

	private static class AddMod extends SlashCommand {

		public AddMod() {
			this.name = "mod";
			this.help = bot.getMsg("bot.guild.access.add.mod.help");
			this.options = Collections.singletonList(
				new OptionData(OptionType.USER, "member", bot.getMsg("bot.guild.access.add.option_user"), true)
			);
			this.subcommandGroup = new SubcommandGroupData("add", bot.getMsg("bot.guild.access.add.help"));
		}

		@Override
		protected void execute(SlashCommandEvent event) {
			event.deferReply(true).queue(
				hook -> {
					try {
						// check access
						bot.getCheckUtil().hasAccess(event, ACCESS_LEVEL)
						// check module enabled
							.moduleEnabled(event, MODULE)
						// check user perms
							.hasPermissions(event, userPerms)
						// check bots perms
							.hasPermissions(event, true, botPerms);
						// check setup
						if (mustSetup) {
							bot.getCheckUtil().guildExists(event, mustSetup);
						}
					} catch (CheckException ex) {
						hook.editOriginal(ex.getEditData()).queue();
						return;
					}

					Member targetMember = Objects.requireNonNull(event.getOption("member", OptionMapping::getAsMember));
					sendReply(event, hook, targetMember);
				}
			);
		}

		private void sendReply(SlashCommandEvent event, InteractionHook hook, @Nonnull Member targetMember) {
			Member author = Objects.requireNonNull(event.getMember());
			Guild guild = Objects.requireNonNull(event.getGuild());
			String guildId = guild.getId();

			guild.retrieveMember(targetMember).queue(
				member -> {
					if (member.equals(author) || member.getUser().isBot()) {
						hook.editOriginal(bot.getEmbedUtil().getError(event, "bot.guild.access.add.not_self")).queue();
						return;
					}
					if (bot.getCheckUtil().getAccessLevel(event, member).getLevel() >= bot.getCheckUtil().getAccessLevel(event, author).getLevel()) {
						hook.editOriginal(bot.getEmbedUtil().getError(event, "bot.guild.access.add.is_higher")).queue();
						return;
					}
					String access = bot.getDBUtil().hasAccess(guildId, member.getId());
					if (access != null && access.equals("mod")) {
						hook.editOriginal(bot.getEmbedUtil().getError(event, "bot.guild.access.add.mod.already")).queue();
						return;
					}
					if (access != null && access.equals("admin")) {
						bot.getDBUtil().accessChange(guildId, member.getId(), false);
					} else {
						bot.getDBUtil().accessAdd(guildId, member.getId(), false);
					}

					EmbedBuilder embed = bot.getEmbedUtil().getEmbed(event)
						.setDescription(bot.getMsg(guildId, "bot.guild.access.add.mod.done").replace("{member}", member.getAsMention()))
						.setColor(Constants.COLOR_SUCCESS);
					hook.editOriginalEmbeds(embed.build()).queue();
				},
				error -> {
					// remake to specify error response - not in this guild or user does not exist
					// add errors - UNKNOWN_MEMBER & UNKNOWN_USER
					hook.editOriginal(bot.getEmbedUtil().getError(event, "bot.guild.access.add.no_member")).queue();
				}
			);
			
		}
	}

	private static class AddAdmin extends SlashCommand {

		public AddAdmin() {
			this.name = "admin";
			this.help = bot.getMsg("bot.guild.access.add.admin.help");
			this.options = Collections.singletonList(
				new OptionData(OptionType.USER, "member", bot.getMsg("bot.guild.access.add.option_user"), true)
			);
			this.subcommandGroup = new SubcommandGroupData("add", bot.getMsg("bot.guild.access.add.help"));
		}

		@Override
		protected void execute(SlashCommandEvent event) {
			event.deferReply(true).queue(
				hook -> {
					try {
						// check access
						bot.getCheckUtil().hasAccess(event, ACCESS_LEVEL2)
						// check module enabled
							.moduleEnabled(event, MODULE)
						// check user perms
							.hasPermissions(event, userPerms)
						// check bots perms
							.hasPermissions(event, true, botPerms);
						// check setup
						if (mustSetup) {
							bot.getCheckUtil().guildExists(event, mustSetup);
						}
					} catch (CheckException ex) {
						hook.editOriginal(ex.getEditData()).queue();
						return;
					}

					Member targetMember = Objects.requireNonNull(event.getOption("member", OptionMapping::getAsMember));
					sendReply(event, hook, targetMember);
				}
			);
		}

		private void sendReply(SlashCommandEvent event, InteractionHook hook, @Nonnull Member targetMember) {
			Member author = Objects.requireNonNull(event.getMember());
			Guild guild = Objects.requireNonNull(event.getGuild());
			String guildId = guild.getId();

			guild.retrieveMember(targetMember).queue(
				member -> {
					if (member.equals(author) || member.getUser().isBot()) {
						hook.editOriginal(bot.getEmbedUtil().getError(event, "bot.guild.access.add.not_self")).queue();
						return;
					}
					if (bot.getCheckUtil().getAccessLevel(event, member).getLevel() >= bot.getCheckUtil().getAccessLevel(event, author).getLevel()) {
						hook.editOriginal(bot.getEmbedUtil().getError(event, "bot.guild.access.add.is_higher")).queue();
						return;
					}
					String access = bot.getDBUtil().hasAccess(guildId, member.getId());
					if (access != null && access.equals("admin")) {
						hook.editOriginal(bot.getEmbedUtil().getError(event, "bot.guild.access.add.admin.already")).queue();
						return;
					}
					if (access != null && access.equals("mod")) {
						bot.getDBUtil().accessChange(guildId, member.getId(), true);
					} else {
						bot.getDBUtil().accessAdd(guildId, member.getId(), true);
					}

					EmbedBuilder embed = bot.getEmbedUtil().getEmbed(event)
						.setDescription(bot.getMsg(guildId, "bot.guild.access.add.admin.done").replace("{member}", member.getAsMention()))
						.setColor(Constants.COLOR_SUCCESS);
					hook.editOriginalEmbeds(embed.build()).queue();

				}, 
				failure -> {
					hook.editOriginal(bot.getEmbedUtil().getError(event, "bot.guild.access.add.no_member")).queue();
				}
			);

		}
	}

	private static class View extends SlashCommand {

		public View() {
			this.name = "view";
			this.help = bot.getMsg("bot.guild.access.view.help");
		}

		@Override
		protected void execute(SlashCommandEvent event) {
			event.deferReply(true).queue(
				hook -> {
					try {
						// check access
						bot.getCheckUtil().hasAccess(event, ACCESS_LEVEL)
						// check module enabled
							.moduleEnabled(event, MODULE)
						// check user perms
							.hasPermissions(event, userPerms)
						// check bots perms
							.hasPermissions(event, true, botPerms);
						// check setup
						if (mustSetup) {
							bot.getCheckUtil().guildExists(event, mustSetup);
						}
					} catch (CheckException ex) {
						hook.editOriginal(ex.getEditData()).queue();
						return;
					}

					sendReply(event, hook);
				}
			);
		}

		@SuppressWarnings("null")
		private void sendReply(SlashCommandEvent event, InteractionHook hook) {

			Member member = Objects.requireNonNull(event.getMember());
			Guild guild = Objects.requireNonNull(event.getGuild());
			String guildId = guild.getId();
			
			String[] modsId = bot.getDBUtil().accessModGet(guildId).toArray(new String[0]);
			String[] adminsId = bot.getDBUtil().accessAdminGet(guildId).toArray(new String[0]);

			EmbedBuilder embedBuilder = bot.getEmbedUtil().getEmbed(event)
				.setTitle(bot.getMsg(guildId, "bot.guild.access.view.embed.title"));

			if (adminsId.length == 0 && modsId.length == 0) {
				hook.editOriginalEmbeds(
					embedBuilder.setDescription(
						bot.getMsg(guildId, "bot.guild.access.view.embed.none_found")
					).build()
				).queue();
				return;
			}
			
			StringBuilder sb = new StringBuilder();
			// Admins
			sb.append(bot.getMsg(guildId, "bot.guild.access.view.embed.admin")).append("\n");

			guild.retrieveMembersByIds(false, adminsId).onSuccess(
				admins -> {
					if (admins.isEmpty()) {
						sb.append(bot.getMsg(guildId, "bot.guild.access.view.embed.none")).append("\n");
					}
					for (Member admin : admins) {
						sb.append("> " + admin.getAsMention()).append(" (`"+admin.getUser().getAsTag()+"`, "+admin.getId()+")").append("\n");
					}
					sb.append("\n");
					// Mods
					sb.append(bot.getMsg(guildId, "bot.guild.access.view.embed.mod")).append("\n");

					guild.retrieveMembersByIds(false, modsId).onSuccess(
						mods -> {
							if (mods.isEmpty()) {
								sb.append(bot.getMsg(guildId, "bot.guild.access.view.embed.none"));
							} else {
								for (Member mod : mods) {
									sb.append("> " + mod.getAsMention()).append(" (`"+mod.getUser().getAsTag()+"`, "+mod.getId()+")").append("\n");
								}
							}
							embedBuilder.setDescription(sb);
							hook.editOriginalEmbeds(embedBuilder.build()).queue();
							guild.pruneMemberCache();
						}
					);
					
				}
			);

		}
	}

	private static class RemoveMod extends SlashCommand {

		public RemoveMod() {
			this.name = "mod";
			this.help = bot.getMsg("bot.guild.access.remove.mod.help");
			this.options = Collections.singletonList(
				new OptionData(OptionType.USER, "member", bot.getMsg("bot.guild.access.remove.option_user"), true)
			);
			this.subcommandGroup = new SubcommandGroupData("remove", bot.getMsg("bot.guild.access.remove.help"));
		}

		@Override
		protected void execute(SlashCommandEvent event) {
			event.deferReply(true).queue(
				hook -> {
					try {
						// check access
						bot.getCheckUtil().hasAccess(event, ACCESS_LEVEL)
						// check module enabled
							.moduleEnabled(event, MODULE)
						// check user perms
							.hasPermissions(event, userPerms)
						// check bots perms
							.hasPermissions(event, true, botPerms);
						// check setup
						if (mustSetup) {
							bot.getCheckUtil().guildExists(event, mustSetup);
						}
					} catch (CheckException ex) {
						hook.editOriginal(ex.getEditData()).queue();
						return;
					}

					Member targetMember = Objects.requireNonNull(event.getOption("member", OptionMapping::getAsMember));
					sendReply(event, hook, targetMember);
				}
			);
		}

		private void sendReply(SlashCommandEvent event, InteractionHook hook, @Nonnull Member targetMember) {
			Member author = Objects.requireNonNull(event.getMember());
			Guild guild = Objects.requireNonNull(event.getGuild());
			String guildId = guild.getId();

			guild.retrieveMember(targetMember).queue(
				member -> {
					if (member.equals(author) || member.getUser().isBot()) {
						hook.editOriginal(bot.getEmbedUtil().getError(event, "bot.guild.access.remove.not_self")).queue();
						return;
					}
					if (bot.getCheckUtil().getAccessLevel(event, member).getLevel() >= bot.getCheckUtil().getAccessLevel(event, author).getLevel()) {
						hook.editOriginal(bot.getEmbedUtil().getError(event, "bot.guild.access.remove.is_higher")).queue();
						return;
					}
					String access = bot.getDBUtil().hasAccess(guildId, member.getId());
					if (access == null || access.equals("admin")) {
						hook.editOriginal(bot.getEmbedUtil().getError(event, "bot.guild.access.remove.mod.has_no_access")).queue();
						return;
					}
					bot.getDBUtil().accessRemove(guildId, member.getId());

					EmbedBuilder embed = bot.getEmbedUtil().getEmbed(event)
						.setDescription(bot.getMsg(guildId, "bot.guild.access.remove.mod.done").replace("{member}", member.getAsMention()))
						.setColor(Constants.COLOR_SUCCESS);
					hook.editOriginalEmbeds(embed.build()).queue();

				}, 
				failure -> {
					hook.editOriginal(bot.getEmbedUtil().getError(event, "bot.guild.access.remove.no_member")).queue();
				}
			);

		}
	}

	private static class RemoveAdmin extends SlashCommand {

		public RemoveAdmin() {
			this.name = "admin";
			this.help = bot.getMsg("bot.guild.access.remove.admin.help");
			this.options = Collections.singletonList(
				new OptionData(OptionType.USER, "member", bot.getMsg("bot.guild.access.remove.option_user"), true)
			);
			this.subcommandGroup = new SubcommandGroupData("remove", bot.getMsg("bot.guild.access.remove.help"));
		}

		@Override
		protected void execute(SlashCommandEvent event) {
			event.deferReply(true).queue(
				hook -> {
					try {
						// check access
						bot.getCheckUtil().hasAccess(event, ACCESS_LEVEL2)
						// check module enabled
							.moduleEnabled(event, MODULE)
						// check user perms
							.hasPermissions(event, userPerms)
						// check bots perms
							.hasPermissions(event, true, botPerms);
						// check setup
						if (mustSetup) {
							bot.getCheckUtil().guildExists(event, mustSetup);
						}
					} catch (CheckException ex) {
						hook.editOriginal(ex.getEditData()).queue();
						return;
					}

					Member targetMember = Objects.requireNonNull(event.getOption("member", OptionMapping::getAsMember));
					sendReply(event, hook, targetMember);
				}
			);
		}

		private void sendReply(SlashCommandEvent event, InteractionHook hook, @Nonnull Member targetMember) {
			Member author = Objects.requireNonNull(event.getMember());
			Guild guild = Objects.requireNonNull(event.getGuild());
			String guildId = guild.getId();

			guild.retrieveMember(targetMember).queue(
				member -> {
					if (member.equals(author) || member.getUser().isBot()) {
						hook.editOriginal(bot.getEmbedUtil().getError(event, "bot.guild.access.remove.not_self")).queue();
						return;
					}
					if (bot.getCheckUtil().getAccessLevel(event, member).getLevel() >= bot.getCheckUtil().getAccessLevel(event, author).getLevel()) {
						hook.editOriginal(bot.getEmbedUtil().getError(event, "bot.guild.access.remove.is_higher")).queue();
						return;
					}
					String access = bot.getDBUtil().hasAccess(guildId, member.getId());
					if (access == null || access.equals("mod")) {
						hook.editOriginal(bot.getEmbedUtil().getError(event, "bot.guild.access.remove.admin.has_no_access")).queue();
						return;
					}
					bot.getDBUtil().accessRemove(guildId, member.getId());

					EmbedBuilder embed = bot.getEmbedUtil().getEmbed(event)
						.setDescription(bot.getMsg(guildId, "bot.guild.access.remove.admin.done").replace("{member}", member.getAsMention()))
						.setColor(Constants.COLOR_SUCCESS);
					hook.editOriginalEmbeds(embed.build()).queue();

				}, 
				failure -> {
					hook.editOriginal(bot.getEmbedUtil().getError(event, "bot.guild.access.remove.no_member")).queue();
				}
			);

		}
	}
}
