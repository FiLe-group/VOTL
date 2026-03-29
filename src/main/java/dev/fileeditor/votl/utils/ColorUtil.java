package dev.fileeditor.votl.utils;

import org.jetbrains.annotations.NotNull;

import java.awt.*;

public class ColorUtil {

	/**
	 * @param i - integer color value
	 * @param alpha - value from 0(transparent) to 255(opaque)
	 * @return Color
	 */
	@NotNull
	public static Color decodeHex(int i, int alpha) {
		if (alpha > 255 || alpha < 0) alpha = 255;
		return new Color((i >> 16) & 0xFF, (i >> 8) & 0xFF, i & 0xFF, alpha);
	}

	/**
	 * @param i - integer color value
	 * @param alpha - value from 0(transparent) to 1(opaque)
	 * @return Color
	 */
	@NotNull
	public static Color decodeHex(int i, float alpha) {
		int alphaInt = (alpha > 1 || alpha < 0) ? 255 : Math.round(alpha * 255f);
		return new Color((i >> 16) & 0xFF, (i >> 8) & 0xFF, i & 0xFF, alphaInt);
	}

	/**
	 * @param hex - hex string
	 * @param alpha - value from 0(transparent) to 255(opaque)
	 * @return Color
	 */
	@NotNull
	public static Color decodeHex(@NotNull String hex, int alpha) {
		return decodeHex(Integer.decode(hex), alpha);
	}

	/**
	 * @param hex - hex string
	 * @param alpha - value from 0(transparent) to 1(opaque)
	 * @return Color
	 */
	@NotNull
	public static Color decodeHex(@NotNull String hex, float alpha) {
		return decodeHex(Integer.decode(hex), alpha);
	}

	/**
	 * @param hex - hex string (24bit integer)
	 * @return Color
	 */
	@NotNull
	public static Color decodeHex(@NotNull String hex) {
		return Color.decode(hex);
	}

}