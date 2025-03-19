package dev.fileeditor.votl.servlet.routes;

import java.util.Optional;

import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import com.fasterxml.jackson.databind.ObjectMapper;

import net.dv8tion.jda.api.entities.Guild;

import io.javalin.http.Context;
import io.javalin.http.Handler;
import net.dv8tion.jda.api.entities.channel.concrete.Category;
import net.dv8tion.jda.api.entities.channel.concrete.ForumChannel;
import net.dv8tion.jda.api.entities.channel.concrete.NewsChannel;
import net.dv8tion.jda.api.entities.channel.concrete.StageChannel;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import net.dv8tion.jda.api.entities.channel.concrete.VoiceChannel;
import net.dv8tion.jda.api.entities.channel.middleman.GuildChannel;
import org.jetbrains.annotations.NotNull;

public class GetChannels implements Handler {
	@Override
	public void handle(@NotNull Context ctx) throws Exception {
		final Guild guild = Checks.getGuild(ctx);

		ObjectMapper mapper = new ObjectMapper();
		ArrayNode channelArray = mapper.createArrayNode();

		guild.getChannels().forEach(channel -> {
			ObjectNode channelNode = mapper.createObjectNode();

			channelNode.put("id", channel.getIdLong());
			channelNode.put("name", channel.getName());
			channelNode.put("type", channel.getType().getId());
			channelNode.put("category", Optional.ofNullable(getCategory(channel)).map(Category::getIdLong).orElse(null));

			channelArray.add(channelNode);
		});

		// respond
		ctx.json(channelArray);
	}

	private Category getCategory(GuildChannel channel) {
		return switch (channel.getType()) {
			case TEXT -> ((TextChannel) channel).getParentCategory();
			case VOICE -> ((VoiceChannel) channel).getParentCategory();
			case NEWS -> ((NewsChannel) channel).getParentCategory();
			case STAGE -> ((StageChannel) channel).getParentCategory();
			case FORUM -> ((ForumChannel) channel).getParentCategory();
			default -> null;
		};
	}
}
