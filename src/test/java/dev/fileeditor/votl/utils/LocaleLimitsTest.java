package dev.fileeditor.votl.utils;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

import dev.fileeditor.votl.BaseTest;
import org.json.JSONObject;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Discord rejects commands at startup if option names or descriptions are too long.
 */
public class LocaleLimitsTest extends BaseTest {

	private static final int MAX_NAME = 32;
	private static final int MAX_DESCRIPTION = 100;

	@ParameterizedTest
	@ValueSource(strings = {"en-GB", "ru"})
	public void testCommandTextLimits(String locale) throws IOException {
		JSONObject root;
		try (InputStream is = getClass().getClassLoader().getResourceAsStream("lang/%s.json".formatted(locale))) {
			assertNotNull(is, locale);
			root = new JSONObject(new String(is.readAllBytes(), StandardCharsets.UTF_8));
		}

		List<String> errors = new ArrayList<>();
		walk(root, "", errors);
		assertTrue(errors.isEmpty(), locale + ":\n" + String.join("\n", errors));
	}

	// Objects with both "name" and "help" are command or option definitions
	private static void walk(JSONObject object, String path, List<String> errors) {
		if (object.opt("name") instanceof String name && object.opt("help") instanceof String help) {
			if (name.length() > MAX_NAME)
				errors.add("%s.name is %d characters (max %d)".formatted(path, name.length(), MAX_NAME));
			if (help.length() > MAX_DESCRIPTION)
				errors.add("%s.help is %d characters (max %d)".formatted(path, help.length(), MAX_DESCRIPTION));
		}
		for (String key : object.keySet()) {
			if (object.opt(key) instanceof JSONObject child) walk(child, path + "." + key, errors);
		}
	}

}
