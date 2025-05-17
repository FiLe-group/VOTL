package dev.fileeditor.votl.commands.moderation;

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import dev.fileeditor.votl.base.command.SlashCommand;
import dev.fileeditor.votl.base.command.SlashCommandEvent;
import dev.fileeditor.votl.objects.CmdAccessLevel;
import dev.fileeditor.votl.objects.CmdModule;
import dev.fileeditor.votl.objects.constants.CmdCategory;
import dev.fileeditor.votl.objects.constants.Constants;
import dev.fileeditor.votl.utils.database.managers.ServerBlacklistManager;
import dev.fileeditor.votl.utils.message.MessageUtil;

import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.MessageEmbed;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;

public class BlacklistCmd extends SlashCommand {
	
	public BlacklistCmd() {
		this.name = "blacklist";
		this.path = "bot.moderation.blacklist";
		this.children = new SlashCommand[]{new View(), new Search(), new Remove()};
		this.category = CmdCategory.MODERATION;
		this.module = CmdModule.MODERATION;
		this.accessLevel = CmdAccessLevel.OPERATOR;
	}

	@Override
	protected void execute(SlashCommandEvent event) {}

	private class View extends SlashCommand {
		public View() {
			this.name = "view";
			this.path = "bot.moderation.blacklist.view";
			this.options = List.of(
				new OptionData(OptionType.INTEGER, "group", lu.getText(path+".group.help"), true, true).setMinValue(1),
				new OptionData(OptionType.INTEGER, "page", lu.getText(path+".page.help")).setMinValue(1)
			);
		}

		@Override
		protected void execute(SlashCommandEvent event) {
			event.deferReply(true).queue();

			Integer groupId = event.optInteger("group");
			long guildId = event.getGuild().getIdLong();
			if ( !(bot.getDBUtil().group.isOwner(groupId, guildId) || bot.getDBUtil().group.canManage(groupId, guildId)) ) {
				// Is not group's owner or manager
				editError(event, path+".cant_view");
				return;
			}

			Integer page = event.optInteger("page", 1);
			var list = bot.getDBUtil().serverBlacklist.getByPage(groupId, page);
			if (list.isEmpty()) {
				editEmbed(event, bot.getEmbedUtil().getEmbed().setDescription(lu.getText(event, path+".empty").formatted(page)).build());
				return;
			}
			int pages = (int) Math.ceil(bot.getDBUtil().serverBlacklist.countEntries(groupId) / 20.0);

			EmbedBuilder builder = new EmbedBuilder().setColor(Constants.COLOR_DEFAULT)
				.setTitle(lu.getText(event, path+".title").formatted(groupId, page, pages));
			list.forEach(data ->
				builder.addField(
					"ID: %s".formatted(data.getUserId()),
					lu.getText(event, path+".value").formatted(
						Optional.ofNullable(event.getJDA().getGuildById(data.getGuildId())).map(Guild::getName).orElse("-"),
						"<@%s>".formatted(data.getModId()),
						Optional.ofNullable(data.getReason()).map(v -> MessageUtil.limitString(v, 100)).orElse("-")
					),
					true
				)
			);

			editEmbed(event, builder.build());
		}
	}

	private class Search extends SlashCommand {
		public Search() {
			this.name = "search";
			this.path = "bot.moderation.blacklist.search";
			this.options = List.of(
				new OptionData(OptionType.USER, "user", lu.getText(path+".user.help"), true)
			);
		}

		@Override
		protected void execute(SlashCommandEvent event) {
			event.deferReply(true).queue();

			long guildId = event.getGuild().getIdLong();
			final List<Integer> groupIds = new ArrayList<>();
			groupIds.addAll(bot.getDBUtil().group.getOwnedGroups(guildId));
			groupIds.addAll(bot.getDBUtil().group.getGuildGroups(guildId));
			if (groupIds.isEmpty()) {
				editError(event, path+".cant_view");
				return;
			}

			User user = event.optUser("user");

			List<MessageEmbed> embeds = new ArrayList<>();
			for (ServerBlacklistManager.BlacklistData data : bot.getDBUtil().serverBlacklist.searchUserId(user.getIdLong())) {
				embeds.add(bot.getEmbedUtil().getEmbed()
					.setTitle("Group #`%s`".formatted(data.getGroupId()))
					.setDescription(lu.getText(event, path+".value")
						.formatted(
							"%s `%s`".formatted(user.getAsMention(), user.getId()),
							Optional.ofNullable(event.getJDA().getGuildById(data.getGuildId())).map(Guild::getName).orElse("-"),
							"<@%s>".formatted(data.getModId()),
							Optional.ofNullable(data.getReason()).map(v -> MessageUtil.limitString(v, 100)).orElse("-")
						)
					).build()
				);
			}

			if (embeds.isEmpty()) {
				editEmbed(event, bot.getEmbedUtil().getEmbed()
					.setDescription(lu.getText(event, path+".not_found").formatted(user.getAsMention()))
					.build());
			} else {
				event.getHook().editOriginalEmbeds(embeds).queue();
			}
		}
	}

	private class Remove extends SlashCommand {
		public Remove() {
			this.name = "remove";
			this.path = "bot.moderation.blacklist.remove";
			this.options = List.of(
				new OptionData(OptionType.INTEGER, "group", lu.getText(path+".group.help"), true, true).setMinValue(1),
				new OptionData(OptionType.USER, "user", lu.getText(path+".user.help"), true)
			);
		}

		@Override
		protected void execute(SlashCommandEvent event) {
			event.deferReply().queue();

			Integer groupId = event.optInteger("group");
			long guildId = event.getGuild().getIdLong();
			if ( !(bot.getDBUtil().group.isOwner(groupId, guildId) || bot.getDBUtil().group.canManage(groupId, guildId)) ) {
				// Is not group's owner or manager
				editError(event, path+".cant_view");
				return;
			}

			User user = event.optUser("user");
			if (bot.getDBUtil().serverBlacklist.inGroupUser(groupId, user.getIdLong())) {
				try {
					bot.getDBUtil().serverBlacklist.removeUser(groupId, user.getIdLong());
				} catch (SQLException ex) {
					editErrorDatabase(event, ex, "blacklist remove user");
					return;
				}
				// Log into master
				bot.getLogger().mod.onBlacklistRemoved(event.getUser(), user, groupId);
				// Reply
				editEmbed(event, bot.getEmbedUtil().getEmbed(Constants.COLOR_SUCCESS)
					.setDescription(lu.getText(event, path+".done_user").formatted(user.getAsMention(), user.getId(), groupId))
					.build()
				);
			} else {
				editError(event, path+".no_user", "Received: "+user.getAsMention());
			}
		}
	}
	
}
