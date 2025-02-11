package dev.fileeditor.votl.servlet.routes;

import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;

import dev.fileeditor.oauth2.session.Session;
import dev.fileeditor.votl.servlet.WebServlet;

import io.javalin.http.util.CookieStore;
import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Member;

import io.javalin.http.ForbiddenResponse;
import io.javalin.http.NotFoundResponse;

public class Checks {
	
	public static void checkPermissions(Session session, Guild guild, Consumer<Member> success) {
		WebServlet.getClient().getUser(session).queue(user -> {
			guild.retrieveMemberById(user.getIdLong()).queue(member -> {
				checkPerms(member);
				// Execute code
				success.accept(member);
			},
			failure -> {
				throw new NotFoundResponse("User is not member of the guild.");
			});
		},
		failure -> {
			throw new NotFoundResponse("Unable to get the user.");
		});
	}

	// TODO
	public static CompletableFuture<Void> checkPermissionsAsync(CookieStore cs, Guild guild, Consumer<Member> success) {
		return WebServlet.getClient().getUser(WebServlet.getSession(cs)).future()
			.thenCompose(user -> guild.retrieveMemberById(user.getIdLong()).submit())
			.thenAccept((member) -> {
				checkPerms(member);
				// Execute code
				success.accept(member);
			});
	}

	private static void checkPerms(Member member) throws ForbiddenResponse{
		if (!member.hasPermission(Permission.ADMINISTRATOR))
			throw new ForbiddenResponse("User can not perform this action.");
	}
	
}
