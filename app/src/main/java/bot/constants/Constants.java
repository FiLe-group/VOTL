package bot.constants;

import java.io.File;
import java.nio.file.Path;
import java.nio.file.Paths;

public final class Constants {
	private Constants() {
		throw new IllegalStateException("Utility class");
	}

	public static final String SEPAR = File.separator;

	public static final Path DATA_PATH = Paths.get("." + SEPAR + "data");
	public static final String LANG_DIR = SEPAR + "lang" + SEPAR;

	public static final String SUCCESS = "\u2611";
	public static final String WARNING = "\u26A0";
	public static final String ERROR = "\u274C";

	public static final String OWNER_ID = "369062521719488524";

	public static final String CATOWNER = "owner";
	public static final String CATVOICE = "voice";
	public static final String CATOTHER = "other";

}
