package dev.fileeditor.votl.commands.moderation;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;

import dev.fileeditor.votl.App;
import dev.fileeditor.votl.base.command.CooldownScope;
import dev.fileeditor.votl.base.command.SlashCommandEvent;
import dev.fileeditor.votl.commands.CommandBase;
import dev.fileeditor.votl.objects.CaseType;
import dev.fileeditor.votl.objects.CmdAccessLevel;
import dev.fileeditor.votl.objects.CmdModule;
import dev.fileeditor.votl.objects.constants.CmdCategory;
import dev.fileeditor.votl.objects.constants.Constants;

import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;

public class ModStatsCmd extends CommandBase {
	
	public ModStatsCmd(App bot) {
		super(bot);
		this.name = "modstats";
		this.path = "bot.moderation.modstats";
		this.options = List.of(
			new OptionData(OptionType.USER, "user", lu.getText(path+".user.help"))
		);
		this.category = CmdCategory.MODERATION;
		this.module = CmdModule.MODERATION;
		this.accessLevel = CmdAccessLevel.MOD;
		this.cooldown = 15;
		this.cooldownScope = CooldownScope.USER;
	}

	@Override
	protected void execute(SlashCommandEvent event) {
		event.deferReply().queue();
		Member mod = event.optMember("user", event.getMember());
		long guildId = event.getGuild().getIdLong();

		Map<Integer, Integer> count30 = bot.getDBUtil().cases.countCasesByMod(guildId, mod.getIdLong(), Instant.now().minus(30, ChronoUnit.DAYS));
		int roles30 = bot.getDBUtil().tickets.countTicketsByMod(event.getGuild().getIdLong(), mod.getIdLong(), Instant.now().minus(30, ChronoUnit.DAYS), true);
		if (count30.isEmpty() && roles30==0) {
			editError(event, path+".empty");
			return;
		}

		Map<Integer, Integer> count7 = bot.getDBUtil().cases.countCasesByMod(guildId, mod.getIdLong(), Instant.now().minus(7, ChronoUnit.DAYS));
		int roles7 = bot.getDBUtil().tickets.countTicketsByMod(event.getGuild().getIdLong(), mod.getIdLong(), Instant.now().minus(7, ChronoUnit.DAYS), true);

		EmbedBuilder builder = new EmbedBuilder().setColor(Constants.COLOR_DEFAULT)
			.setAuthor(mod.getUser().getName(), null, mod.getEffectiveAvatarUrl())
			.setTitle(lu.getText(event, path+".title"))
			.setFooter("ID: "+mod.getId())
			.setTimestamp(Instant.now());

		int strikes7 = count7.getOrDefault(CaseType.STRIKE_1.getValue(), 0)+count7.getOrDefault(CaseType.STRIKE_2.getValue(), 0)+count7.getOrDefault(CaseType.STRIKE_3.getValue(), 0);
		int strikes30 = count30.getOrDefault(CaseType.STRIKE_1.getValue(), 0)+count30.getOrDefault(CaseType.STRIKE_2.getValue(), 0)+count30.getOrDefault(CaseType.STRIKE_3.getValue(), 0);

		builder.addField(lu.getText(event, path+".strikes"), "%2d | %d".formatted(strikes7, strikes30), true)
			.addField(lu.getText(event, path+".mutes"), "%2d | %d".formatted(count7.getOrDefault(CaseType.MUTE.getValue(), 0), count30.getOrDefault(CaseType.MUTE.getValue(), 0)), true)
			.addField(lu.getText(event, path+".kicks"), "%2d | %d".formatted(count7.getOrDefault(CaseType.KICK.getValue(), 0), count30.getOrDefault(CaseType.KICK.getValue(), 0)), true)
			.addField(lu.getText(event, path+".bans"), "%2d | %d".formatted(count7.getOrDefault(CaseType.BAN.getValue(), 0), count30.getOrDefault(CaseType.BAN.getValue(), 0)), true)
			.addField(lu.getText(event, path+".roles"), "%2d | %d".formatted(roles7, roles30), true)
			.addBlankField(true)
			.addField(lu.getText(event, path+".total"), "%2d | %d".formatted(count7.values().stream().reduce(0, Integer::sum)+roles7, count30.values().stream().reduce(0, Integer::sum)+roles30), false);
		
		editHookEmbed(event, builder.build());
	}
}
