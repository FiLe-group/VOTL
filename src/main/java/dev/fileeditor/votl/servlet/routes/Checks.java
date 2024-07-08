package dev.fileeditor.votl.servlet.routes;

import java.util.function.Consumer;

import dev.fileeditor.votl.servlet.WebServlet;
import dev.fileeditor.votl.servlet.oauth2.Session;

import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Member;

import io.javalin.http.ForbiddenResponse;
import io.javalin.http.NotFoundResponse;

public class Checks {
	
	public static void checkPermissions(Session session, Guild guild, Consumer<Member> success) {
		WebServlet.getWebClient().getUser(session).queue(user -> {
			guild.retrieveMemberById(user.getIdLong()).queue(member -> {
				if (!member.isOwner() && !member.hasPermission(Permission.ADMINISTRATOR))
					throw new ForbiddenResponse("User has no permission.");
				// Execute code
				success.accept(member);
			},
			failure -> {
				throw new NotFoundResponse("User is member of the guild.");
			});
		},
		failure -> {
			throw new NotFoundResponse("Unable to get the user.");
		});
	}
	
}
