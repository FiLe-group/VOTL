package bot.commands.voice;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import javax.annotation.Nonnull;

import com.jagrosh.jdautilities.command.SlashCommand;
import com.jagrosh.jdautilities.command.SlashCommandEvent;
import com.jagrosh.jdautilities.doc.standard.CommandInfo;

import bot.App;
import bot.objects.constants.CmdCategory;
import bot.utils.exception.CheckException;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.PermissionOverride;
import net.dv8tion.jda.api.entities.channel.concrete.VoiceChannel;
import net.dv8tion.jda.api.exceptions.InsufficientPermissionException;
import net.dv8tion.jda.api.interactions.InteractionHook;

@CommandInfo(
	name = "perms",
	description = "View/Reset voice channel permissions",
	usage = "/perms <select>",
	requirements = "Must have created voice channel"
)
public class PermsCmd extends SlashCommand {
	
	private static App bot;
	private static final String MODULE = "voice";

	public PermsCmd(App bot) {
		this.name = "perms";
		this.help = bot.getMsg("bot.voice.perms.help");
		this.category = CmdCategory.VOICE;
		PermsCmd.bot = bot;
		this.children = new SlashCommand[]{new View(), new Reset()};
	}

	@Override
	protected void execute(SlashCommandEvent event) {

	}

	private static class View extends SlashCommand {

		protected Permission[] botPerms;

		public View() {
			this.name = "view";
			this.help = bot.getMsg("bot.voice.perms.view.help");
			this.botPerms = new Permission[]{Permission.MANAGE_PERMISSIONS};
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

			Member member = Objects.requireNonNull(event.getMember());
			Guild guild = Objects.requireNonNull(event.getGuild());
			String guildId = guild.getId();
	
			try {
				bot.getCheckUtil().moduleEnabled(event, guildId, MODULE)
					.hasPermissions(event.getTextChannel(), member, true, botPerms)
					.guildExists(event, guildId);
			} catch (CheckException ex) {
				hook.editOriginal(ex.getEditData()).queue();
				return;
			}

			if (bot.getDBUtil().isVoiceChannel(member.getId())) {
				VoiceChannel vc = guild.getVoiceChannelById(bot.getDBUtil().channelGetChannel(member.getId()));

				EmbedBuilder embed = bot.getEmbedUtil().getEmbed(member)
					.setTitle(bot.getMsg(guildId, "bot.voice.perms.view.embed.title").replace("{channel}", vc.getName()))
					.setDescription(bot.getMsg(guildId, "bot.voice.perms.view.embed.field")+"\n\n");

				//@Everyone
				PermissionOverride publicOverride = vc.getPermissionOverride(guild.getPublicRole());

				String view = contains(publicOverride, Permission.VIEW_CHANNEL);
				String join = contains(publicOverride, Permission.VOICE_CONNECT);
				
				embed = embed.appendDescription(formatHolder(bot.getMsg(guildId, "bot.voice.perms.view.embed.everyone"), view, join))
					.appendDescription("\n\n" + bot.getMsg(guildId, "bot.voice.perms.view.embed.roles") + "\n");

				//Roles
				List<PermissionOverride> overrides = new ArrayList<>(vc.getRolePermissionOverrides()); // cause given override list is immutable
				try {
					overrides.remove(vc.getPermissionOverride(Objects.requireNonNull(guild.getBotRole()))); // removes bot's role
					overrides.remove(vc.getPermissionOverride(guild.getPublicRole())); // removes @everyone role
				} catch (NullPointerException ex) {
					bot.getLogger().warn("PermsCmd null pointer at role override remove");
				}
				
				if (overrides.isEmpty()) {
					embed = embed.appendDescription(bot.getMsg(guildId, "bot.voice.perms.view.embed.none") + "\n");
				} else {
					for (PermissionOverride ov : overrides) {
						view = contains(ov, Permission.VIEW_CHANNEL);
						join = contains(ov, Permission.VOICE_CONNECT);

						embed = embed.appendDescription(formatHolder(ov.getRole().getName(), view, join) + "\n");
					}
				}
				embed = embed.appendDescription("\n" + bot.getMsg(guildId, "bot.voice.perms.view.embed.members") + "\n");

				//Members
				overrides = new ArrayList<>(vc.getMemberPermissionOverrides());
				try {
					overrides.remove(vc.getPermissionOverride(member)); // removes user
					overrides.remove(vc.getPermissionOverride(guild.getSelfMember())); // removes bot
				} catch (NullPointerException ex) {
					bot.getLogger().warn("PermsCmd null pointer at member override remove");
				}

				if (overrides.isEmpty()) {
					embed = embed.appendDescription(bot.getMsg(guildId, "bot.voice.perms.view.embed.none") + "\n");
				} else {
					for (PermissionOverride ov : overrides) {
						view = contains(ov, Permission.VIEW_CHANNEL);
						join = contains(ov, Permission.VOICE_CONNECT);

						embed = embed.appendDescription(formatHolder(ov.getMember().getEffectiveName(), view, join) + "\n");
					}
				}
				

				hook.editOriginalEmbeds(embed.build()).queue();
			} else {
				hook.editOriginal(bot.getEmbedUtil().getError(event, "errors.no_channel")).queue();
				return;
			}
		}

		private String contains(PermissionOverride override, Permission perm) {
			if (override != null) {
				if (override.getAllowed().contains(perm))
					return "✅";
				else if (override.getDenied().contains(perm))
					return "❌";
			}
			return "▪️";
		}

		@Nonnull
		private String formatHolder(String holder, String view, String join) {
			return "`" + holder + "` | " + view + " | " + join;
		}
	}

	private static class Reset extends SlashCommand {

		protected Permission[] botPerms;

		public Reset() {
			this.name = "reset";
			this.help = bot.getMsg("bot.voice.perms.reset.help");
			this.botPerms = new Permission[]{Permission.MANAGE_ROLES, Permission.MANAGE_PERMISSIONS, Permission.VIEW_CHANNEL, Permission.VOICE_CONNECT};
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

			Member member = Objects.requireNonNull(event.getMember());
			Guild guild = Objects.requireNonNull(event.getGuild());
			String guildId = guild.getId();
	
			try {
				bot.getCheckUtil().moduleEnabled(event, guildId, MODULE)
					.hasPermissions(event.getTextChannel(), member, true, botPerms)
					.guildExists(event, guildId);
			} catch (CheckException ex) {
				hook.editOriginal(ex.getEditData()).queue();
				return;
			}

			if (bot.getDBUtil().isVoiceChannel(member.getId())) {
				VoiceChannel vc = guild.getVoiceChannelById(bot.getDBUtil().channelGetChannel(member.getId()));
				try {
					vc.getManager().sync().queue();
				} catch (InsufficientPermissionException ex) {
					hook.editOriginal(bot.getEmbedUtil().getPermError(event.getTextChannel(), member, ex.getPermission(), true)).queue();
					return;
				}
	
				hook.editOriginalEmbeds(
					bot.getEmbedUtil().getEmbed(member)
						.setDescription(bot.getMsg(guildId, "bot.voice.perms.reset.done"))
						.build()
				).queue();
			} else {
				hook.editOriginal(bot.getEmbedUtil().getError(event, "errors.no_channel")).queue();
				return;
			}
		}
	}
}
