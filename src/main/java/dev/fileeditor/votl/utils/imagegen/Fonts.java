package dev.fileeditor.votl.utils.imagegen;

import dev.fileeditor.votl.utils.exception.FailedToLoadResourceException;

import java.awt.*;
import java.io.IOException;

public class Fonts {

	public static class Roboto {
		public static final Font light, regular, medium;

		static {
			light = loadFont("RobotoMono-Light.ttf");
			regular = loadFont("RobotoMono-Regular.ttf");
			medium = loadFont("RobotoMono-Medium.ttf");
		}
	}

	private static Font loadFont(String resourceName) {
		try {
			return Font.createFont(
				Font.TRUETYPE_FONT,
				Fonts.class.getClassLoader()
					.getResourceAsStream("fonts/" + resourceName)
			);
		} catch (FontFormatException | IOException e) {
			throw new FailedToLoadResourceException(String.format("Failed to load the font resource %s",
				resourceName
			), e);
		}
	}
}