package bot.commands.guild;

import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;

import javax.annotation.Nonnull;

import com.jagrosh.jdautilities.doc.standard.CommandInfo;

import bot.App;
import bot.objects.CmdAccessLevel;
import bot.objects.command.SlashCommand;
import bot.objects.command.SlashCommandEvent;
import bot.objects.constants.CmdCategory;
import bot.utils.file.lang.LangUtil;
import bot.utils.message.LocaleUtil;
import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.entities.MessageEmbed;
import net.dv8tion.jda.api.interactions.DiscordLocale;
import net.dv8tion.jda.api.interactions.InteractionHook;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.Command.Choice;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;

@CommandInfo
(
	name = "language",
	description = "Set language for guild.",
	usage = "/language <reset / language:>",
	requirements = "Have 'Manage server' permission"
)
public class LanguageCmd extends SlashCommand {

	public LanguageCmd(App bot) {
		this.name = "language";
		this.helpPath = "bot.guild.language.help";
		this.bot = bot;
		this.children = new SlashCommand[]{new Reset(), new Set(bot.getLocaleUtil()), new Show()};
		this.userPermissions = new Permission[]{Permission.MANAGE_SERVER};
		this.category = CmdCategory.GUILD;
		this.module = "language";
		this.accessLevel = CmdAccessLevel.ADMIN;
		this.mustSetup = true;
	}

	@Override
	protected void execute(SlashCommandEvent event) {

	}

	private class Reset extends SlashCommand {

		public Reset() {
			this.name = "reset";
			this.helpPath = "bot.guild.language.reset.help";
		}

		@Override
		protected void execute(SlashCommandEvent event) {

			event.deferReply(true).queue(
				hook -> {
					String defaultLang = lu.getDefaultLanguage();
					sendReply(event, hook, defaultLang);
				}
			);

		}

	}

	private class Set extends SlashCommand {

		@SuppressWarnings("null")
		public Set(LocaleUtil lu) {
			this.name = "set";
			this.helpPath = "bot.guild.language.set.help";
			this.options = Collections.singletonList(
				new OptionData(OptionType.STRING, "language", lu.getText("bot.guild.language.set.option_description"))
					.setRequired(true)
					.addChoices(getLangList().stream().map(
						locale -> {
							return new Choice(locale.getLocale(), locale.getLocale());
						}
					).collect(Collectors.toList()))
			);
		}

		@Override
		@SuppressWarnings("null")
		protected void execute(SlashCommandEvent event) {

			event.deferReply(true).queue(
				hook -> {
					String lang = event.getOption("language", lu.getDefaultLanguage(), OptionMapping::getAsString);
					sendReply(event, hook, lang);
				}
			);

		}

	}

	private class Show extends SlashCommand {

		public Show() {
			this.name = "show";
			this.helpPath = "bot.guild.language.show.help";
		}

		@Override
		protected void execute(SlashCommandEvent event) {

			event.deferReply(true).queue(
				hook -> {
					DiscordLocale userLocale = event.getUserLocale();
					MessageEmbed embed = bot.getEmbedUtil().getEmbed(event)
						.setTitle(lu.getLocalized(userLocale, "bot.guild.language.show.embed.title"))
						.setDescription(lu.getLocalized(userLocale, "bot.guild.language.show.embed.value"))
						.addField(lu.getLocalized(userLocale, "bot.guild.language.show.embed.field"), getLanguages(), false)
						.build();

					hook.editOriginalEmbeds(embed).queue();
				}
			);

		}

	}

	private void sendReply(SlashCommandEvent event, InteractionHook hook, @Nonnull String language) {

		String guildId = Optional.ofNullable(event.getGuild()).map(g -> g.getId()).orElse("0");
		DiscordLocale userLocale = event.getUserLocale();

		// fail-safe
		if (DiscordLocale.from(language).equals(DiscordLocale.UNKNOWN)) {
			MessageEmbed embed = bot.getEmbedUtil().getEmbed(event)
				.setTitle(lu.getLocalized(userLocale, "bot.guild.language.embed.available_lang_title"))
				.setDescription(lu.getLocalized(userLocale, "bot.guild.language.embed.available_lang_value"))
				.addField(lu.getLocalized(userLocale, "bot.guild.language.embed.available_lang_field"), getLanguages(), false)
				.build();
			hook.editOriginalEmbeds(embed).queue();
			return;
		}

		bot.getDBUtil().guildSetLanguage(guildId, language);

		MessageEmbed embed = bot.getEmbedUtil().getEmbed(event)
			.setColor(bot.getMessageUtil().getColor("rgb:0,200,30"))
			.setDescription(lu.getLocalized(userLocale, "bot.guild.language.done").replace("{language}", language))
			.build();
		hook.editOriginalEmbeds(embed).queue();
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
