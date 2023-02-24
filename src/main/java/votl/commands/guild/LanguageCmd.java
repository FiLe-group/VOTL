package votl.commands.guild;

import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;

import javax.annotation.Nonnull;

import votl.App;
import votl.commands.CommandBase;
import votl.objects.CmdAccessLevel;
import votl.objects.CmdModule;
import votl.objects.command.SlashCommand;
import votl.objects.command.SlashCommandEvent;
import votl.objects.constants.CmdCategory;
import votl.utils.file.lang.LangUtil;

import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.entities.MessageEmbed;
import net.dv8tion.jda.api.interactions.DiscordLocale;
import net.dv8tion.jda.api.interactions.commands.Command.Choice;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;

import com.jagrosh.jdautilities.doc.standard.CommandInfo;

@CommandInfo
(
	name = "language",
	description = "Set language for guild.",
	usage = "/language <reset / language:>",
	requirements = "Have 'Manage server' permission"
)
public class LanguageCmd extends CommandBase {

	public LanguageCmd(App bot) {
		super(bot);
		this.name = "language";
		this.path = "bot.guild.language";
		this.children = new SlashCommand[]{new Reset(bot), new Set(bot), new Show(bot)};
		this.userPermissions = new Permission[]{Permission.MANAGE_SERVER};
		this.category = CmdCategory.GUILD;
		this.module = CmdModule.LANGUAGE;
		this.accessLevel = CmdAccessLevel.ADMIN;
		this.mustSetup = true;
	}

	@Override
	protected void execute(SlashCommandEvent event) {

	}

	private class Reset extends CommandBase {

		public Reset(App bot) {
			super(bot);
			this.name = "reset";
			this.path = "bot.guild.language.reset";
		}

		@Override
		protected void execute(SlashCommandEvent event) {
			sendReply(event, lu.getDefaultLanguage());
		}

	}

	private class Set extends CommandBase {

		public Set(App bot) {
			super(bot);
			this.name = "set";
			this.path = "bot.guild.language.set";
			this.options = Collections.singletonList(
				new OptionData(OptionType.STRING, "language", lu.getText(path+".option_description"), true)
					.addChoices(getLangList().stream().map(
						locale -> {
							return new Choice(locale.getLocale(), locale.getLocale());
						}
					).collect(Collectors.toList()))
			);
		}

		@Override
		protected void execute(SlashCommandEvent event) {
			String lang = event.optString("kanguage", lu.getDefaultLanguage());
			sendReply(event, lang);
		}

	}

	private class Show extends CommandBase {

		public Show(App bot) {
			super(bot);
			this.name = "show";
			this.path = "bot.guild.language.show";
		}

		@Override
		protected void execute(SlashCommandEvent event) {
			MessageEmbed embed = bot.getEmbedUtil().getEmbed(event)
				.setTitle(lu.getText(event, path+".embed.title"))
				.setDescription(lu.getText(event, path+".embed.value"))
				.addField(lu.getText(event, path+".embed.field"), getLanguages(), false)
				.build();
			createReplyEmbed(event, embed);
		}

	}

	private void sendReply(SlashCommandEvent event, @Nonnull String language) {
		String guildId = Optional.ofNullable(event.getGuild()).map(g -> g.getId()).orElse("0");

		// fail-safe
		if (DiscordLocale.from(language).equals(DiscordLocale.UNKNOWN)) {
			MessageEmbed embed = bot.getEmbedUtil().getEmbed(event)
				.setTitle(lu.getText(event, "bot.guild.language.embed.available_lang_title"))
				.setDescription(lu.getText(event, "bot.guild.language.embed.available_lang_value"))
				.addField(lu.getText(event, "bot.guild.language.embed.available_lang_field"), getLanguages(), false)
				.build();
			createReplyEmbed(event, embed);
			return;
		}

		bot.getDBUtil().guild.setLanguage(guildId, language);

		MessageEmbed embed = bot.getEmbedUtil().getEmbed(event)
			.setColor(bot.getMessageUtil().getColor("rgb:0,200,30"))
			.setDescription(lu.getText(event, "bot.guild.language.done").replace("{language}", language))
			.build();
		createReplyEmbed(event, embed);
	}

	private List<DiscordLocale> getLangList(){
        return bot.getFileManager().getLanguages();
    }
    
	@Nonnull
    private String getLanguages(){
        List<DiscordLocale> langs = getLangList();
        
        StringBuilder builder = new StringBuilder();
        for (DiscordLocale locale : langs){
            if (builder.length() > 1)
                builder.append("\n");
            
            builder.append(LangUtil.Language.getString(locale));
        }
        
        return Objects.requireNonNull(builder.toString());
    }
}
