package dev.fileeditor.votl.commands.role;

import java.sql.SQLException;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import dev.fileeditor.votl.base.command.SlashCommand;
import dev.fileeditor.votl.base.command.SlashCommandEvent;
import dev.fileeditor.votl.objects.AccessPermission;
import dev.fileeditor.votl.objects.CmdModule;
import dev.fileeditor.votl.objects.constants.CmdCategory;
import dev.fileeditor.votl.objects.constants.Constants;
import dev.fileeditor.votl.objects.constants.Limits;
import dev.fileeditor.votl.utils.database.managers.AutoRoleManager.AutoRoleData;

import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Role;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;

public class AutoRoleCmd extends SlashCommand {

	public AutoRoleCmd() {
		this.name = "autorole";
		this.path = "bot.roles.autorole";
		this.children = new SlashCommand[]{
			new Add(), new Remove(), new View()
		};
		this.category = CmdCategory.ROLES;
		this.module = CmdModule.ROLES;
		this.requiredPermission = AccessPermission.CMD_AUTO_ROLE;
	}

	@Override
	protected void execute(SlashCommandEvent event) {}

	private class Add extends SlashCommand {
		public Add() {
			this.name = "add";
			this.path = "bot.roles.autorole.add";
			this.options = List.of(
				new OptionData(OptionType.ROLE, "trigger", lu.getText(path+".trigger.help"), true),
				new OptionData(OptionType.ROLE, "secondary", lu.getText(path+".secondary.help"), true)
			);
		}

		@Override
		protected void execute(SlashCommandEvent event) {
			Guild guild = event.getGuild();
			assert guild != null && event.getMember() != null;

			Role trigger = event.optRole("trigger");
			Role secondary = event.optRole("secondary");
			if (trigger == null || secondary == null) {
				editError(event, "errors.option.role");
				return;
			}
			if (trigger.getIdLong() == secondary.getIdLong()) {
				editError(event, path+".same_role");
				return;
			}

			// Check roles
			final boolean whitelistEnabled = bot.getDBUtil().getGuildSettings(guild).isRoleWhitelistEnabled();
			for (Role r : List.of(trigger, secondary)) {
				String denyReason = bot.getCheckUtil().denyRole(r, guild, event.getMember(), true);
				if (denyReason != null) {
					editError(event, "errors.option.role_interact", "Role: %s\n> %s".formatted(r.getAsMention(), denyReason));
					return;
				}
				if (whitelistEnabled && !bot.getDBUtil().roles.existsRole(r.getIdLong())) {
					editError(event, "errors.role_not_whitelisted", "Role: %s".formatted(r.getAsMention()));
					return;
				}
			}

			long guildId = guild.getIdLong();
			long triggerId = trigger.getIdLong();
			long secondaryId = secondary.getIdLong();

			List<AutoRoleData> pairs = bot.getDBUtil().autoRole.getPairs(guildId);
			if (pairs.stream().anyMatch(p -> p.getTriggerRoleId() == triggerId && p.getTargetRoleId() == secondaryId)) {
				editError(event, path+".already_exists");
				return;
			}
			if (pairs.size() >= Limits.AUTO_ROLES) {
				editErrorLimit(event, "auto-role pairs", Limits.AUTO_ROLES);
				return;
			}
			if (createsCycle(pairs, triggerId, secondaryId)) {
				editError(event, path+".cycle");
				return;
			}

			try {
				bot.getDBUtil().autoRole.add(guildId, triggerId, secondaryId);
			} catch (SQLException ex) {
				editErrorDatabase(event, ex, "add auto-role pair");
				return;
			}

			editEmbed(event, bot.getEmbedUtil().getEmbed(Constants.COLOR_SUCCESS)
				.setDescription(lu.getGuildText(event, path+".done", trigger.getAsMention(), secondary.getAsMention()))
				.build());
		}

		// Does adding an edge trigger->target create a cycle with the already stored pairs?
		private boolean createsCycle(List<AutoRoleData> pairs, long trigger, long target) {
			Map<Long, List<Long>> adjacency = new HashMap<>();
			for (AutoRoleData p : pairs) {
				adjacency.computeIfAbsent(p.getTriggerRoleId(), _ -> new ArrayList<>()).add(p.getTargetRoleId());
			}
			Set<Long> visited = new HashSet<>();
			Deque<Long> stack = new ArrayDeque<>();
			stack.push(target);
			while (!stack.isEmpty()) {
				long current = stack.pop();
				if (current == trigger) return true;
				if (!visited.add(current)) continue;
				for (Long next : adjacency.getOrDefault(current, List.of())) {
					stack.push(next);
				}
			}
			return false;
		}
	}

	private class Remove extends SlashCommand {
		public Remove() {
			this.name = "remove";
			this.path = "bot.roles.autorole.remove";
			this.options = List.of(
				new OptionData(OptionType.ROLE, "trigger", lu.getText(path+".trigger.help"), true),
				new OptionData(OptionType.ROLE, "secondary", lu.getText(path+".secondary.help"), true)
			);
		}

		@Override
		protected void execute(SlashCommandEvent event) {
			Guild guild = event.getGuild();
			assert guild != null;

			Role trigger = event.optRole("trigger");
			Role secondary = event.optRole("secondary");
			if (trigger == null || secondary == null) {
				editError(event, "errors.option.role");
				return;
			}

			long guildId = guild.getIdLong();
			if (!bot.getDBUtil().autoRole.exists(guildId, trigger.getIdLong(), secondary.getIdLong())) {
				editError(event, path+".not_found");
				return;
			}

			try {
				bot.getDBUtil().autoRole.remove(guildId, trigger.getIdLong(), secondary.getIdLong());
			} catch (SQLException ex) {
				editErrorDatabase(event, ex, "remove auto-role pair");
				return;
			}

			editEmbed(event, bot.getEmbedUtil().getEmbed(Constants.COLOR_SUCCESS)
				.setDescription(lu.getGuildText(event, path+".done"))
				.build());
		}
	}

	private class View extends SlashCommand {
		public View() {
			this.name = "view";
			this.path = "bot.roles.autorole.view";
			this.ephemeral = true;
		}

		@Override
		protected void execute(SlashCommandEvent event) {
			Guild guild = event.getGuild();
			assert guild != null;

			List<AutoRoleData> pairs = bot.getDBUtil().autoRole.getPairs(guild.getIdLong());
			if (pairs.isEmpty()) {
				editEmbed(event, bot.getEmbedUtil().getEmbed().setDescription(lu.getGuildText(event, path+".empty")).build());
				return;
			}

			EmbedBuilder builder = bot.getEmbedUtil().getEmbed().setTitle(lu.getGuildText(event, path+".title"));
			StringBuilder buffer = new StringBuilder();

			for (AutoRoleData data : pairs) {
				String line = "<@&%s> → <@&%s>\n".formatted(data.getTriggerRoleId(), data.getTargetRoleId());
				if (buffer.length() + line.length() > 1024) {
					builder.addField("", buffer.toString(), false);
					buffer.setLength(0);
				}
				buffer.append(line);
			}
			builder.addField("", buffer.toString(), false);

			editEmbed(event, builder.build());
		}
	}

}
