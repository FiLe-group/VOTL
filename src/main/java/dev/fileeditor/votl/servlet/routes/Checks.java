package dev.fileeditor.votl.servlet.routes;

import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;

import dev.fileeditor.oauth2.entities.OAuth2User;
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

	public static CompletableFuture<Void> retrieveGuilds(Session session, Consumer<List<GuildInfo>> filteredGuilds) {
		return WebServlet.getClient().getUser(session)
			.future()
			.thenCombine(getGuilds(session), Checks::filterGuilds)
			.thenCompose(guilds -> guilds)
			.thenAccept(filteredGuilds);
	}

	private static CompletableFuture<List<Guild>> getGuilds(Session session) {
		return WebServlet.getClient().getGuilds(session)
			.future()
			.thenApply(guilds -> guilds.stream()
				.map(guild -> App.getInstance().JDA.getGuildById(guild.getIdLong()))
				.filter(Objects::nonNull)
				.toList()
			);
	}

	private static CompletableFuture<List<GuildInfo>> filterGuilds(OAuth2User user, List<Guild> guilds) {
		List<CompletableFuture<GuildInfo>> futures = guilds.parallelStream()
			.map(guild -> guild.retrieveMemberById(user.getIdLong()).submit()
				.thenApply(member -> new GuildInfo(
					guild.getId(),
					guild.getName(),
					guild.getIconId(),
					guild.getBannerId(),
					guild.getMemberCount(),
					member.hasPermission(Permission.ADMINISTRATOR)
				))
				.exceptionally((t) -> {
					return null;
				})
			)
			.toList();

		return CompletableFuture.allOf(futures.toArray(new CompletableFuture[0]))
			.thenApply(v -> futures.stream()
				.map(CompletableFuture::join)
				.filter(Objects::nonNull)
				.toList()
			);
	}

	private static void checkAdminPerms(Member member) throws ForbiddenResponse {
		if (!member.hasPermission(Permission.ADMINISTRATOR))
			throw new ForbiddenResponse("User can not perform this action.");
	}

	public record GuildInfo(String id, String name, String icon, String banner, int size, boolean isAdmin) {}
	
}
