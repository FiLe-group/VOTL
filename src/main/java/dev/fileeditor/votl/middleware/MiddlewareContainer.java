package dev.fileeditor.votl.middleware;

import dev.fileeditor.votl.contracts.middleware.Middleware;

class MiddlewareContainer {

	private final Middleware middleware;
	private final String[] arguments;

	public MiddlewareContainer(Middleware middleware) {
		this.middleware = middleware;
		this.arguments = new String[0];
	}

	MiddlewareContainer(Middleware middleware, String[] arguments) {
		this.middleware = middleware;
		this.arguments = arguments;
	}

	public Middleware getMiddleware() {
		return middleware;
	}

	public String[] getArguments() {
		return arguments;
	}

}
