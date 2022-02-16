package bot.utils.message;

import java.time.ZonedDateTime;

import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.MessageEmbed;
import net.dv8tion.jda.api.entities.TextChannel;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.events.message.MessageReceivedEvent;
import bot.App;

public class EmbedUtil {

	private final App bot;

	public EmbedUtil(App bot) {
		this.bot = bot;
	}

	public EmbedBuilder getEmbed() {
		return new EmbedBuilder().setColor(0x8010112e).setTimestamp(ZonedDateTime.now());
	}

	public EmbedBuilder getEmbed(Member member) {
		return getEmbed().setFooter(
			bot.getMsg(member.getGuild().getId(), "embed.footer", member.getUser().getAsTag(), false),
			member.getUser().getEffectiveAvatarUrl()
		);
	}

	public EmbedBuilder getEmbed(User user) {
		return getEmbed().setFooter(
			bot.getMsg("0", "embed.footer", user.getAsTag(), false),
			user.getEffectiveAvatarUrl()
		);
	} 

	public EmbedBuilder getErrorEmbed(Member member) {
		return (member == null ? getEmbed() : getEmbed(member)).setColor(0xFF0000);
	}

	public EmbedBuilder getErrorEmbed(User user) {
		return (user == null ? getEmbed() : getEmbed(user)).setColor(0xFF0000);
	}

	public MessageEmbed getPermErrorEmbed(Member member, Guild guild, TextChannel channel, Permission perm, boolean self) {
		EmbedBuilder embed = getErrorEmbed(member);
		String msg;
		if (self) {
			if (channel == null) {
				msg = bot.getMsg(guild.getId(), "errors.missing_perms.self")
					.replace("{permissions}", perm.getName());
			} else {
				msg = bot.getMsg(guild.getId(), "errors.missing_perms.self_channel")
					.replace("{permissions}", perm.getName())
					.replace("{channel}", channel.getAsMention());
			}
		} else {
			if (channel == null) {
				msg = bot.getMsg(guild.getId(), "errors.missing_perms.other")
					.replace("{permissions}", perm.getName());
			} else {
				msg = bot.getMsg(guild.getId(), "errors.missing_perms.other_channel")
					.replace("{permissions}", perm.getName())
					.replace("{channel}", channel.getAsMention());
			}
		}

		return embed.setDescription(msg).build();
	}

	public void sendError(MessageReceivedEvent event, String path) {
		sendError(event, path, null);
	}

	public void sendError(MessageReceivedEvent event, String path, String reason) {
		
		String guildID = (event.isFromGuild() ? event.getGuild().getId() : "0");

		EmbedBuilder embed = getErrorEmbed(event.getAuthor());
		String msg;
		
		msg = (event.getMember() == null ? bot.getMsg(guildID, path) : bot.getMsg(guildID, path, event.getMember().getEffectiveName()));

		embed.setDescription(msg);

		if (reason != null)
			embed.addField(
				"Error:",
				reason,
				false
			);

		event.getChannel().sendMessageEmbeds(embed.build()).queue();
	}

	public void sendPermError(TextChannel tc, Member member, Permission perm, boolean self) {
		sendPermError(tc, member, null, perm, self);
	}

	public void sendPermError(TextChannel tc, Member member, TextChannel channel, Permission perm, boolean self) {
		if (perm.equals(Permission.MESSAGE_SEND)) {
			member.getUser().openPrivateChannel()
				.flatMap(ch -> ch.sendMessage(bot.getMsg(member.getGuild().getId(), "errors.no_send_perm")))
				.queue();
			return;
		}
		if (perm.equals(Permission.MESSAGE_EMBED_LINKS)) {
			if (channel == null) {
				tc.sendMessage(
					bot.getMsg(
						tc.getId(),
						"errors.missing_perms.self"
					)
					.replace("{permission}", perm.getName())
				).queue();
			} else {
				tc.sendMessage(
					bot.getMsg(
						tc.getId(),
						"errors.missing_perms.self_channel"
					)
					.replace("{permission}", perm.getName())
					.replace("{channel}", channel.getAsMention())
				).queue();
			}
			return;
		}

		tc.sendMessageEmbeds(getPermErrorEmbed(member, tc.getGuild(), channel, perm, self)).queue();
	}
	
}
