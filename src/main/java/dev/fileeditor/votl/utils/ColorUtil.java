package dev.fileeditor.votl.utils;

import java.awt.*;

public class ColorUtil {

	/**
	 * @param hex - hex string
	 * @param alpha - value from 0(transparent) to 255(opaque)
	 * @return Color
	 */
	public static Color decode(String hex, int alpha) {
		int i = Integer.decode(hex);
		if (alpha > 255 || alpha < 0) alpha = 0;
		return new Color((i >> 16) & 0xFF, (i >> 8) & 0xFF, i & 0xFF, alpha);
	}

	public static Color decode(int i, int alpha) {
		if (alpha > 255 || alpha < 0) alpha = 255;
		return new Color((i >> 16) & 0xFF, (i >> 8) & 0xFF, i & 0xFF, alpha);
	}

	public static Color decode(String hex) {
		return Color.decode(hex);
	}

	/**
	 * Creates an sRGB color with the specified red, green,
	 * and blue values with in the range (0 - 255).
	 *
	 * @param red   The red component.
	 * @param green The green component
	 * @param blue  The blue component
	 * @return The color with the given values.
	 */
	public static Color getColor(float red, float green, float blue) {
		return new Color(red / 255F, green / 255F, blue / 255F, 1F);
	}

	/**
	 * Creates an sRGBA color with the specified red, green,
	 * blue, and alpha values with in the range (0 - 255).
	 * The alpha should be in the rage of 0 and 100.
	 *
	 * @param red   The red component.
	 * @param green The green component.
	 * @param blue  The blue component.
	 * @param alpha The alpha component.
	 * @return The color with the given values.
	 */
	public static Color getColor(float red, float green, float blue, float alpha) {
		return new Color(red / 255F, green / 255F, blue / 255F, alpha / 100F);
	}


}