package bot.utils.message;

import java.time.ZonedDateTime;
import java.util.Optional;

import javax.annotation.Nonnull;

import com.jagrosh.jdautilities.command.SlashCommandEvent;

import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.utils.messages.MessageCreateBuilder;
import net.dv8tion.jda.api.utils.messages.MessageCreateData;
import bot.App;

public class EmbedUtil {

	private final App bot;

	public EmbedUtil(App bot) {
		this.bot = bot;
	}

	@Nonnull
	public EmbedBuilder getEmbed() {
		return new EmbedBuilder().setColor(0x8010112e).setTimestamp(ZonedDateTime.now());
	}

	@Nonnull
	public EmbedBuilder getEmbed(Member member) {
		return getEmbed().setFooter(
			bot.getMsg(member.getGuild().getId(), "embed.footer", member.getUser().getAsTag(), false),
			member.getUser().getEffectiveAvatarUrl()
		);
	}

	@Nonnull
	public EmbedBuilder getEmbed(User user) {
		return getEmbed().setFooter(
			bot.getMsg("0", "embed.footer", user.getAsTag(), false),
			user.getEffectiveAvatarUrl()
		);
	} 

	@Nonnull
	private EmbedBuilder getErrorEmbed(Member member) {
		return (member == null ? getEmbed() : getEmbed(member)).setColor(0xFF0000);
	}

	@Nonnull
	private EmbedBuilder getErrorEmbed(User user) {
		return (user == null ? getEmbed() : getEmbed(user)).setColor(0xFF0000);
	}

	@Nonnull
	public EmbedBuilder getPermErrorEmbed(Member member, Guild guild, TextChannel channel, Permission perm, boolean self) {
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

		return embed.setDescription(msg);
	}

	@Nonnull
	public MessageCreateData getError(SlashCommandEvent event, String path) {
		return getError(event, path, null);
	}

	@Nonnull
	public MessageCreateData getError(SlashCommandEvent event, String path, String reason) {
		
		String guildID = Optional.ofNullable(event.getGuild()).map(g -> g.getId()).orElse("0");

		EmbedBuilder embed = event.isFromGuild() ? getErrorEmbed(event.getMember()) : getErrorEmbed(event.getUser());
		embed.setDescription(
			Optional.ofNullable(event.getMember()).map(m -> bot.getMsg(guildID, path, m.getEffectiveName())).orElse(bot.getMsg(guildID, path))
		);

		if (reason != null)
			embed.addField(
				"Error:",
				reason,
				false
			);
		
		MessageCreateBuilder mb = new MessageCreateBuilder();
		mb.setEmbeds(embed.build());

		return mb.build();
	}

	@Nonnull
	public MessageCreateData getPermError(TextChannel tc, Member member, Permission perm, boolean self) {
		return getPermError(tc, member, null, perm, self);
	}

	@Nonnull
	public MessageCreateData getPermError(TextChannel tc, Member member, TextChannel channel, Permission perm, boolean self) {
		if (perm.equals(Permission.MESSAGE_SEND)) {
			member.getUser().openPrivateChannel()
				.flatMap(ch -> ch.sendMessage(bot.getMsg(member.getGuild().getId(), "errors.no_send_perm")))
				.queue();
			return MessageCreateData.fromContent("PM sended");
		}
		MessageCreateBuilder mb = new MessageCreateBuilder();

		if (perm.equals(Permission.MESSAGE_EMBED_LINKS)) {
			if (channel == null) {
				mb.setContent(
					bot.getMsg(tc.getId(),"errors.missing_perms.self")
						.replace("{permission}", perm.getName())
				);
			} else {
				mb.setContent(
					bot.getMsg(tc.getId(),"errors.missing_perms.self_channel")
						.replace("{permission}", perm.getName())
						.replace("{channel}", channel.getAsMention())
				);
			}
		} else {
			mb.setEmbeds(getPermErrorEmbed(member, tc.getGuild(), channel, perm, self).build());
		}
		return mb.build();
	}
	
}
