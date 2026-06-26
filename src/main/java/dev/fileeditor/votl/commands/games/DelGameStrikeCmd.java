package dev.fileeditor.votl.commands.games;

import java.sql.SQLException;
import java.util.List;

import dev.fileeditor.votl.base.command.SlashCommand;
import dev.fileeditor.votl.base.command.SlashCommandEvent;
import dev.fileeditor.votl.objects.AccessPermission;
import dev.fileeditor.votl.objects.CmdModule;
import dev.fileeditor.votl.objects.constants.CmdCategory;
import dev.fileeditor.votl.objects.constants.Constants;
import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.entities.channel.ChannelType;
import net.dv8tion.jda.api.entities.channel.middleman.GuildChannel;
import net.dv8tion.jda.api.exceptions.InsufficientPermissionException;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;

public class DelGameStrikeCmd extends SlashCommand {

	private final long denyPerms = Permission.getRaw(Permission.MESSAGE_SEND, Permission.MESSAGE_SEND_IN_THREADS, Permission.MESSAGE_ADD_REACTION, Permission.CREATE_PUBLIC_THREADS);

	public DelGameStrikeCmd() {
		this.name = "delgamestrike";
		this.path = "bot.games.delgamestrike";
		this.options = List.of(
			new OptionData(OptionType.CHANNEL, "channel", lu.getText(path+".channel.help"), true)
				.setChannelTypes(ChannelType.TEXT),
			new OptionData(OptionType.USER, "user", lu.getText(path+".user.help"), true)
		);
		this.category = CmdCategory.GAMES;
		this.module = CmdModule.GAMES;
		this.requiredPermission = AccessPermission.CMD_DEL_GAME_STRIKE;
		addMiddlewares(
			"throttle:guild,2,20"
		);
	}

	@Override
	protected void execute(SlashCommandEvent event) {
		assert event.getGuild() != null;
		GuildChannel channel = event.optGuildChannel("channel");
		assert channel != null;
		Integer maxStrikes = bot.getDBUtil().games.getMaxStrikes(channel.getIdLong());
		if (maxStrikes == null) {
			editError(event, "errors.option.channel", "Channel: %s".formatted(channel.getAsMention()));
			return;
		}
		User tu = event.optUser("user");
		if (tu == null || tu.isBot()) {
			editError(event, "errors.option.user");
			return;
		}

		long channelId = channel.getIdLong();
		Integer strikeCount = bot.getDBUtil().games.countStrikes(channelId, tu.getIdLong());
		if (strikeCount == null || strikeCount <= 0) {
			editError(event, path+".no_strikes");
			return;
		}

		try {
			bot.getDBUtil().games.removeStrike(channelId, tu.getIdLong());
		} catch (SQLException e) {
			editErrorDatabase(event, e, "game remove strike");
			return;
		}

		// If user was at/above ban threshold, lift the channel permission override
		if (strikeCount >= maxStrikes) {
			Member targetMember = event.getGuild().getMemberById(tu.getIdLong());
			if (targetMember != null) {
				try {
					channel.getPermissionContainer().upsertPermissionOverride(targetMember)
						.clear(denyPerms).reason("Game strike removed").queue();
				} catch (InsufficientPermissionException ignored) {}
			}
		}

		int newCount = strikeCount - 1;
		editEmbed(event, bot.getEmbedUtil().getEmbed(Constants.COLOR_SUCCESS)
			.setDescription(lu.getGuildText(event, path+".done", tu.getAsMention(), channel.getAsMention(), newCount, maxStrikes))
			.build());
	}

}
