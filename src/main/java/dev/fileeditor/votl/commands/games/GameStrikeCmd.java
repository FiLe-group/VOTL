package dev.fileeditor.votl.commands.games;

import java.sql.SQLException;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

import dev.fileeditor.votl.base.command.SlashCommand;
import dev.fileeditor.votl.base.command.SlashCommandEvent;
import dev.fileeditor.votl.objects.CaseType;
import dev.fileeditor.votl.objects.CmdAccessLevel;
import dev.fileeditor.votl.objects.CmdModule;
import dev.fileeditor.votl.objects.constants.CmdCategory;
import dev.fileeditor.votl.objects.constants.Constants;
import dev.fileeditor.votl.objects.constants.Limits;
import dev.fileeditor.votl.utils.CaseProofUtil;
import dev.fileeditor.votl.utils.database.managers.CaseManager;
import dev.fileeditor.votl.utils.database.managers.GuildSettingsManager;
import dev.fileeditor.votl.utils.exception.AttachmentParseException;
import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.MessageEmbed;
import net.dv8tion.jda.api.entities.channel.ChannelType;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import net.dv8tion.jda.api.entities.channel.middleman.GuildChannel;
import net.dv8tion.jda.api.exceptions.ErrorHandler;
import net.dv8tion.jda.api.exceptions.InsufficientPermissionException;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import net.dv8tion.jda.api.requests.ErrorResponse;
import net.dv8tion.jda.api.utils.TimeFormat;

public class GameStrikeCmd extends SlashCommand {

	private final long denyPerms = Permission.getRaw(Permission.MESSAGE_SEND, Permission.MESSAGE_SEND_IN_THREADS, Permission.MESSAGE_ADD_REACTION, Permission.CREATE_PUBLIC_THREADS);

	public GameStrikeCmd() {
		this.name = "gamestrike";
		this.path = "bot.games.gamestrike";
		this.options = List.of(
			new OptionData(OptionType.CHANNEL, "channel", lu.getText(path+".channel.help"), true)
				.setChannelTypes(ChannelType.TEXT),
			new OptionData(OptionType.USER, "user", lu.getText(path+".user.help"), true),
			new OptionData(OptionType.STRING, "reason", lu.getText(path+".reason.help"), true)
				.setMaxLength(Limits.REASON_CHARS),
			new OptionData(OptionType.ATTACHMENT, "proof", lu.getText(path+".proof.help"))
		);
		this.category = CmdCategory.GAMES;
		this.module = CmdModule.GAMES;
		this.accessLevel = CmdAccessLevel.MOD;
		addMiddlewares(
			"throttle:user,1,10",
			"throttle:guild,2,20"
		);
	}

	@Override
	protected void execute(SlashCommandEvent event) {
		GuildChannel channel = event.optGuildChannel("channel");
		if (bot.getDBUtil().games.getMaxStrikes(channel.getIdLong()) == null) {
			editError(event, path+".not_found", "Channel: %s".formatted(channel.getAsMention()));
			return;
		}
		Member tm = event.optMember("user");
		if (tm == null || tm.getUser().isBot() || tm.equals(event.getMember())
			|| tm.equals(event.getGuild().getSelfMember())
			|| bot.getCheckUtil().hasHigherAccess(tm, event.getMember())) {
			editError(event, path+".not_member");
			return;
		}

		// Check strike cooldown
		long channelId = channel.getIdLong();
		Duration strikeCooldown = bot.getDBUtil().getGuildSettings(event.getGuild()).getStrikeCooldown();
		if (strikeCooldown.isPositive()) {
			Instant lastUpdate = bot.getDBUtil().games.getLastUpdate(channelId, tm.getIdLong());
			if (lastUpdate != null && lastUpdate.plus(strikeCooldown).isAfter(Instant.now())) {
				// Cooldown between strikes
				editEmbed(event, bot.getEmbedUtil().getEmbed(Constants.COLOR_FAILURE)
					.setDescription(lu.getGuildText(event, path+".cooldown", TimeFormat.RELATIVE.after(strikeCooldown)))
					.build()
				);
				return;
			}
		}

		// Get proof
		final CaseProofUtil.ProofData proofData;
		try {
			proofData = CaseProofUtil.getData(event);
		} catch (AttachmentParseException e) {
			editError(event, e.getPath(), e.getMessage());
			return;
		}

		String reason = bot.getModerationUtil().parseReasonMentions(event, this);
		// Add to DB
		long guildId = event.getGuild().getIdLong();

		final CaseManager.CaseData strikeData;
		try {
			bot.getDBUtil().games.addStrike(guildId, channelId, tm.getIdLong());
			strikeData = bot.getDBUtil().cases.add(
				CaseType.GAME_STRIKE, tm.getIdLong(), tm.getUser().getName(),
				event.getUser().getIdLong(), event.getUser().getName(),
				guildId, reason, null
			);
		} catch (SQLException e) {
			editErrorDatabase(event, e, "game add strike");
			return;
		}

		// Log
		final int strikeCount = bot.getDBUtil().games.countStrikes(channelId, tm.getIdLong());
		final int maxStrikes = bot.getDBUtil().games.getMaxStrikes(channelId);
		bot.getGuildLogger().mod.onNewCase(event.getGuild(), tm.getUser(), strikeData, proofData, strikeCount+"/"+maxStrikes);

		// Check if reached limit
		if (strikeCount >= maxStrikes) {
			try {
				channel.getPermissionContainer().upsertPermissionOverride(tm).setDenied(denyPerms).reason("Game ban").queue();
			} catch (InsufficientPermissionException ignored) {}
		}

		// Inform user and send to drama
		final GuildSettingsManager.DramaLevel dramaLevel = bot.getDBUtil().getGuildSettings(event.getGuild()).getDramaLevel();
		tm.getUser().openPrivateChannel().queue(pm -> {
			final String text = bot.getModerationUtil().getGamestrikeDmText(CaseType.GAME_STRIKE, event.getGuild(), reason, event.getUser(), channel, strikeCount, maxStrikes);
			if (text == null) return;

			pm.sendMessage(text).setSuppressEmbeds(true)
				.queue(null, new ErrorHandler().handle(ErrorResponse.CANNOT_SEND_TO_USER, (failure) -> {
					if (dramaLevel.equals(GuildSettingsManager.DramaLevel.ONLY_BAD_DM)) {
						TextChannel dramaChannel = Optional.ofNullable(bot.getDBUtil().getGuildSettings(event.getGuild()).getDramaChannelId())
							.map(event.getJDA()::getTextChannelById)
							.orElse(null);
						if (dramaChannel != null) {
							final MessageEmbed dramaEmbed = bot.getModerationUtil().getDramaEmbed(CaseType.GAME_STRIKE, event.getGuild(), tm, reason, null, channel);
							if (dramaEmbed == null) return;
							dramaChannel.sendMessage("||%s||".formatted(tm.getAsMention()))
								.addEmbeds(dramaEmbed)
								.queue();
						}
					}
				}));
		});
		if (dramaLevel.equals(GuildSettingsManager.DramaLevel.ALL)) {
			TextChannel dramaChannel = Optional.ofNullable(bot.getDBUtil().getGuildSettings(event.getGuild()).getDramaChannelId())
				.map(event.getJDA()::getTextChannelById)
				.orElse(null);
			if (dramaChannel != null) {
				final MessageEmbed dramaEmbed = bot.getModerationUtil().getDramaEmbed(CaseType.GAME_STRIKE, event.getGuild(), tm, reason, null, channel);
				if (dramaEmbed != null) {
					dramaChannel.sendMessageEmbeds(dramaEmbed).queue();
				}
			}
		}

		// Reply
		editEmbed(event, bot.getEmbedUtil().getEmbed(Constants.COLOR_SUCCESS)
			.setDescription(lu.getGuildText(event, path+".done", tm.getAsMention(), channel.getAsMention()))
			.setFooter("#"+strikeData.getLocalId())
			.build());
	}
}