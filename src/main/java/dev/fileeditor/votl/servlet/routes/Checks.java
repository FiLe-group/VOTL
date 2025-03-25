package dev.fileeditor.votl.servlet.routes;

import java.util.Arrays;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;

import dev.fileeditor.oauth2.session.Session;
import dev.fileeditor.votl.App;
import dev.fileeditor.votl.objects.CmdModule;
import dev.fileeditor.votl.servlet.WebServlet;

import io.javalin.http.*;
import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Member;

import org.jetbrains.annotations.NotNull;

import static dev.fileeditor.votl.servlet.utils.SessionUtil.SESSION_KEY;

public class Checks {

	public static CompletableFuture<Void> checkPermissionsAsync(Session session, Guild guild, Consumer<Member> success) {
		return WebServlet.getClient().getUser(session).future()
			.thenCompose(user -> guild.retrieveMemberById(user.getIdLong()).submit())
			.thenAccept((member) -> {
				checkAdminPerms(member);
				// Execute code
				success.accept(member);
			})
			.exceptionally((t) -> {
				return null;
			});
	}

	@NotNull
	public static Session getSession(Context ctx) {
		final Session session = ctx.sessionAttribute(SESSION_KEY);
		if (session == null) {
			throw new UnauthorizedResponse("Session not found.");
		}
		return session;
	}

	public static Guild getGuild(Context ctx) {
		final Guild guild = App.getInstance().JDA.getGuildById(ctx.pathParamAsClass("guild", Long.class)
			.getOrThrow(e -> new BadRequestResponse("Incorrect guild ID provided."))
		);
		if (guild == null) {
			throw new NotFoundResponse("Guild not found.");
		}
		return guild;
	}

	public static CmdModule getModule(Context ctx) {
		String moduleName = ctx.pathParamAsClass("module", String.class)
			.check(CmdModule::exists, "Incorrect module name provided.")
			.getOrThrow(e -> new BadRequestResponse("Incorrect module name provided."));

		return Arrays.stream(CmdModule.values())
			.filter(cmd -> cmd.name().equalsIgnoreCase(moduleName))
			.findFirst()
			.orElseThrow(() -> new BadRequestResponse("Module not found."));
	}

	public static CompletableFuture<List<GuildInfo>> getGuilds(Session session) {
		return WebServlet.getClient().getGuilds(session)
			.future()
			.thenApply(guilds -> guilds
				.stream()
				.map(guild -> {
					Guild jdaGuild = App.getInstance().JDA.getGuildById(guild.getId());
					int memberCount = guild.getOnlineCount()>-1 ? guild.getOnlineCount() :
						jdaGuild!=null ? jdaGuild.getMemberCount() : -1;
					return new GuildInfo(
						guild.getId(), guild.getName(),
						guild.getIconId(), guild.getBannerId(),
						memberCount,
						guild.hasPermission(Permission.ADMINISTRATOR),
						jdaGuild != null
					);
				})
				.toList()
			);
	}

	private static void checkAdminPerms(Member member) throws ForbiddenResponse {
		if (!member.hasPermission(Permission.ADMINISTRATOR))
			throw new ForbiddenResponse("User can not perform this action.");
	}

	public record GuildInfo(String id, String name, String icon, String banner, int size, boolean isAdmin, boolean botJoined) {}
	
}
