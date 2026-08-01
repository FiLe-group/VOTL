package dev.fileeditor.votl.utils;

import java.util.Set;

import dev.fileeditor.votl.App;
import dev.fileeditor.votl.objects.AccessPermission;
import dev.fileeditor.votl.objects.constants.Constants;
import dev.fileeditor.votl.utils.file.lang.LocaleUtil;

import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.components.actionrow.ActionRow;
import net.dv8tion.jda.api.components.buttons.Button;
import net.dv8tion.jda.api.components.container.Container;
import net.dv8tion.jda.api.components.separator.Separator;
import net.dv8tion.jda.api.components.textdisplay.TextDisplay;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.GuildVoiceState;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.PermissionOverride;
import net.dv8tion.jda.api.entities.Role;
import net.dv8tion.jda.api.entities.channel.concrete.Category;
import net.dv8tion.jda.api.entities.channel.concrete.VoiceChannel;
import net.dv8tion.jda.api.entities.channel.middleman.AudioChannel;
import net.dv8tion.jda.api.entities.emoji.Emoji;
import net.dv8tion.jda.api.interactions.DiscordLocale;
import net.dv8tion.jda.api.managers.channel.concrete.VoiceChannelManager;
import net.dv8tion.jda.api.requests.restaction.PermissionOverrideAction;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Shared helpers for custom voice channels.
 */
public class VoiceUtil {

	/** Discord requires channel names to be between 2 and 100 characters. */
	private static final int NAME_MIN_LENGTH = 2;
	private static final int NAME_MAX_LENGTH = 100;

	/**
	 * Granted on every custom channel to each role holding {@link AccessPermission#VOICE_BYPASS},
	 * so moderators keep access to channels their owner locked or hid. Role allow-overrides win
	 * over the deny that locking/ghosting writes onto the verification or {@code @everyone} role.
	 */
	public static final Set<Permission> bypassPerms = Set.of(Permission.VIEW_CHANNEL, Permission.VOICE_CONNECT);

	private static final String PANEL_PATH = "bot.guild.setup.voice.panel";

	/**
	 * Whether the channel holds no actual users. Bots are ignored, so a music or recording bot
	 * left behind in a custom channel does not keep it alive forever.
	 */
	public static boolean hasNoUsers(@NotNull AudioChannel channel) {
		return channel.getMembers().stream().allMatch(member -> member.getUser().isBot());
	}

	/**
	 * Clamps a channel name to the length Discord accepts: truncated to {@value #NAME_MAX_LENGTH}
	 * characters, and padded when a name template resolves to something too short to be valid.
	 */
	@NotNull
	public static String formatChannelName(@NotNull String name) {
		String formatted = name.length() > NAME_MAX_LENGTH ? name.substring(0, NAME_MAX_LENGTH) : name;
		if (formatted.length() < NAME_MIN_LENGTH) {
			formatted += "-".repeat(NAME_MIN_LENGTH - formatted.length());
		}
		return formatted;
	}

	/**
	 * Restores {@code permission} for {@code role} to whatever the channel originally inherited
	 * from its category, rather than granting it outright.
	 * <p>
	 * Granting is not the inverse of denying: if the category denies the permission to this role,
	 * then locking and unlocking a channel with {@code grant} would leave it more permissive than
	 * it started, silently widening access.
	 */
	public static void resetInheritedPermission(@NotNull VoiceChannel vc, @NotNull Role role, @NotNull Permission permission) {
		Category category = vc.getParentCategory();
		PermissionOverride inherited = category == null ? null : category.getPermissionOverride(role);
		PermissionOverrideAction action = vc.upsertPermissionOverride(role);

		if (inherited == null) {
			action.clear(permission).queue();
		} else if (inherited.getAllowed().contains(permission)) {
			action.grant(permission).queue();
		} else if (inherited.getDenied().contains(permission)) {
			action.deny(permission).queue();
		} else {
			action.clear(permission).queue();
		}
	}

	/**
	 * Builds the custom voice channel management panel as a Components V2 container. Shared by
	 * {@code /setup voice panel} and by the copy posted into each new channel's built-in text chat,
	 * so both stay in step.
	 *
	 * @param masterChannelId the generator channel members join to create their own
	 */
	@NotNull
	public static Container buildPanel(@NotNull LocaleUtil lu, @NotNull DiscordLocale locale, @Nullable Long masterChannelId) {
		Button lock = Button.danger("voice:lock", lu.getLocalized(locale, PANEL_PATH+".lock")).withEmoji(Emoji.fromUnicode("🔒"));
		Button unlock = Button.success("voice:unlock", lu.getLocalized(locale, PANEL_PATH+".unlock")).withEmoji(Emoji.fromUnicode("🔓"));
		Button ghost = Button.danger("voice:ghost", lu.getLocalized(locale, PANEL_PATH+".ghost")).withEmoji(Emoji.fromUnicode("👻"));
		Button unghost = Button.success("voice:unghost", lu.getLocalized(locale, PANEL_PATH+".unghost")).withEmoji(Emoji.fromUnicode("👁️"));
		Button permit = Button.success("voice:permit", lu.getLocalized(locale, PANEL_PATH+".permit")).withEmoji(Emoji.fromUnicode("➕"));
		Button reject = Button.danger("voice:reject", lu.getLocalized(locale, PANEL_PATH+".reject")).withEmoji(Emoji.fromUnicode("➖"));
		Button perms = Button.secondary("voice:perms", lu.getLocalized(locale, PANEL_PATH+".perms")).withEmoji(Emoji.fromUnicode("⚙️"));
		Button edit = Button.secondary("voice:edit", lu.getLocalized(locale, PANEL_PATH+".edit")).withEmoji(Emoji.fromUnicode("✏️"));
		Button delete = Button.danger("voice:delete", lu.getLocalized(locale, PANEL_PATH+".delete")).withEmoji(Emoji.fromUnicode("🗑️"));

		return Container.of(
				TextDisplay.of("## "+lu.getLocalized(locale, PANEL_PATH+".panel_title")),
				TextDisplay.of(lu.getLocalized(locale, PANEL_PATH+".panel_text").formatted(masterChannelId)),
				Separator.createDivider(Separator.Spacing.LARGE),
				TextDisplay.of(lu.getLocalized(locale, PANEL_PATH+".section_access")),
				ActionRow.of(unlock, lock),
				Separator.createDivider(Separator.Spacing.SMALL),
				TextDisplay.of(lu.getLocalized(locale, PANEL_PATH+".section_visibility")),
				ActionRow.of(unghost, ghost),
				Separator.createDivider(Separator.Spacing.SMALL),
				TextDisplay.of(lu.getLocalized(locale, PANEL_PATH+".section_members")),
				ActionRow.of(permit, reject, perms),
				Separator.createDivider(Separator.Spacing.LARGE),
				ActionRow.of(edit, delete)
			)
			.withAccentColor(Constants.COLOR_DEFAULT);
	}

	/**
	 * Resolves which custom voice channel {@code member} is allowed to delete.
	 * <p>
	 * The channel they are currently sitting in takes priority over the one they own, so a
	 * moderator who walks into someone else's channel deletes that one rather than their own
	 * elsewhere. Deleting a channel they do not own requires {@link AccessPermission#VOICE_BYPASS}.
	 */
	@NotNull
	public static DeleteTarget resolveDeleteTarget(@NotNull App bot, @NotNull Member member) {
		var db = bot.getDBUtil();
		GuildVoiceState state = member.getVoiceState();

		if (state != null && state.getChannel() instanceof VoiceChannel current && db.voice.existsChannel(current.getIdLong())) {
			Long ownerId = db.voice.getUser(current.getIdLong());
			if (ownerId != null && ownerId == member.getIdLong())
				return new DeleteTarget(current, null);
			if (bot.getCheckUtil().hasAccess(member, AccessPermission.VOICE_BYPASS))
				return new DeleteTarget(current, null);
			return new DeleteTarget(null, "bot.voice.listener.not_owner");
		}

		Long ownChannelId = db.voice.getChannel(member.getIdLong());
		if (ownChannelId != null) {
			VoiceChannel own = member.getGuild().getVoiceChannelById(ownChannelId);
			if (own != null) return new DeleteTarget(own, null);
		}
		return new DeleteTarget(null, "errors.no_channel");
	}

	/** Either a deletable {@code channel} or the locale {@code errorPath} explaining why there isn't one. */
	public record DeleteTarget(@Nullable VoiceChannel channel, @Nullable String errorPath) {}

	/** Whether the member may bypass the restrictions custom channel owners set. */
	public static boolean hasBypass(@NotNull App bot, @NotNull Member member) {
		return bot.getCheckUtil().hasAccess(member, AccessPermission.VOICE_BYPASS);
	}

	/** Whether the role itself carries {@link AccessPermission#VOICE_BYPASS}. */
	@SuppressWarnings("BooleanMethodIsAlwaysInverted")
	public static boolean isBypassRole(@NotNull App bot, @NotNull Role role) {
		return bot.getDBUtil().accessGroups
			.getRolesWithPermission(role.getGuild().getIdLong(), AccessPermission.VOICE_BYPASS)
			.contains(role.getIdLong());
	}

	/**
	 * Re-applies the bypass overrides onto an existing channel, for the paths that rewrite its
	 * permissions wholesale — otherwise syncing with the category would silently drop them.
	 */
	@NotNull
	public static VoiceChannelManager applyBypassOverrides(@NotNull App bot, @NotNull Guild guild, @NotNull VoiceChannelManager manager) {
		for (long roleId : bot.getDBUtil().accessGroups.getRolesWithPermission(guild.getIdLong(), AccessPermission.VOICE_BYPASS)) {
			Role role = guild.getRoleById(roleId);
			if (role == null) continue;
			manager = manager.putPermissionOverride(role, bypassPerms, null);
		}
		return manager;
	}

}