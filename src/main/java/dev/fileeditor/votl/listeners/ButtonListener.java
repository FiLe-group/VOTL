package dev.fileeditor.votl.listeners;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Objects;

import javax.annotation.Nonnull;

import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.Mentions;
import net.dv8tion.jda.api.entities.PermissionOverride;
import net.dv8tion.jda.api.entities.Role;
import net.dv8tion.jda.api.entities.channel.concrete.VoiceChannel;
import net.dv8tion.jda.api.events.interaction.ModalInteractionEvent;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import net.dv8tion.jda.api.events.interaction.component.EntitySelectInteractionEvent;
import net.dv8tion.jda.api.exceptions.InsufficientPermissionException;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import net.dv8tion.jda.api.interactions.components.selections.EntitySelectMenu;
import net.dv8tion.jda.api.interactions.components.selections.EntitySelectMenu.SelectTarget;
import net.dv8tion.jda.api.interactions.components.text.TextInput;
import net.dv8tion.jda.api.interactions.components.text.TextInputStyle;
import net.dv8tion.jda.api.interactions.modals.Modal;

import dev.fileeditor.votl.App;
import dev.fileeditor.votl.objects.Emotes;
import dev.fileeditor.votl.objects.constants.Constants;
import dev.fileeditor.votl.utils.database.DBUtil;
import dev.fileeditor.votl.utils.file.lang.LocaleUtil;

public class ButtonListener extends ListenerAdapter {

	private final App bot;
	private final DBUtil db;
	private final LocaleUtil lu;

	public ButtonListener(App bot) {
		this.bot = bot;
		this.db = bot.getDBUtil();
		this.lu = bot.getLocaleUtil();
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
				TextInput textInput = TextInput.create("name", lu.getText(event, "bot.voice.listener.panel.name-label"), TextInputStyle.SHORT)
					.setPlaceholder("My channel")
					.setMaxLength(100)
					.build();
				event.replyModal(Modal.create("channel-name", lu.getText(event, "bot.voice.listener.panel.modal")).addActionRow(textInput).build()).queue();
			}
			else if (key.equals("limit")) {
				TextInput textInput = TextInput.create("limit", lu.getText(event, "bot.voice.listener.panel.limit-label"), TextInputStyle.SHORT)
					.setPlaceholder("0 <> 99")
					.setRequiredRange(1, 2)
					.build();
				event.replyModal(Modal.create("channel-limit", lu.getText(event, "bot.voice.listener.panel.modal")).addActionRow(textInput).build()).queue();
			}
			else if (key.equals("permit")) {
				String text = lu.getText(event, "bot.voice.listener.panel.permit-label");
				event.reply(text).addActionRow(EntitySelectMenu.create("channel-permit", SelectTarget.USER, SelectTarget.ROLE).setMaxValues(10).build()).setEphemeral(true).queue();
			}
			else if (key.equals("reject")) {
				String text = lu.getText(event, "bot.voice.listener.panel.reject-label");
				event.reply(text).addActionRow(EntitySelectMenu.create("channel-reject", SelectTarget.USER, SelectTarget.ROLE).setMaxValues(10).build()).setEphemeral(true).queue();
			}
			else if (key.equals("perms")) {
				event.deferReply(true).queue();
				EmbedBuilder embedBuilder = bot.getEmbedUtil().getEmbed(event)
					.setTitle(lu.getLocalized(event.getUserLocale(), "bot.voice.listener.panel.perms-embed.title").replace("{channel}", vc.getName()))
					.setDescription(lu.getLocalized(event.getUserLocale(), "bot.voice.listener.panel.perms-embed.field")+"\n\n");
				
				//@Everyone
				PermissionOverride publicOverride = vc.getPermissionOverride(guild.getPublicRole());

				String view = contains(publicOverride, Permission.VIEW_CHANNEL);
				String join = contains(publicOverride, Permission.VOICE_CONNECT);
				
				embedBuilder = embedBuilder.appendDescription(formatHolder(lu.getLocalized(event.getUserLocale(), "bot.voice.listener.panel.perms-embed.everyone"), view, join))
					.appendDescription("\n\n" + lu.getLocalized(event.getUserLocale(), "bot.voice.listener.panel.perms-embed.roles") + "\n");

				//Roles
				List<PermissionOverride> overrides = new ArrayList<>(vc.getRolePermissionOverrides()); // cause given override list is immutable
				try {
					overrides.remove(vc.getPermissionOverride(Objects.requireNonNull(guild.getBotRole()))); // removes bot's role
					overrides.remove(vc.getPermissionOverride(guild.getPublicRole())); // removes @everyone role
				} catch (NullPointerException ex) {
					bot.getLogger().warn("PermsCmd null pointer at role override remove");
				}
				
				if (overrides.isEmpty()) {
					embedBuilder.appendDescription(lu.getLocalized(event.getUserLocale(), "bot.voice.listener.panel.perms-embed.none") + "\n");
				} else {
					for (PermissionOverride ov : overrides) {
						view = contains(ov, Permission.VIEW_CHANNEL);
						join = contains(ov, Permission.VOICE_CONNECT);

						embedBuilder.appendDescription(formatHolder(ov.getRole().getName(), view, join) + "\n");
					}
				}
				embedBuilder.appendDescription("\n" + lu.getLocalized(event.getUserLocale(), "bot.voice.listener.panel.perms-embed.members") + "\n");

				//Members
				overrides = new ArrayList<>(vc.getMemberPermissionOverrides());
				try {
					overrides.remove(vc.getPermissionOverride(event.getMember())); // removes user
					overrides.remove(vc.getPermissionOverride(guild.getSelfMember())); // removes bot
				} catch (NullPointerException ex) {
					bot.getLogger().warn("PermsCmd null pointer at member override remove");
				}

				EmbedBuilder embedBuilder2 = embedBuilder;
				List<PermissionOverride> ovs = overrides;

				guild.retrieveMembersByIds(false, overrides.stream().map(ov -> ov.getId()).toArray(String[]::new)).onSuccess(
					members -> {
						if (members.isEmpty()) {
							embedBuilder2.appendDescription(lu.getLocalized(event.getUserLocale(), "bot.voice.listener.panel.perms-embed.none") + "\n");
						} else {
							for (PermissionOverride ov : ovs) {
								String view2 = contains(ov, Permission.VIEW_CHANNEL);
								String join2 = contains(ov, Permission.VOICE_CONNECT);

								Member find = members.stream().filter(m -> m.getId().equals(ov.getId())).findFirst().get(); 
								embedBuilder2.appendDescription(formatHolder(find.getEffectiveName(), view2, join2) + "\n");
							}
						}

						event.getHook().sendMessageEmbeds(embedBuilder2.build()).queue();
					}
				);
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

			if (db.verify.isBlacklisted(guild.getId(), member.getId())) {
				replyError(event, "bot.verification.you_blacklisted");
				return;
			}

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

			event.deferEdit().queue();
			guild.addRoleToMember(member, role).reason("Verification completed").queue(
					success -> {
						bot.getLogListener().onVerified(member, guild);
					},
					failure -> {
						replyError(event, "bot.verification.failed_role");
						bot.getLogger().info("Was unable to add verify role to user in "+guild.getName()+"("+guild.getId()+")", failure);
					});

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
		event.replyEmbeds(new EmbedBuilder().setColor(Constants.COLOR_SUCCESS).setDescription(lu.getLocalized(event.getUserLocale(), path)).build()).setEphemeral(true).queue();
	}

	@Override
	public void onModalInteraction(@Nonnull ModalInteractionEvent event) {
		String modalId = event.getModalId();

		if (modalId.startsWith("channel")) {
			Guild guild = event.getGuild();
			String memberId = event.getMember().getId();

			String tcId = bot.getDBUtil().voice.getChannel(memberId);
			if (tcId == null) {
				event.deferEdit().queue();
				return;
			}
			VoiceChannel vc = guild.getVoiceChannelById(tcId);
			if (vc == null) {
				event.deferEdit().queue();
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
					.setDescription(lu.getText(event, "bot.voice.listener.panel.name-done").replace("{channel}", input))
					.build()
				).setEphemeral(true).queue();
			} else if (key.equals("limit")) {
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
					.setDescription(lu.getText(event, "bot.voice.listener.panel.limit-done").replace("{limit}", value.toString()))
					.build()
				).setEphemeral(true).queue();
			} else {
				event.deferEdit().queue();
			}
		} else {
			event.deferEdit().queue();
		}
	}

	@Override
	public void onEntitySelectInteraction(EntitySelectInteractionEvent event) {
		String selectId = event.getComponentId();

		if (selectId.startsWith("channel")) {
			Guild guild = event.getGuild();
			Member author = event.getMember();

			String tcId = bot.getDBUtil().voice.getChannel(author.getId());
			if (tcId == null) {
				event.deferEdit().queue();
				return;
			}
			VoiceChannel vc = guild.getVoiceChannelById(tcId);
			if (vc == null) {
				event.deferEdit().queue();
				return;
			}

			String key = selectId.split("-")[1];
			if (key.equals("permit") || key.equals("reject")) {
				Mentions mentions = event.getMentions();
				
				List<Member> members = mentions.getMembers();
				List<Role> roles = mentions.getRoles();
				if (members.isEmpty() & roles.isEmpty()) {
					event.deferEdit().queue();
					return;
				}
				if (members.contains(author) || members.contains(guild.getSelfMember())) {
					event.replyEmbeds(bot.getEmbedUtil().getError(event, "bot.voice.listener.panel.not_self"));
					return;
				}

				List<String> mentionStrings = new ArrayList<>();
				String text = "";
				
				if (key.equals("permit")) {
					for (Member member : members) {
						try {
							vc.getManager().putPermissionOverride(member, EnumSet.of(Permission.VOICE_CONNECT, Permission.VIEW_CHANNEL), null).queue();
							mentionStrings.add(member.getEffectiveName());
						} catch (InsufficientPermissionException ex) {
							event.replyEmbeds(bot.getEmbedUtil().getError(event, "errors.missing_perms.self"));
							return;
						}
					}
			
					for (Role role : roles) {
						if (!role.hasPermission(new Permission[]{Permission.ADMINISTRATOR, Permission.MANAGE_SERVER, Permission.MANAGE_PERMISSIONS, Permission.MANAGE_ROLES}))
							try {
								vc.getManager().putPermissionOverride(role, EnumSet.of(Permission.VOICE_CONNECT, Permission.VIEW_CHANNEL), null).queue();
								mentionStrings.add(role.getName());
							} catch (InsufficientPermissionException ex) {
								event.replyEmbeds(bot.getEmbedUtil().getError(event, "errors.missing_perms.self"));
								return;
							}
					}
	
					text = lu.getUserText(event, "bot.voice.listener.panel.permit-done", mentionStrings);
				} else {
					for (Member member : members) {
						try {
							vc.getManager().putPermissionOverride(member, null, EnumSet.of(Permission.VOICE_CONNECT)).queue();
							mentionStrings.add(member.getEffectiveName());
						} catch (InsufficientPermissionException ex) {
							event.replyEmbeds(bot.getEmbedUtil().getError(event, "errors.missing_perms.self"));
							return;
						}
						if (vc.getMembers().contains(member)) {
							guild.kickVoiceMember(member).queue();
						}
					}
			
					for (Role role : roles) {
						if (!role.hasPermission(new Permission[]{Permission.ADMINISTRATOR, Permission.MANAGE_SERVER, Permission.MANAGE_PERMISSIONS, Permission.MANAGE_ROLES}))
							try {
								vc.getManager().putPermissionOverride(role, null, EnumSet.of(Permission.VOICE_CONNECT)).queue();
								mentionStrings.add(role.getName());
							} catch (InsufficientPermissionException ex) {
								event.replyEmbeds(bot.getEmbedUtil().getError(event, "errors.missing_perms.self"));
								return;
							}
					}

					text = lu.getUserText(event, "bot.voice.listener.panel.reject-done", mentionStrings);
				}

				event.editMessageEmbeds(bot.getEmbedUtil().getEmbed(event)
						.setDescription(text)
						.build()
					).setContent("").setComponents().queue();
				
			} else {
				event.deferEdit().queue();
			}
		} else {
			event.deferEdit().queue();
		}
	}

	private String contains(PermissionOverride override, Permission perm) {
		if (override != null) {
			if (override.getAllowed().contains(perm))
				return Emotes.CHECK_C.getEmote();
			else if (override.getDenied().contains(perm))
				return Emotes.CROSS_C.getEmote();
		}
		return Emotes.NONE.getEmote();
	}

	@Nonnull
	private String formatHolder(String holder, String view, String join) {
		return "> " + view + " | " + join + " | `" + holder + "`";
	}
	
}
