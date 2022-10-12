package bot.commands.guild;

import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;

import javax.annotation.Nonnull;

import com.jagrosh.jdautilities.command.SlashCommand;
import com.jagrosh.jdautilities.command.SlashCommandEvent;
import com.jagrosh.jdautilities.doc.standard.CommandInfo;

import bot.App;
import bot.objects.constants.CmdCategory;
import bot.utils.exception.CheckException;
import bot.utils.file.lang.LangUtil;
import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.entities.MessageEmbed;
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
	
	private static App bot;
	private static final String MODULE = "language";

	protected static Permission[] userPerms;

	public LanguageCmd(App bot) {
		this.name = "language";
		this.help = bot.getMsg("bot.guild.language.help");
		this.category = CmdCategory.GUILD;
		LanguageCmd.userPerms = new Permission[]{Permission.MANAGE_SERVER};
		LanguageCmd.bot = bot;
		this.children = new SlashCommand[]{new Reset(), new Set(), new Show()};
	}

	@Override
	protected void execute(SlashCommandEvent event) {

	}

	private static class Reset extends SlashCommand {

		public Reset() {
			this.name = "reset";
			this.help = bot.getMsg("bot.guild.language.reset.help");
		}

		@Override
		protected void execute(SlashCommandEvent event) {

			event.deferReply(true).queue(
				hook -> {
					String defaultLang = LanguageCmd.bot.defaultLanguage;
					sendReply(event, hook, defaultLang);
				}
			);

		}

	}

	private static class Set extends SlashCommand {

		@SuppressWarnings("null")
		public Set() {
			this.name = "set";
			this.help = bot.getMsg("bot.guild.language.set.help");
			this.options = Collections.singletonList(
				new OptionData(OptionType.STRING, "language", bot.getMsg("bot.guild.language.set.option_description"))
					.setRequired(true)
					.addChoices(getLangList().stream().map(
						lang -> {
							return new Choice(lang, lang);
						}
					).collect(Collectors.toList()))
			);
		}

		@Override
		protected void execute(SlashCommandEvent event) {

			event.deferReply(true).queue(
				hook -> {
					String lang = event.getOption("language", null, OptionMapping::getAsString).toLowerCase();
					sendReply(event, hook, lang);
				}
			);

		}

	}

	private static class Show extends SlashCommand {

		public Show() {
			this.name = "show";
			this.help = bot.getMsg("bot.guild.language.show.help");
		}

		@Override
		protected void execute(SlashCommandEvent event) {

			event.deferReply(true).queue(
				hook -> {
					try {
						bot.getCheckUtil().moduleEnabled(event, Objects.requireNonNull(event.getGuild()).getId(), MODULE);
					} catch (CheckException ex) {
						hook.editOriginal(ex.getEditData()).queue();
						return;
					}
					String guildId = Optional.ofNullable(event.getGuild()).map(g -> g.getId()).orElse("0");
					MessageEmbed embed = bot.getEmbedUtil().getEmbed(event.getMember())
						.setTitle(bot.getMsg(guildId, "bot.guild.language.show.embed.title"))
						.setDescription(bot.getMsg(guildId, "bot.guild.language.show.embed.value"))
						.addField(bot.getMsg(guildId, "bot.guild.language.show.embed.field"), getLanguages(), false)
						.build();

					hook.editOriginalEmbeds(embed).queue();
				}
			);

		}

	}

	private static void sendReply(SlashCommandEvent event, InteractionHook hook, String language) {

		String guildId = Optional.ofNullable(event.getGuild()).map(g -> g.getId()).orElse("0");

		try {
			bot.getCheckUtil().moduleEnabled(event, guildId, MODULE)
				.hasPermissions(event.getTextChannel(), event.getMember(), userPerms)
				.guildExists(event, guildId);
		} catch (CheckException ex) {
			hook.editOriginal(ex.getEditData()).queue();
			return;
		}

		// fail-safe
		if (!getLangList().contains(language.toLowerCase())) {
			MessageEmbed embed = bot.getEmbedUtil().getEmbed(event.getMember())
				.setTitle(bot.getMsg(guildId, "bot.guild.language.embed.available_lang_title"))
				.setDescription(bot.getMsg(guildId, "bot.guild.language.embed.available_lang_value"))
				.addField(bot.getMsg(guildId, "bot.guild.language.embed.available_lang_field"), getLanguages(), false)
				.build();
			hook.editOriginalEmbeds(embed).queue();
			return;
		}

		bot.getDBUtil().guildSetLanguage(guildId, language);

		MessageEmbed embed = bot.getEmbedUtil().getEmbed(event.getMember())
			.setColor(bot.getMessageUtil().getColor("rgb:0,200,30"))
			.setDescription(bot.getMsg(guildId, "bot.guild.language.done").replace("{language}", language))
			.build();
		hook.editOriginalEmbeds(embed).queue();
	}

	private static List<String> getLangList(){
        return bot.getFileManager().getLanguages();
    }
    
	@Nonnull
    private static String getLanguages(){
        List<String> langs = getLangList();
        Collections.sort(langs);
        
        StringBuilder builder = new StringBuilder();
        for(String language : langs){
            if(builder.length() > 1)
                builder.append("\n");
            
            builder.append(LangUtil.Language.getString(language));
        }
        
        return Objects.requireNonNull(builder.toString());
    }
}
