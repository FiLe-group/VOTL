package votl.commands.guild;

import java.util.Collections;
import java.util.Objects;

import javax.annotation.Nonnull;

import votl.App;
import votl.objects.CmdAccessLevel;
import votl.objects.command.SlashCommand;
import votl.objects.command.SlashCommandEvent;
import votl.objects.constants.CmdCategory;
import votl.objects.constants.Constants;
import votl.utils.message.LocaleUtil;

import com.jagrosh.jdautilities.doc.standard.CommandInfo;

import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.interactions.DiscordLocale;
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

	public AccessCmd(App bot) {
		this.name = "access";
		this.path = "bot.guild.access";
		this.children = new SlashCommand[]{new AddMod(bot.getLocaleUtil()), new AddAdmin(bot.getLocaleUtil()),
			new View(), new RemoveMod(bot.getLocaleUtil()), new RemoveAdmin(bot.getLocaleUtil())};
		this.bot = bot;
		this.category = CmdCategory.GUILD;
		this.accessLevel = CmdAccessLevel.ADMIN;
		this.mustSetup = true;
	}

	@Override
	protected void execute(SlashCommandEvent event) {
		
	}

	private class AddMod extends SlashCommand {

		public AddMod(LocaleUtil lu) {
			this.name = "mod";
			this.path = "bot.guild.access.add.mod";
			this.options = Collections.singletonList(
				new OptionData(OptionType.USER, "member", lu.getText("bot.guild.access.add.option_user"), true)
			);
			this.subcommandGroup = new SubcommandGroupData("add", lu.getText("bot.guild.access.add.help"));
		}

		@Override
		protected void execute(SlashCommandEvent event) {
			event.deferReply(true).queue(
				hook -> {
					Member targetMember = Objects.requireNonNull(event.getOption("member", OptionMapping::getAsMember));
					sendReply(event, hook, targetMember);
				}
			);
		}

		private void sendReply(SlashCommandEvent event, InteractionHook hook, @Nonnull Member targetMember) {
			Member author = Objects.requireNonNull(event.getMember());
			Guild guild = Objects.requireNonNull(event.getGuild());
			String guildId = guild.getId();
			DiscordLocale userLocale = event.getUserLocale();

			guild.retrieveMember(targetMember).queue(
				member -> {
					if (member.equals(author) || member.getUser().isBot()) {
						hook.editOriginal(bot.getEmbedUtil().getError(event, "bot.guild.access.add.not_self")).queue();
						return;
					}
					if (bot.getCheckUtil().getAccessLevel(event.getClient(), member).getLevel() >= bot.getCheckUtil().getAccessLevel(event.getClient(), author).getLevel()) {
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
						.setDescription(lu.getLocalized(userLocale, "bot.guild.access.add.mod.done").replace("{member}", member.getAsMention()))
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

	private class AddAdmin extends SlashCommand {

		public AddAdmin(LocaleUtil lu) {
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
			event.deferReply(true).queue(
				hook -> {
					Member targetMember = Objects.requireNonNull(event.getOption("member", OptionMapping::getAsMember));
					sendReply(event, hook, targetMember);
				}
			);
		}

		private void sendReply(SlashCommandEvent event, InteractionHook hook, @Nonnull Member targetMember) {
			Member author = Objects.requireNonNull(event.getMember());
			Guild guild = Objects.requireNonNull(event.getGuild());
			String guildId = guild.getId();
			DiscordLocale userLocale = event.getUserLocale();

			guild.retrieveMember(targetMember).queue(
				member -> {
					if (member.equals(author) || member.getUser().isBot()) {
						hook.editOriginal(bot.getEmbedUtil().getError(event, "bot.guild.access.add.not_self")).queue();
						return;
					}
					if (bot.getCheckUtil().getAccessLevel(event.getClient(), member).getLevel() >= bot.getCheckUtil().getAccessLevel(event.getClient(), author).getLevel()) {
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
						.setDescription(lu.getLocalized(userLocale, "bot.guild.access.add.admin.done").replace("{member}", member.getAsMention()))
						.setColor(Constants.COLOR_SUCCESS);
					hook.editOriginalEmbeds(embed.build()).queue();

				}, 
				failure -> {
					hook.editOriginal(bot.getEmbedUtil().getError(event, "bot.guild.access.add.no_member")).queue();
				}
			);

		}
	}

	private class View extends SlashCommand {

		public View() {
			this.name = "view";
			this.path = "bot.guild.access.view";
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
			
			String[] modsId = bot.getDBUtil().accessModGet(guildId).toArray(new String[0]);
			String[] adminsId = bot.getDBUtil().accessAdminGet(guildId).toArray(new String[0]);

			EmbedBuilder embedBuilder = bot.getEmbedUtil().getEmbed(event)
				.setTitle(lu.getLocalized(userLocale, "bot.guild.access.view.embed.title"));

			if (adminsId.length == 0 && modsId.length == 0) {
				hook.editOriginalEmbeds(
					embedBuilder.setDescription(
						lu.getLocalized(userLocale, "bot.guild.access.view.embed.none_found")
					).build()
				).queue();
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
							hook.editOriginalEmbeds(embedBuilder.build()).queue();
							guild.pruneMemberCache();
						}
					);
					
				}
			);

		}
	}

	private class RemoveMod extends SlashCommand {

		public RemoveMod(LocaleUtil lu) {
			this.name = "mod";
			this.path = "bot.guild.access.remove.mod";
			this.options = Collections.singletonList(
				new OptionData(OptionType.USER, "member", lu.getText("bot.guild.access.remove.option_user"), true)
			);
			this.subcommandGroup = new SubcommandGroupData("remove", lu.getText("bot.guild.access.remove.help"));
		}

		@Override
		protected void execute(SlashCommandEvent event) {
			event.deferReply(true).queue(
				hook -> {
					Member targetMember = Objects.requireNonNull(event.getOption("member", OptionMapping::getAsMember));
					sendReply(event, hook, targetMember);
				}
			);
		}

		private void sendReply(SlashCommandEvent event, InteractionHook hook, @Nonnull Member targetMember) {
			Member author = Objects.requireNonNull(event.getMember());
			Guild guild = Objects.requireNonNull(event.getGuild());
			String guildId = guild.getId();
			DiscordLocale userLocale = event.getUserLocale();

			guild.retrieveMember(targetMember).queue(
				member -> {
					if (member.equals(author) || member.getUser().isBot()) {
						hook.editOriginal(bot.getEmbedUtil().getError(event, "bot.guild.access.remove.not_self")).queue();
						return;
					}
					if (bot.getCheckUtil().getAccessLevel(event.getClient(), member).getLevel() >= bot.getCheckUtil().getAccessLevel(event.getClient(), author).getLevel()) {
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
						.setDescription(lu.getLocalized(userLocale, "bot.guild.access.remove.mod.done").replace("{member}", member.getAsMention()))
						.setColor(Constants.COLOR_SUCCESS);
					hook.editOriginalEmbeds(embed.build()).queue();

				}, 
				failure -> {
					hook.editOriginal(bot.getEmbedUtil().getError(event, "bot.guild.access.remove.no_member")).queue();
				}
			);

		}
	}

	private class RemoveAdmin extends SlashCommand {

		public RemoveAdmin(LocaleUtil lu) {
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
			event.deferReply(true).queue(
				hook -> {
					Member targetMember = Objects.requireNonNull(event.getOption("member", OptionMapping::getAsMember));
					sendReply(event, hook, targetMember);
				}
			);
		}

		private void sendReply(SlashCommandEvent event, InteractionHook hook, @Nonnull Member targetMember) {
			Member author = Objects.requireNonNull(event.getMember());
			Guild guild = Objects.requireNonNull(event.getGuild());
			String guildId = guild.getId();
			DiscordLocale userLocale = event.getUserLocale();

			guild.retrieveMember(targetMember).queue(
				member -> {
					if (member.equals(author) || member.getUser().isBot()) {
						hook.editOriginal(bot.getEmbedUtil().getError(event, "bot.guild.access.remove.not_self")).queue();
						return;
					}
					if (bot.getCheckUtil().getAccessLevel(event.getClient(), member).getLevel() >= bot.getCheckUtil().getAccessLevel(event.getClient(), author).getLevel()) {
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
						.setDescription(lu.getLocalized(userLocale, "bot.guild.access.remove.admin.done").replace("{member}", member.getAsMention()))
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
