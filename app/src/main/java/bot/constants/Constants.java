package bot.constants;

public final class Constants {
	private Constants() {
		throw new IllegalStateException("Utility class");
	}

	private static final String DATA_PATH = "./app/src/main/resources";
	public static final String CONFIG_FILE = DATA_PATH + "/config.json";
	public static final String DATABASE_FILE = DATA_PATH + "/server.db";
	public static final String LANG_PATH = DATA_PATH + "/lang/";

	public static final String SUCCESS = "\u2611";
	public static final String WARNING = "\u26A0";
	public static final String ERROR = "\u274C";

	public static final String OWNER = "GreenLord#0593";

}
