package votl.listeners;

import java.util.concurrent.TimeUnit;

import javax.annotation.Nonnull;

import votl.App;
import votl.objects.constants.Constants;
import votl.utils.database.DBUtil;

import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.Role;
import net.dv8tion.jda.api.entities.channel.concrete.VoiceChannel;
import net.dv8tion.jda.api.entities.emoji.Emoji;
import net.dv8tion.jda.api.events.interaction.ModalInteractionEvent;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import net.dv8tion.jda.api.exceptions.InsufficientPermissionException;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import net.dv8tion.jda.api.interactions.components.buttons.Button;
import net.dv8tion.jda.api.interactions.components.buttons.ButtonStyle;
import net.dv8tion.jda.api.interactions.components.text.TextInput;
import net.dv8tion.jda.api.interactions.components.text.TextInputStyle;
import net.dv8tion.jda.api.interactions.modals.Modal;

public class ButtonListener extends ListenerAdapter {

	private final App bot;
	private final DBUtil db;

	public ButtonListener(App bot) {
		this.bot = bot;
		this.db = bot.getDBUtil();
	}
	
	@Override
	public void onButtonInteraction(@Nonnull ButtonInteractionEvent event) {
		String buttonId = event.getButton().getId();

		if (buttonId.startsWith("voicepanel")) {
			Member member = event.getMember();
			Guild guild = event.getGuild();

			if (!member.getVoiceState().inAudioChannel()) {
				replyError(event, "bot.voice.listener.not_in_voice");
				return;
			}

			// EDIT if not "claim"
			// AudioChannel ac = author.getVoiceState().getChannel();
			if (!db.voice.existsUser(member.getId())) {
				replyError(event, "errors.no_channel");
				return;
			}

			VoiceChannel vc = event.getGuild().getVoiceChannelById(bot.getDBUtil().voice.getChannel(member.getId()));

			String key = buttonId.split("-")[1];
			if (key.equals("lock")) {
				try {
					vc.upsertPermissionOverride(guild.getPublicRole()).deny(Permission.VOICE_CONNECT).queue();
				} catch (InsufficientPermissionException ex) {
					event.reply(bot.getEmbedUtil().createPermError(event, member, ex.getPermission(), true)).setEphemeral(true).queue();
					return;
				}
				replySuccess(event, "bot.voice.listener.panel.lock");
			}
			else if (key.equals("unlock")) {
				try {
					vc.upsertPermissionOverride(guild.getPublicRole()).clear(Permission.VOICE_CONNECT).queue();
				} catch (InsufficientPermissionException ex) {
					event.reply(bot.getEmbedUtil().createPermError(event, member, ex.getPermission(), true)).setEphemeral(true).queue();
					return;
				}
				replySuccess(event, "bot.voice.listener.panel.unlock");
			}
			else if (key.equals("ghost")) {
				try {
					vc.upsertPermissionOverride(guild.getPublicRole()).deny(Permission.VIEW_CHANNEL).queue();
				} catch (InsufficientPermissionException ex) {
					event.reply(bot.getEmbedUtil().createPermError(event, member, ex.getPermission(), true)).setEphemeral(true).queue();
					return;
				}
				replySuccess(event, "bot.voice.listener.panel.ghost");
			}
			else if (key.equals("unghost")) {
				try {
					vc.upsertPermissionOverride(guild.getPublicRole()).clear(Permission.VIEW_CHANNEL).queue();
				} catch (InsufficientPermissionException ex) {
					event.reply(bot.getEmbedUtil().createPermError(event, member, ex.getPermission(), true)).setEphemeral(true).queue();
					return;
				}
				replySuccess(event, "bot.voice.listener.panel.unghost");
			}
			else if (key.equals("name")) {
				TextInput textInput = TextInput.create("name", bot.getLocaleUtil().getText(event, "bot.voice.listener.panel.name-label"), TextInputStyle.SHORT)
					.setPlaceholder("My channel")
					.setMaxLength(100)
					.build();
				event.replyModal(Modal.create("channel-name", bot.getLocaleUtil().getText(event, "bot.voice.listener.panel.modal")).addActionRow(textInput).build()).queue();
			}
			else if (key.equals("limit")) {
				TextInput textInput = TextInput.create("limit", bot.getLocaleUtil().getText(event, "bot.voice.listener.panel.limit-label"), TextInputStyle.SHORT)
					.setPlaceholder("0 <> 99")
					.setRequiredRange(1, 2)
					.build();
				event.replyModal(Modal.create("channel-limit", bot.getLocaleUtil().getText(event, "bot.voice.listener.panel.modal")).addActionRow(textInput).build()).queue();
			}
			else if (key.equals("permit")) {
				TextInput textInput = TextInput.create("permit", bot.getLocaleUtil().getText(event, "bot.voice.listener.panel.permit-label"), TextInputStyle.SHORT)
					.setPlaceholder(bot.getLocaleUtil().getText(event, "bot.voice.listener.panel.option_mention"))
					.build();
				event.replyModal(Modal.create("channel-permit", bot.getLocaleUtil().getText(event, "bot.voice.listener.panel.modal")).addActionRow(textInput).build()).queue();
			}
			else if (key.equals("reject")) {
				TextInput textInput = TextInput.create("reject", bot.getLocaleUtil().getText(event, "bot.voice.listener.panel.reject-label"), TextInputStyle.SHORT)
					.setPlaceholder(bot.getLocaleUtil().getText(event, "bot.voice.listener.panel.option_mention"))
					.build();
				event.replyModal(Modal.create("channel-reject", bot.getLocaleUtil().getText(event, "bot.voice.listener.panel.modal")).addActionRow(textInput).build()).queue();
			}
			else if (key.equals("delete")) {
				bot.getDBUtil().voice.remove(vc.getId());
				vc.delete().reason("Channel owner request").queue();
				replySuccess(event, "bot.voice.listener.panel.delete");
			}
			else {
				replyError(event, "errors.unknown", "Received key: "+key);
			}
		}

		else if (buttonId.startsWith("verify")) {
			Member member = event.getMember();
			Guild guild = event.getGuild();

			String roleId = db.verify.getVerifyRole(guild.getId());
			if (roleId == null) {
				event.deferEdit().queue();
				return;
			}
			Role role = guild.getRoleById(roleId);
			if (member.getRoles().contains(role)) {
				replyError(event, "bot.verification.you_verified");
				return;
			}

			String steam64 = db.verifyRequest.getSteam64(member.getId());
			if (steam64 != null) {
				// Give verify role to user
				if (role == null) {
					replyError(event, "bot.verification.failed_role", "Role not found");
					bot.getLogger().info("Verify role not found for server "+guild.getName()+"("+guild.getId()+")");
					return;
				}

				event.deferEdit().queue();
				guild.addRoleToMember(member, role).reason("Verification completed - "+steam64).queue(
					success -> {
						bot.getLogListener().onVerified(member, steam64, guild);
					},
					failure -> {
						replyError(event, "bot.verification.failed_role");
						bot.getLogger().info("Was unable to add verify role to user in "+guild.getName()+"("+guild.getId()+")", failure);
					});
			} else {
				String guildId = guild.getId();
				Button refresh = Button.of(ButtonStyle.DANGER, "verify-refresh", bot.getLocaleUtil().getText(event, "bot.verification.listener.refresh"), Emoji.fromUnicode("🔁"));
				// Check if user pressed refresh button
				if (buttonId.endsWith("refresh")) {
					// Ask user to wait for 30 seconds each time
					event.replyEmbeds(new EmbedBuilder().setColor(Constants.COLOR_FAILURE).setTitle(bot.getLocaleUtil().getText(event, "bot.verification.listener.wait_title"))
						.setDescription(bot.getLocaleUtil().getText(event, "bot.verification.listener.wait_value")).build()).setEphemeral(true).queue();
					event.editButton(refresh.asDisabled()).queue(success -> event.editButton(refresh).queueAfter(30, TimeUnit.SECONDS));
					return;
				}
				// Reply with instruction on how to verify, buttons - link and refresh
				Button verify = Button.link(bot.getDBUtil().verify.getVerificationLink(guildId), bot.getLocaleUtil().getText(event, "bot.verification.listener.connect"));
				EmbedBuilder builder = new EmbedBuilder().setColor(Constants.COLOR_DEFAULT).setTitle(bot.getLocaleUtil().getText(event, "bot.verification.listener.embed_title"))
					.setDescription(bot.getDBUtil().verify.getInstructionText(guildId))
					.addField(bot.getLocaleUtil().getText(event, "bot.verification.listener.embed_howto"), bot.getDBUtil().verify.getInstructionField(guildId), false);

				event.replyEmbeds(builder.build()).addActionRow(verify, refresh).setEphemeral(true).queue();
			}
		} else {
			replyError(event, "errors.unknown", "Received button ID: "+buttonId);
		}
	}

	public void replyError(ButtonInteractionEvent event, String... text) {
		if (text.length > 1) {
			event.replyEmbeds(bot.getEmbedUtil().getError(event, text[0], text[1])).setEphemeral(true).queue();
		} else {
			event.replyEmbeds(bot.getEmbedUtil().getError(event, text[0])).setEphemeral(true).queue();
		}
	}

	public void replySuccess(ButtonInteractionEvent event, String path) {
		event.replyEmbeds(new EmbedBuilder().setColor(Constants.COLOR_SUCCESS).setDescription(bot.getLocaleUtil().getLocalized(event.getUserLocale(), path)).build()).setEphemeral(true).queue();
	}

	@Override
	public void onModalInteraction(@Nonnull ModalInteractionEvent event) {
		String modalId = event.getModalId();

		if (modalId.startsWith("channel")) {
			Guild guild = event.getGuild();
			String memberId = event.getMember().getId();
			VoiceChannel vc = guild.getVoiceChannelById(bot.getDBUtil().voice.getChannel(memberId));

			if (vc == null) {
				event.deferEdit().queue();;
				return;
			}

			String key = modalId.split("-")[1];
			if (key.equals("name")) {
				String input = event.getValue("name").getAsString();
				vc.getManager().setName(input).queue();
				if (!bot.getDBUtil().user.exists(memberId)) {
					bot.getDBUtil().user.add(memberId);
				}
				bot.getDBUtil().user.setName(memberId, input);

				event.replyEmbeds(new EmbedBuilder().setColor(Constants.COLOR_SUCCESS)
					.setDescription(bot.getLocaleUtil().getText(event, "bot.voice.listener.panel.limit-done").replace("{limit}", input))
					.build()
				).setEphemeral(true).queue();
			}
			else if (key.equals("limit")) {
				String input = event.getValue("limit").getAsString();
				Integer value;
				try {
					value = Integer.parseInt(input);
				} catch (NumberFormatException ex) {
					event.deferEdit().queue();
					return;
				}
				if (value < 0 || value > 99) {
					event.deferEdit().queue();
					return;
				}

				vc.getManager().setUserLimit(value).queue();
				if (!bot.getDBUtil().user.exists(memberId)) {
					bot.getDBUtil().user.add(memberId);
				}
				bot.getDBUtil().user.setLimit(memberId, value);

				event.replyEmbeds(new EmbedBuilder().setColor(Constants.COLOR_SUCCESS)
					.setDescription(bot.getLocaleUtil().getText(event, "bot.voice.listener.panel.limit-done").replace("{limit}", value.toString()))
					.build()
				).setEphemeral(true).queue();
			}
			else if (key.equals("permit") || key.equals("reject")) {
				event.deferEdit().queue();
				
			}
			else {
				// smt
			}
		} else {
			// smt
			event.deferEdit().queue();
		}
	}
	
}
