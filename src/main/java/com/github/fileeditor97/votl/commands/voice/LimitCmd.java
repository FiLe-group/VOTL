package com.github.fileeditor97.votl.commands.voice;

import java.util.Collections;
import java.util.Objects;
import java.util.Optional;

import com.jagrosh.jdautilities.doc.standard.CommandInfo;

import com.github.fileeditor97.votl.App;
import com.github.fileeditor97.votl.objects.CmdModule;
import com.github.fileeditor97.votl.objects.command.SlashCommand;
import com.github.fileeditor97.votl.objects.command.SlashCommandEvent;
import com.github.fileeditor97.votl.objects.constants.CmdCategory;
import com.github.fileeditor97.votl.utils.message.LocaleUtil;
import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.channel.concrete.VoiceChannel;
import net.dv8tion.jda.api.interactions.DiscordLocale;
import net.dv8tion.jda.api.interactions.InteractionHook;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;

@CommandInfo(
	name = "Limit",
	description = "Sets limit for your channel.",
	usage = "/limit <limit:Integer from 0 to 99>",
	requirements = "Must have created voice channel"
)
public class LimitCmd extends SlashCommand {

	public LimitCmd(App bot) {
		this.name = "limit";
		this.path = "bot.voice.limit";
		this.children = new SlashCommand[]{new Set(bot.getLocaleUtil()), new Reset()};
		this.botPermissions = new Permission[]{Permission.MANAGE_CHANNEL};
		this.bot = bot;
		this.category = CmdCategory.VOICE;
		this.module = CmdModule.VOICE;
		this.mustSetup = true;
	}

	@Override
	protected void execute(SlashCommandEvent event) {

	}

	private class Set extends SlashCommand {

		public Set(LocaleUtil lu) {
			this.name = "set";
			this.path = "bot.voice.limit.set";
			this.options = Collections.singletonList(
				new OptionData(OptionType.INTEGER, "limit", lu.getText("bot.voice.limit.set.option_description"))
					.setRequiredRange(0, 99)
					.setRequired(true)
			);
		}

		@Override
		protected void execute(SlashCommandEvent event) {

			event.deferReply(true).queue(
				hook -> {
					try {
						Integer filLimit = event.getOption("limit", 0, OptionMapping::getAsInt);
						sendReply(event, hook, filLimit);
					} catch (Exception e) {
						hook.editOriginal(bot.getEmbedUtil().getError(event, "errors.request_error", e.toString())).queue();
					}
				}
			);

		}
	}

	private class Reset extends SlashCommand {

		public Reset() {
			this.name = "reset";
			this.path = "bot.voice.limit.reset";
		}

		@Override
		protected void execute(SlashCommandEvent event) {

			event.deferReply(true).queue(
				hook -> {
					Integer filLimit = Optional.ofNullable(bot.getDBUtil().guildVoiceGetLimit(Objects.requireNonNull(event.getGuild()).getId())).orElse(0);
					sendReply(event, hook, filLimit);
				}
			);

		}
	}

	@SuppressWarnings("null")
	private void sendReply(SlashCommandEvent event, InteractionHook hook, Integer filLimit) {

		Member member = Objects.requireNonNull(event.getMember());
		String memberId = member.getId();

		if (!bot.getDBUtil().isVoiceChannel(memberId)) {
			hook.editOriginal(bot.getEmbedUtil().getError(event, "errors.no_channel")).queue();
			return;
		}

		Guild guild = Objects.requireNonNull(event.getGuild());
		DiscordLocale userLocale = event.getUserLocale();

		VoiceChannel vc = guild.getVoiceChannelById(bot.getDBUtil().channelGetChannel(memberId));
		vc.getManager().setUserLimit(filLimit).queue();
		
		if (!bot.getDBUtil().isUser(memberId)) {
			bot.getDBUtil().userAdd(memberId);
		}
		bot.getDBUtil().userSetLimit(memberId, filLimit);

		hook.editOriginalEmbeds(
			bot.getEmbedUtil().getEmbed(event)
				.setDescription(lu.getLocalized(userLocale, "bot.voice.limit.done").replace("{value}", filLimit.toString()))
				.build()
		).queue();
	}
}
