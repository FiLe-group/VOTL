package bot.utils.message;

import java.time.ZonedDateTime;

import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.MessageEmbed;
import net.dv8tion.jda.api.entities.TextChannel;
import bot.App;

public class EmbedUtil {

    private final App bot;

    public EmbedUtil(App bot) {
        this.bot = bot;
    }

    public EmbedBuilder getEmbed() {
        return new EmbedBuilder().setColor(0x8025272b).setTimestamp(ZonedDateTime.now());
    }

    public EmbedBuilder getEmbed(Member member) {
        return getEmbed().setFooter(
            bot.getMsg(member.getGuild().getId(), "embed.footer", member.getUser().getAsTag(), false),
            member.getUser().getEffectiveAvatarUrl()
        );
    }

    public EmbedBuilder getErrorEmbed(Member member) {
        return (member == null ? getEmbed() : getEmbed(member)).setColor(0xFF0000);
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

    public void sendError(TextChannel tc, Member member, String path) {
        sendError(tc, member, path, null);
    }

    public void sendError(TextChannel tc, Member member, String path, String reason) {
        
        Guild guild = tc.getGuild();

        EmbedBuilder embed = getErrorEmbed(member);
        String msg;
        
        msg = member == null ? bot.getMsg(guild.getId(), path) : bot.getMsg(guild.getId(), path, member.getEffectiveName());

        embed.setDescription(msg);

        if (reason != null)
            embed.addField(
                "Error:",
                reason,
                false
            );

        tc.sendMessageEmbeds(embed.build()).queue();
    }

    public void sendPermError(TextChannel tc, Member member, Permission perm, boolean self) {

    }

    public void sendPermError(TextChannel tc, Member member, TextChannel channel, Permission perm, boolean self) {
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
