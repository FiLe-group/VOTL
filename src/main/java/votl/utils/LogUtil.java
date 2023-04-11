package votl.utils;

import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;

import javax.annotation.Nonnull;

import votl.App;
import votl.objects.constants.Constants;
import votl.utils.message.LocaleUtil;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.MessageEmbed;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.entities.Guild.Ban;
import net.dv8tion.jda.api.interactions.DiscordLocale;

public class LogUtil {

	private final App bot;
	private final LocaleUtil lu;

	private final String path = "bot.moderation.embeds.";

	public LogUtil(App bot) {
		this.bot = bot;
		this.lu = bot.getLocaleUtil();
	}
	
	@Nonnull
	public MessageEmbed getBanEmbed(DiscordLocale locale, Map<String, Object> banMap) {
		return getBanEmbed(locale, banMap, null);
	}

	@Nonnull
	public MessageEmbed getBanEmbed(DiscordLocale locale, Map<String, Object> banMap, String userIcon) {
		return getBanEmbed(locale, Integer.parseInt(banMap.get("banId").toString()), banMap.get("userTag").toString(),
			banMap.get("userId").toString(), banMap.get("modTag").toString(), banMap.get("modId").toString(), Timestamp.valueOf(banMap.get("timeStart").toString()),
			Duration.parse(banMap.get("duration").toString()), banMap.get("reason").toString(), userIcon, true)
			.build();
	}

	@Nonnull
	private EmbedBuilder getBanEmbed(DiscordLocale locale, Integer banId, String userTag, String userId, String modTag, String modId, Timestamp start, Duration duration, String reason, String userIcon, Boolean formatMod) {
		Instant timeStart = start.toInstant();
		Instant timeEnd = timeStart.plus(duration);
		return bot.getEmbedUtil().getEmbed().setColor(Constants.COLOR_FAILURE)
			.setAuthor(lu.getLocalized(locale, path+"ban.title").replace("{case_id}", banId.toString()).replace("{user_tag}", userTag), null, userIcon)
			.addField(lu.getLocalized(locale, path+"ban.user"), String.format("<@%s>", userId), true)
			.addField(lu.getLocalized(locale, path+"ban.mod"), (formatMod ? String.format("<@%s>", modId) : modTag), true)
			.addField(lu.getLocalized(locale, path+"ban.duration"), duration.isZero() ? lu.getLocalized(locale, path+"permanently") : 
				lu.getLocalized(locale, path+"temporary")
					.replace("{time}", bot.getTimeUtil().formatTime(timeEnd, false)), true)
			.addField(lu.getLocalized(locale, path+"ban.reason"), reason, true)
			.setFooter("ID: "+userId)
			.setTimestamp(timeStart);
	}

	@Nonnull
	public MessageEmbed getSyncBanEmbed(DiscordLocale locale, Guild master, User enforcer, User target, String reason) {
		return bot.getEmbedUtil().getEmbed().setColor(Constants.COLOR_FAILURE)
			.setAuthor(lu.getLocalized(locale, path+"ban.title_synced").replace("{user_tag}", target.getAsTag()), null, target.getAvatarUrl())
			.addField(lu.getLocalized(locale, path+"ban.user"), target.getAsMention(), true)
			.addField(lu.getLocalized(locale, path+"ban.reason"), reason, true)
			.addField(lu.getLocalized(locale, path+"ban.master"), "`"+master.getName()+"` (#"+master.getId()+")", true)
			.addField(lu.getLocalized(locale, path+"ban.enforcer"), enforcer.getAsTag(), true)
			.setFooter("ID: "+target.getId())
			.setTimestamp(Instant.now())
			.build();
	}

	@Nonnull
	public MessageEmbed getSyncUnbanEmbed(DiscordLocale locale, Guild master, User enforcer, User target, String banReason, String reason) {
		return bot.getEmbedUtil().getEmbed().setColor(Constants.COLOR_WARNING)
			.setAuthor(lu.getLocalized(locale, path+"unban.title_synced").replace("{user_tag}", target.getAsTag()), null, target.getAvatarUrl())
			.addField(lu.getLocalized(locale, path+"unban.user"), target.getAsMention(), true)
			.addField(lu.getLocalized(locale, path+"unban.ban_reason"), (banReason!=null ? banReason : "-"), true)
			.addField(lu.getLocalized(locale, path+"unban.reason"), reason, true)
			.addField(lu.getLocalized(locale, path+"unban.master"), "`"+master.getName()+"` (#"+master.getId()+")", true)
			.addField(lu.getLocalized(locale, path+"unban.enforcer"), enforcer.getAsTag(), true)
			.setFooter("ID: "+target.getId())
			.setTimestamp(Instant.now())
			.build();
	}

	@Nonnull
	public MessageEmbed getUnbanEmbed(DiscordLocale locale, Ban banData, Member mod, String reason) {
		return getUnbanEmbed(locale, banData.getUser().getAsTag(), banData.getUser().getId(), mod.getAsMention(), banData.getReason(), reason);
	}

	@Nonnull
	private MessageEmbed getUnbanEmbed(DiscordLocale locale, String userTag, String userId, String modMention, String banReason, String reason) {
		return bot.getEmbedUtil().getEmbed().setColor(Constants.COLOR_WARNING)
			.setAuthor(lu.getLocalized(locale, path+"unban.title").replace("{user_tag}", userTag))
			.addField(lu.getLocalized(locale, path+"unban.user"), String.format("<@%s>", userId), true)
			.addField(lu.getLocalized(locale, path+"unban.mod"), modMention, true)
			.addField(lu.getLocalized(locale, path+"unban.ban_reason"), (banReason!=null ? banReason : "-"), true)
			.addField(lu.getLocalized(locale, path+"unban.reason"), reason, true)
			.setFooter("ID: "+userId)
			.setTimestamp(Instant.now())
			.build();
	}

	@Nonnull
	private EmbedBuilder groupLogEmbed(DiscordLocale locale, String masterId, String masterIcon, Integer groupId, String name) {
		return bot.getEmbedUtil().getEmbed().setColor(Constants.COLOR_WARNING)
			.setAuthor(lu.getLocalized(locale, path+"group.title").replace("{group_name}", name).replace("{group_id}", groupId.toString()), null, masterIcon)
			.setFooter(lu.getLocalized(locale, path+"group.master")+masterId)
			.setTimestamp(Instant.now());
	}

	@Nonnull
	public MessageEmbed getGroupCreationEmbed(DiscordLocale locale, String adminMention, String masterId, String masterIcon, Integer groupId, String name) {
		return groupLogEmbed(locale, masterId, masterIcon, groupId, name)
			.setColor(Constants.COLOR_SUCCESS)
			.setTitle(lu.getLocalized(locale, path+"group.created"))
			.addField(lu.getLocalized(locale, path+"group.admin"), adminMention, false)
			.build();
	}

	@Nonnull
	public MessageEmbed getGroupDeletedEmbed(DiscordLocale locale, String masterId, String masterIcon, Integer groupId, String name) {
		return groupLogEmbed(locale, masterId, masterIcon, groupId, name)
			.setColor(Constants.COLOR_FAILURE)
			.setTitle(lu.getLocalized(locale, path+"group.deleted"))
			.build();
	}

	@Nonnull
	public MessageEmbed getGroupDeletedMasterEmbed(DiscordLocale locale, String adminMention, String masterId, String masterIcon, Integer groupId, String name) {
		return groupLogEmbed(locale, masterId, masterIcon, groupId, name)
			.setColor(Constants.COLOR_FAILURE)
			.setTitle(lu.getLocalized(locale, path+"group.deleted"))
			.addField(lu.getLocalized(locale, path+"group.admin"), adminMention, false)
			.build();
	}

	@Nonnull
	public MessageEmbed getGroupJoinEmbed(DiscordLocale locale, String adminMention, String masterId, String masterIcon, Integer groupId, String name) {
		return groupLogEmbed(locale, masterId, masterIcon, groupId, name)
			.setColor(Constants.COLOR_SUCCESS)
			.setTitle(lu.getLocalized(locale, path+"group.join"))
			.addField(lu.getLocalized(locale, path+"group.admin"), adminMention, false)
			.build();
	}

	@Nonnull
	public MessageEmbed getGroupLeaveEmbed(DiscordLocale locale, String adminMention, String masterId, String masterIcon, Integer groupId, String name) {
		return groupLogEmbed(locale, masterId, masterIcon, groupId, name)
			.setColor(Constants.COLOR_FAILURE)
			.setTitle(lu.getLocalized(locale, path+"group.leave"))
			.addField(lu.getLocalized(locale, path+"group.admin"), adminMention, false)
			.build();
	}

	@Nonnull
	public MessageEmbed getGroupJoinMasterEmbed(DiscordLocale locale, String masterId, String masterIcon, String targetName, String targetId, Integer groupId, String name) {
		return groupLogEmbed(locale, masterId, masterIcon, groupId, name)
			.setColor(Constants.COLOR_SUCCESS)
			.setTitle(lu.getLocalized(locale, path+"group.joined"))
			.addField(lu.getLocalized(locale, path+"group.guild"), "*"+targetName+"* (`"+targetId+"`)", true)
			.build();
	}

	@Nonnull
	public MessageEmbed getGroupLeaveMasterEmbed(DiscordLocale locale, String masterId, String masterIcon, String targetName, String targetId, Integer groupId, String name) {
		return groupLogEmbed(locale, masterId, masterIcon, groupId, name)
			.setColor(Constants.COLOR_FAILURE)
			.setTitle(lu.getLocalized(locale, path+"group.left"))
			.addField(lu.getLocalized(locale, path+"group.guild"), "*"+targetName+"* (`"+targetId+"`)", true)
			.build();
	}

	@Nonnull
	public MessageEmbed getGroupRemoveEmbed(DiscordLocale locale, String adminMention, String masterId, String masterIcon, String targetName, String targetId, Integer groupId, String name) {
		return groupLogEmbed(locale, masterId, masterIcon, groupId, name)
			.setColor(Constants.COLOR_FAILURE)
			.setTitle(lu.getLocalized(locale, path+"group.removed"))
			.addField(lu.getLocalized(locale, path+"group.guild"), "*"+targetName+"* (`"+targetId+"`)", true)
			.addField(lu.getLocalized(locale, path+"group.admin"), adminMention, false)
			.build();
	}

	@Nonnull
	public MessageEmbed getGroupRenamedEmbed(DiscordLocale locale, String masterId, String masterIcon, Integer groupId, String oldName, String newName) {
		return groupLogEmbed(locale, masterId, masterIcon, groupId, newName)
			.setTitle(lu.getLocalized(locale, path+"group.renamed"))
			.addField(lu.getLocalized(locale, path+"group.oldname"), oldName, true)
			.build();
	}

	@Nonnull
	public MessageEmbed getGroupRenamedMasterEmbed(DiscordLocale locale, String adminMention, String masterId, String masterIcon, Integer groupId, String oldName, String newName) {
		return groupLogEmbed(locale, masterId, masterIcon, groupId, newName)
			.setTitle(lu.getLocalized(locale, path+"group.renamed"))
			.addField(lu.getLocalized(locale, path+"group.oldname"), oldName, true)
			.addField(lu.getLocalized(locale, path+"group.admin"), adminMention, false)
			.build();
	}

}