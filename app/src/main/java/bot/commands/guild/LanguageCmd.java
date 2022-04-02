package bot.commands.guild;

import java.util.Collections;
import java.util.List;

import com.jagrosh.jdautilities.command.Command;
import com.jagrosh.jdautilities.command.CommandEvent;
import com.jagrosh.jdautilities.doc.standard.CommandInfo;

import bot.App;
import bot.utils.file.lang.LangUtil;
import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.entities.MessageEmbed;

@CommandInfo
(
	name = "language",
	description = "Set language for guild.",
	usage = "{prefix}language <language from list>",
	requirements = "Have 'Manage server' permission"
)
public class LanguageCmd extends Command {
	
	private final App bot;

	protected Permission[] userPerms;
	protected Permission[] botPerms;

	public LanguageCmd(App bot) {
		this.name = "language";
		this.help = "bot.guild.language.description";
		this.category = new Category("guild");
		this.userPerms = new Permission[]{Permission.MANAGE_SERVER};
		this.botPerms = new Permission[]{Permission.MESSAGE_EMBED_LINKS, Permission.MANAGE_CHANNEL};
		this.bot = bot;
	}

	@Override
	protected void execute(CommandEvent event) {
		if (bot.getCheckUtil().lacksPermissions(event.getTextChannel(), event.getMember(), true, botPerms) || 
				bot.getCheckUtil().lacksPermissions(event.getTextChannel(), event.getMember(), userPerms))
			return;

		if (!bot.getDBUtil().isGuild(event.getGuild().getId())) {
			bot.getEmbedUtil().sendError(event.getEvent(), "errors.guild_not_setup");
			return;
		}

		String args = event.getArgs();
		if (args.isEmpty()) {
			MessageEmbed embed = bot.getEmbedUtil().getEmbed(event.getMember())
				.setTitle(bot.getMsg(event.getGuild().getId(), "bot.guild.language.available_lang"))
				.setDescription(getLanguages())
				.build();
			event.reply(embed);
			return;
		} else if (args.length() < 2 || !getLangList().contains(args.toLowerCase())) {
			bot.getEmbedUtil().sendError(event.getEvent(), "bot.guild.language.invalid_lang");
			return;
		}

		String language = args.toLowerCase();

		bot.getDBUtil().guildSetLanguage(event.getGuild().getId(), language);

		MessageEmbed embed = bot.getEmbedUtil().getEmbed(event.getMember())
			.setColor(bot.getMessageUtil().getColor("rgb:0,200,30"))
			.setDescription(bot.getMsg(event.getGuild().getId(), "bot.guild.language.done").replace("{language}", language))
			.build();
		event.reply(embed);
	}

	private List<String> getLangList(){
        return bot.getFileManager().getLanguages();
    }
    
    private String getLanguages(){
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
