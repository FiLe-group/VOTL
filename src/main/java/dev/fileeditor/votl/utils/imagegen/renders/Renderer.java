package dev.fileeditor.votl.utils.imagegen.renders;

import dev.fileeditor.votl.utils.exception.RenderNotReadyYetException;
import org.jetbrains.annotations.Nullable;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;

public abstract class Renderer {

	/**
	 * Checks if the render is ready to be used, if the
	 * {@link #renderToBytes()} method is called while this returns false,
	 * the {@link RenderNotReadyYetException} will be thrown, preventing
	 * the render from running.
	 *
	 * @return <code>True</code> if the rendered is ready, <code>False</code> otherwise.
	 */
	public abstract boolean canRender();

	/**
	 * Handles the rendering process.
	 *
	 * @return The generated image as a buffered image object,
	 * or <code>NULL</code> if something went wrong.
	 * @throws IOException Thrown when underlying render throws an IOException
	 */
	@Nullable
	protected abstract BufferedImage handleRender() throws IOException;

	/**
	 * Renders the image to build the buffered image object,
	 * then converts it to a byte stream.
	 *
	 * @return The generated image as an array of bytes, or <code>NULL</code>.
	 * @throws IOException Thrown when underlying render throws an IOException.
	 */
	@Nullable
	public byte[] renderToBytes() throws IOException {
		if (!canRender()) {
			throw new RenderNotReadyYetException("One or more required arguments for the renderer have not been setup yet.");
		}

		final BufferedImage bufferedImage = handleRender();
		if (bufferedImage == null) {
			return null;
		}

		ByteArrayOutputStream byteStream = new ByteArrayOutputStream();

		ImageIO.write(bufferedImage, "png", byteStream);
		byteStream.flush();

		byte[] bytes = byteStream.toByteArray();
		byteStream.close();

		return bytes;
	}

}
