package dev.fileeditor.votl.utils;

import java.io.IOException;
import java.net.URI;
import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;

import dev.fileeditor.votl.utils.file.FileManager;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.json.JSONArray;
import org.json.JSONObject;
import org.slf4j.LoggerFactory;

import ch.qos.logback.classic.Logger;
import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.MediaType;
import okhttp3.MultipartBody;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import okhttp3.ResponseBody;

/**
 * Uploads files to a Zipline instance:
 * <ul>
 *     <li>media that is about to disappear from Discord's CDN (deleted emojis, stickers,
 *     replaced role and server icons), so log links keep working. Expires after {@link #EXPIRES_IN}.</li>
 *     <li>ticket transcripts. Expire after {@code zipline-transcript-expiry} (default {@link #TRANSCRIPT_EXPIRES_IN}).</li>
 * </ul>
 */
public class ZiplineUtil {

	private final Logger log = (Logger) LoggerFactory.getLogger(ZiplineUtil.class);

	public static final String EXPIRES_IN = "14d";
	public static final String TRANSCRIPT_EXPIRES_IN = "90d";
	private static final MediaType HTML = MediaType.get("text/html; charset=utf-8");
	private static final long MAX_SIZE = 10 * 1024 * 1024; // 10 MB

	private final OkHttpClient client = new OkHttpClient.Builder()
		.callTimeout(Duration.ofSeconds(30))
		.build();

	private final @Nullable String uploadUrl;
	private final @Nullable String token;
	private final @NotNull String transcriptExpiresIn;

	public ZiplineUtil(@NotNull FileManager fileManager) {
		var baseUrl = fileManager.getNullableString("config", "zipline-url");
		var token = fileManager.getNullableString("config", "zipline-token");
		if (baseUrl == null || token == null) {
			this.uploadUrl = null;
			this.token = null;
		} else {
			this.uploadUrl = baseUrl.replaceAll("/+$", "") + "/api/upload";
			this.token = token;
		}
		this.transcriptExpiresIn = Objects.requireNonNullElse(
			fileManager.getNullableString("config", "zipline-transcript-expiry"), TRANSCRIPT_EXPIRES_IN
		);
	}

	public boolean isEnabled() {
		return uploadUrl != null;
	}

	/**
	 * Re-hosts every source URL.
	 * @param sources key -> source URL
	 * @return key -> link to serve. Re-hosted link on success, source URL if Zipline is disabled or the upload failed.
	 * Keys whose source could not be downloaded are left out.
	 */
	public CompletableFuture<Map<String, String>> rehostAll(@NotNull Map<String, String> sources) {
		return rehostAll(sources, EXPIRES_IN);
	}

	/**
	 * @param expiresIn how long the uploaded files stay available, e.g. "14d"
	 * @see #rehostAll(Map)
	 */
	public CompletableFuture<Map<String, String>> rehostAll(@NotNull Map<String, String> sources, @NotNull String expiresIn) {
		if (!isEnabled()) return CompletableFuture.completedFuture(sources);

		Map<String, String> result = new HashMap<>();
		CompletableFuture<?>[] futures = sources.entrySet().stream()
			.map(e -> rehost(e.getValue(), expiresIn)
				.thenAccept(url -> {
					if (url != null) synchronized (result) { result.put(e.getKey(), url); }
				}))
			.toArray(CompletableFuture[]::new);

		return CompletableFuture.allOf(futures).thenApply(_ -> result);
	}

	/**
	 * Downloads the file and uploads it to Zipline.
	 * @return Zipline link; source URL if upload failed; null if the source could not be downloaded.
	 */
	public CompletableFuture<String> rehost(@NotNull String sourceUrl) {
		return rehost(sourceUrl, EXPIRES_IN);
	}

	/**
	 * @param expiresIn how long the uploaded file stays available, e.g. "14d"
	 * @see #rehost(String)
	 */
	public CompletableFuture<String> rehost(@NotNull String sourceUrl, @NotNull String expiresIn) {
		if (!isEnabled()) return CompletableFuture.completedFuture(sourceUrl);

		CompletableFuture<String> future = new CompletableFuture<>();
		Request download = new Request.Builder().url(sourceUrl).get().build();
		client.newCall(download).enqueue(new Callback() {
			@Override
			public void onFailure(@NotNull Call call, @NotNull IOException ex) {
				log.warn("Failed to download media for re-hosting: {}", sourceUrl, ex);
				future.complete(null);
			}

			@Override
			public void onResponse(@NotNull Call call, @NotNull Response response) {
				try (response) {
					ResponseBody body = response.body();
					if (!response.isSuccessful() || body.contentLength() > MAX_SIZE) {
						log.debug("Media not available for re-hosting ({}): {}", response.code(), sourceUrl);
						future.complete(null);
						return;
					}
					byte[] data = body.bytes();
					upload(data, body.contentType(), fileName(sourceUrl), expiresIn, false)
						.thenAccept(url -> future.complete(url == null ? sourceUrl : url));
				} catch (IOException ex) {
					log.warn("Failed to read media for re-hosting: {}", sourceUrl, ex);
					future.complete(null);
				}
			}
		});
		return future;
	}

	/**
	 * Uploads a ticket transcript under a random UUID name, so the link can't be guessed.
	 * @return Zipline link; null if disabled or the upload failed.
	 */
	public CompletableFuture<String> uploadTranscript(byte[] html) {
		if (!isEnabled()) return CompletableFuture.completedFuture(null);
		// Generic name - the original contains the encoded channel ID
		return upload(html, HTML, "transcript.html", transcriptExpiresIn, true);
	}

	public @NotNull String getTranscriptExpiresIn() {
		return transcriptExpiresIn;
	}

	/**
	 * @param randomName use an unguessable UUID file name, regardless of the server's default name format
	 */
	private CompletableFuture<String> upload(byte[] data, @Nullable MediaType contentType, @NotNull String fileName,
											 @NotNull String expiresIn, boolean randomName) {
		assert uploadUrl != null && token != null;

		RequestBody body = new MultipartBody.Builder()
			.setType(MultipartBody.FORM)
			.addFormDataPart("file", fileName, RequestBody.create(data, contentType))
			.build();

		Request.Builder builder = new Request.Builder()
			.url(uploadUrl)
			.header("Authorization", token)
			.header("x-zipline-deletes-at", expiresIn)	// Zipline v4
			.header("Expires-At", expiresIn);				// Zipline v3
		if (randomName) {
			builder.header("x-zipline-format", "uuid")	// Zipline v4
				.header("Format", "UUID");					// Zipline v3
		}
		Request request = builder.post(body).build();

		CompletableFuture<String> future = new CompletableFuture<>();
		client.newCall(request).enqueue(new Callback() {
			@Override
			public void onFailure(@NotNull Call call, @NotNull IOException ex) {
				log.warn("Zipline upload failed: {}", ex.getMessage());
				future.complete(null);
			}

			@Override
			public void onResponse(@NotNull Call call, @NotNull Response response) {
				try (response) {
					String text = response.body().string();
					if (!response.isSuccessful()) {
						log.warn("Zipline upload failed ({}): {}", response.code(), text);
						future.complete(null);
						return;
					}
					future.complete(parseUrl(text));
				} catch (Exception ex) {
					log.warn("Failed to parse Zipline response", ex);
					future.complete(null);
				}
			}
		});
		return future;
	}

	/**
	 * v4: {"files":[{"id":..,"url":".."}]}, v3: {"files":[".."]}
	 */
	@Nullable
	private static String parseUrl(String text) {
		JSONArray files = new JSONObject(text).optJSONArray("files");
		if (files == null || files.isEmpty()) return null;
		Object first = files.get(0);
		if (first instanceof JSONObject obj) return obj.optString("url", null);
		return first.toString();
	}

	private static String fileName(String url) {
		String path = URI.create(url).getPath();
		return path.substring(path.lastIndexOf('/') + 1);
	}

}
