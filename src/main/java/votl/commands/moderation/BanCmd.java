package votl.commands.moderation;

import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.TimeUnit;

import votl.App;
import votl.commands.CommandBase;
import votl.objects.CmdAccessLevel;
import votl.objects.CmdModule;
import votl.objects.command.CooldownScope;
import votl.objects.command.SlashCommandEvent;
import votl.objects.constants.CmdCategory;
import votl.objects.constants.Constants;
import votl.utils.exception.FormatterException;

import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.interactions.DiscordLocale;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;

import com.jagrosh.jdautilities.doc.standard.CommandInfo;

@CommandInfo
(
	name = "ban",
	description = "ban guild member on server",
	usage = "/ban <@member>[time:][reason:][delete?]"
)
public class BanCmd extends CommandBase {
	
	public BanCmd(App bot) {
		super(bot);
		this.name = "ban";
		this.path = "bot.moderation.ban";
		List<OptionData> options = new ArrayList<>();
		options.add(new OptionData(OptionType.USER, "user", lu.getText(path+".option_user"), true));
		options.add(new OptionData(OptionType.STRING, "time", lu.getText(path+".option_time")));
		options.add(new OptionData(OptionType.STRING, "reason", lu.getText(path+".option_reason")).setMaxLength(400));
		options.add(new OptionData(OptionType.BOOLEAN, "delete", lu.getText(path+".option_delete")));
		this.options = options;
		this.botPermissions = new Permission[]{Permission.BAN_MEMBERS};
		this.category = CmdCategory.MODERATION;
		this.module = CmdModule.MODERATION;
		this.accessLevel = CmdAccessLevel.MOD;
		this.mustSetup = true;
		this.cooldown = 10;
		this.cooldownScope = CooldownScope.GUILD;
	}

	@Override
	protected void execute(SlashCommandEvent event) {
		event.deferReply(true).queue();
		
		User targetUser = event.optUser("user");
		String time = event.optString("time");
		String reason = event.optString("reason", lu.getText(event, path+".no_reason"));
		Boolean delete = event.optBoolean("delete", true);

		sendReply(event, targetUser, time, reason, delete);
	}

	private void sendReply(SlashCommandEvent event, User tu, String time, String sReason, Boolean delete) {
		DiscordLocale userLocale = event.getUserLocale();
		Guild guild = Objects.requireNonNull(event.getGuild());

		if (tu == null) {
			editError(event, path+".not_found");
			return;
		}
		if (event.getUser().equals(tu) || event.getJDA().getSelfUser().equals(tu)) {
			editError(event, path+".not_self");
			return;
		}

		guild.retrieveBan(tu).queue(
			ban -> {
				editHookEmbed(event, bot.getEmbedUtil().getEmbed(event)
					.setColor(Constants.COLOR_WARNING)
					.setDescription(lu.getLocalized(userLocale, path+".already_banned"))
					.addField(lu.getLocalized(userLocale, "bot.moderation.embeds.ban.short_title"), lu.getLocalized(userLocale, "bot.moderation.embeds.ban.short_info")
						.replace("{username}", ban.getUser().getAsTag())
						.replace("{reason}", ban.getReason())
						, false)
					.build()
				);
			},
			failure -> {
				if (failure.getMessage().startsWith("10026")) {
					try {
						Member tm = event.optMember("user");
						Member mod = event.getMember();
						if (Objects.nonNull(tm) && bot.getCheckUtil().hasHigherAccess(event.getClient(), tm, mod)) {
							editError(event, path+".higher_access");
						} else {
							Duration duration = bot.getFormatUtil().getDuration(time, false);

							if (Objects.isNull(tm) || guild.getSelfMember().canInteract(tm)) {
								// perform ban
								guild.ban(tu, (delete ? 2 : 0), TimeUnit.DAYS).reason(sReason).queue();
								String reason = sReason == null ? lu.getText(event, path+".no_reason") : sReason;
								// get new caseId/banId
								Integer banId = 1 + bot.getDBUtil().ban.lastId();
								// add info to db
								bot.getDBUtil().ban.add(banId, tu.getId(), tu.getAsTag(), mod.getId(), mod.getUser().getAsTag(),
									guild.getId(), reason, Timestamp.from(Instant.now()), duration, false);
								// log ban
								bot.getLogListener().onBan(event, tu, mod, banId);
								// sync, maybe?
								
								// send promt asking user for ban sync (if available)
								editHookEmbed(event, bot.getEmbedUtil().getEmbed(event)
									.setColor(Constants.COLOR_SUCCESS)
									.setDescription(lu.getLocalized(userLocale, path+".ban_success")
										.replace("{user_tag}", tu.getAsTag())
										.replace("{duration}", duration.isZero() ? lu.getLocalized(userLocale, "bot.moderation.embeds.permanently") : 
											lu.getLocalized(userLocale, "bot.moderation.embeds.temporary")
												.replace("{time}", bot.getMessageUtil().formatTime(Instant.now().plus(duration), true))
										)
										.replace("{reason}", reason))
									.build()
								);
							} else {
								editError(event, path+".ban_abort");
							}
						}
					} catch (FormatterException ex) {
						editError(event, ex.getPath());
					}
				} else {
					bot.getLogger().warn(failure.getMessage());
					editError(event, "errors.unknown", failure.getMessage());
				}
			});
		
	}
}
