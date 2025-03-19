package dev.fileeditor.votl.servlet.routes;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import dev.fileeditor.oauth2.Scope;
import dev.fileeditor.oauth2.exceptions.InvalidStateException;
import dev.fileeditor.votl.servlet.WebServlet;
import dev.fileeditor.votl.servlet.utils.SessionUtil;
import io.javalin.http.*;

public class ApiCallback implements Handler {
	@Override
	public void handle(Context ctx) throws Exception {
		var query = ctx.queryParamMap();
		if (query.containsKey("error") && query.containsKey("state")) {
			WebServlet.getClient().getStateController().consumeState(ctx.queryParam("state"));
			ObjectNode node = new ObjectMapper().createObjectNode();
			node.put("success", false);
			ctx.json(node);
			return;
		}
		if (!query.containsKey("code") || !query.containsKey("state")) {
			throw new BadRequestResponse("Missing required code or state.");
		}

		final String sessionId = SessionUtil.createSessionId();

		ctx.future(() -> {
			try {
				return WebServlet.getClient().startSession(
					ctx.queryParam("code"), ctx.queryParam("state"), sessionId,
					Scope.IDENTIFY, Scope.GUILDS
				).future().thenRunAsync(() -> {
					ObjectNode node = new ObjectMapper().createObjectNode();
					node.put("success", true);
					node.put("sessionId", sessionId);
					ctx.json(node);
				});
			} catch (InvalidStateException ex) {
				throw new BadRequestResponse("Invalid state provided: %s".formatted(ex.getMessage()));
			}
		});

	}


}
