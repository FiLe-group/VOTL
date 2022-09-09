package bot.commands.guild;

import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

import com.jagrosh.jdautilities.command.SlashCommand;
import com.jagrosh.jdautilities.command.SlashCommandEvent;
import com.jagrosh.jdautilities.doc.standard.CommandInfo;

import bot.App;
import bot.utils.file.lang.LangUtil;
import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.entities.MessageEmbed;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.Command.Choice;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import net.dv8tion.jda.api.utils.messages.MessageCreateData;
import net.dv8tion.jda.api.utils.messages.MessageEditData;

@CommandInfo
(
	name = "language",
	description = "Set language for guild.",
	usage = "/language <reset / language:>",
	requirements = "Have 'Manage server' permission"
)
public class LanguageCmd extends SlashCommand {
	
	private static App bot;

	protected static Permission[] userPerms;

	public LanguageCmd(App bot) {
		this.name = "language";
		this.category = new Category("guild");
		LanguageCmd.userPerms = new Permission[]{Permission.MANAGE_SERVER};
		this.children = new SlashCommand[]{new Reset(bot), new Set(bot), new Show(bot)};
		LanguageCmd.bot = bot;
	}

	@Override
	protected void execute(SlashCommandEvent event) {

	}

	private static class Reset extends SlashCommand {

		public Reset(App bot) {
			this.name = "reset";
			this.help = bot.getMsg("0", "bot.guild.language.reset.description");
		}

		@Override
		protected void execute(SlashCommandEvent event) {

			event.deferReply(true).queue(
				hook -> {
					String defaultLang = LanguageCmd.bot.defaultLanguage;

					MessageEditData reply = getReply(event, defaultLang);

					hook.editOriginal(reply).queue();
				}
			);

		}

	}

	private static class Set extends SlashCommand {

		public Set(App bot) {
			this.name = "set";
			this.help = bot.getMsg("0", "bot.guild.language.set.description");
			this.options = Collections.singletonList(
				new OptionData(OptionType.STRING, "language", bot.getMsg("0", "bot.guild.language.set.option_description"))
					.setRequired(true)
					.addChoices(bot.getFileManager().getLanguages().stream().map(
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
					String lang = event.getOption("language").getAsString().toLowerCase();

					MessageEditData reply = getReply(event, lang);

					hook.editOriginal(reply).queue();
				}
			);

		}

	}

	private static class Show extends SlashCommand {

		private final App bot;

		public Show(App bot) {
			this.name = "show";
			this.help = bot.getMsg("0", "bot.guild.language.show.description");
			this.bot = bot;
		}

		@Override
		protected void execute(SlashCommandEvent event) {

			event.deferReply(true).queue(
				hook -> {
					MessageEmbed embed = bot.getEmbedUtil().getEmbed(event.getMember())
						.setTitle(bot.getMsg(event.getGuild().getId(), "bot.guild.language.show.embed.title"))
						.setDescription(bot.getMsg(event.getGuild().getId(), "bot.guild.language.show.embed.value"))
						.addField(bot.getMsg(event.getGuild().getId(), "bot.guild.language.show.embed.field"), getLanguages(), false)
						.build();

					hook.editOriginalEmbeds(embed).queue();
				}
			);

		}

	}

	private static MessageEditData getReply(SlashCommandEvent event, String language) {
		
		MessageCreateData permission = bot.getCheckUtil().lacksPermissions(event.getTextChannel(), event.getMember(), userPerms);
		if (permission != null)
			return MessageEditData.fromCreateData(permission);

		if (!bot.getDBUtil().isGuild(event.getGuild().getId())) {
			return MessageEditData.fromCreateData(bot.getEmbedUtil().getError(event, "errors.guild_not_setup"));
		}

		if (!getLangList().contains(language.toLowerCase())) {
			MessageEmbed embed = bot.getEmbedUtil().getEmbed(event.getMember())
				.setTitle(bot.getMsg(event.getGuild().getId(), "bot.guild.language.embed.available_lang_title"))
				.setDescription(bot.getMsg(event.getGuild().getId(), "bot.guild.language.embed.available_lang_value"))
				.addField(bot.getMsg(event.getGuild().getId(), "bot.guild.language.embed.available_lang_field"), getLanguages(), false)
				.build();
			return MessageEditData.fromEmbeds(embed);
		}

		bot.getDBUtil().guildSetLanguage(event.getGuild().getId(), language);

		MessageEmbed embed = bot.getEmbedUtil().getEmbed(event.getMember())
			.setColor(bot.getMessageUtil().getColor("rgb:0,200,30"))
			.setDescription(bot.getMsg(event.getGuild().getId(), "bot.guild.language.done").replace("{language}", language))
			.build();
		return MessageEditData.fromEmbeds(embed);
	}

	private static List<String> getLangList(){
        return bot.getFileManager().getLanguages();
    }
    
    private static String getLanguages(){
        List<String> langs = getLangList();
        Collections.sort(langs);
        
        StringBuilder builder = new StringBuilder();
        for(String language : langs){
            if(builder.length() > 1)
                builder.append("\n");
            
            builder.append(LangUtil.Language.getString(language));
        }
        
        return builder.toString();
    }
}
