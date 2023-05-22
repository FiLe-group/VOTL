package votl.objects;

public enum CmdModule {
	VOICE("modules.voice"),
	WEBHOOK("modules.webhook"),
	MODERATION("modules.moderation"),
	VERIFICATION("modules.verification");
	
	private final String path;
	
	CmdModule(String path) {
		this.path = path;
	}

	public String getPath() {
		return path;
	}

}
