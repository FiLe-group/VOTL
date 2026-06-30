package dev.fileeditor.votl.commands.moderation;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import dev.fileeditor.votl.base.command.SlashCommand;
import dev.fileeditor.votl.base.command.SlashCommandEvent;
import dev.fileeditor.votl.objects.AccessPermission;
import dev.fileeditor.votl.objects.CmdModule;
import dev.fileeditor.votl.objects.constants.CmdCategory;
import dev.fileeditor.votl.utils.database.managers.CaseManager.CaseData;
import dev.fileeditor.votl.utils.message.MessageUtil;

import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import net.dv8tion.jda.api.utils.TimeFormat;

public class BanInfoCmd extends SlashCommand {

	public BanInfoCmd() {
		this.name = "baninfo";
		this.path = "bot.moderation.baninfo";
		this.options = List.of(
			new OptionData(OptionType.USER, "user", lu.getText(path+".user.help"), true)
		);
		this.category = CmdCategory.MODERATION;
		this.module = CmdModule.MODERATION;
		this.requiredPermission = AccessPermission.CMD_BAN_INFO;
		this.ephemeral = true;
	}

	@Override
	protected void execute(SlashCommandEvent event) {
		assert event.getGuild() != null;
		User target = event.optUser("user");
		if (target == null) {
			editError(event, "errors.option.user");
			return;
		}

		Set<Long> guildIds = bot.getHelper().collectAllRelatedGuildIds(event.getGuild().getIdLong());
		if (guildIds.isEmpty()) {
			editEmbed(event, bot.getEmbedUtil().getEmbed()
				.setDescription(lu.getGuildText(event, path+".no_groups"))
				.build());
			return;
		}

		List<CaseData> bans = bot.getDBUtil().cases.getBansByUser(target.getIdLong(), guildIds);

		EmbedBuilder builder = bot.getEmbedUtil().getEmbed()
			.setTitle(lu.getGuildText(event, path+".title", target.getName()))
			.setThumbnail(target.getEffectiveAvatarUrl())
			.setFooter("ID: " + target.getId());

		if (bans.isEmpty()) {
			builder.setDescription(lu.getGuildText(event, path+".no_bans", guildIds.size()));
			editEmbed(event, builder.build());
			return;
		}

		List<CaseData> activeBans = new ArrayList<>();
		List<CaseData> pastBans = new ArrayList<>();
		for (CaseData ban : bans) {
			(ban.isActive() ? activeBans : pastBans).add(ban);
		}

		if (!activeBans.isEmpty()) {
			builder.addField(
				lu.getGuildText(event, path+".active_title", activeBans.size()),
				buildBanList(activeBans, event),
				false
			);
		}
		if (!pastBans.isEmpty()) {
			builder.addField(
				lu.getGuildText(event, path+".past_title", pastBans.size()),
				buildBanList(pastBans, event),
				false
			);
		}

		editEmbed(event, builder.build());
	}

	private String buildBanList(List<CaseData> bans, SlashCommandEvent event) {
		StringBuilder sb = new StringBuilder();
		for (CaseData ban : bans) {
			Guild guild = event.getJDA().getGuildById(ban.getGuildId());
			String guildName = guild != null ? guild.getName() : String.valueOf(ban.getGuildId());
			String mod = ban.getModId() > 0
				? "<@%d>".formatted(ban.getModId())
				: (ban.getModTag() != null ? ban.getModTag() : "-");
			String line = "**%s** • %s • by %s\n> %s\n".formatted(
				guildName,
				TimeFormat.DATE_TIME_SHORT.format(ban.getTimeStart()),
				mod,
				MessageUtil.limitString(ban.getReason(), 100)
			);
			if (sb.length() + line.length() > 2000) {
				sb.append(lu.getGuildText(event, path+".truncated", bans.size()));
				break;
			}
			sb.append(line);
		}
		return sb.toString();
	}

}
