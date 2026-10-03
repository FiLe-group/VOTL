package dev.fileeditor.votl.utils;

import java.io.IOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.time.Duration;
import java.time.Instant;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

import dev.fileeditor.votl.utils.exception.FormatterException;
import dev.fileeditor.votl.utils.file.FileManager;
import dev.fileeditor.votl.utils.message.TimeUtil;
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

	private final @Nullable String baseUrl;
	private final @Nullable String uploadUrl;
	private final @Nullable String token;
	private final @NotNull String transcriptExpiresIn;
	// Same value as a duration, to display the expiry date. Null if it can't be parsed.
	private final @Nullable Duration transcriptExpiry;

	// Avatars get a fixed name per period of this length, so each is uploaded once per period and reused.
	// They expire this much later than transcripts, so a reused avatar always outlives the transcript showing it.
	private static final Duration AVATAR_REUSE = Duration.ofDays(7);

	public ZiplineUtil(@NotNull FileManager fileManager) {
		var baseUrl = fileManager.getNullableString("config", "zipline-url");
		var token = fileManager.getNullableString("config", "zipline-token");
		if (baseUrl == null || token == null) {
			this.baseUrl = null;
			this.uploadUrl = null;
			this.token = null;
		} else {
			this.baseUrl = baseUrl.replaceAll("/+$", "");
			this.uploadUrl = this.baseUrl + "/api/upload";
			this.token = token;
		}
		this.transcriptExpiresIn = Objects.requireNonNullElse(
			fileManager.getNullableString("config", "zipline-transcript-expiry"), TRANSCRIPT_EXPIRES_IN
		);
		this.transcriptExpiry = parseExpiry(transcriptExpiresIn);
	}

	@Nullable
	private Duration parseExpiry(String expiresIn) {
		try {
			Duration duration = TimeUtil.stringToDuration(expiresIn, true);
			if (!duration.isZero()) return duration;
		} catch (FormatterException ignored) {}
		log.warn("Can't parse zipline-transcript-expiry \"{}\" (use e.g. 90d or 12w), the expiry date won't be shown.", expiresIn);
		return null;
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
		return rehost(sourceUrl, expiresIn, null);
	}

	/**
	 * @param fixedName file name without extension, if a file with it already exists its link is returned
	 */
	private CompletableFuture<String> rehost(@NotNull String sourceUrl, @NotNull String expiresIn, @Nullable String fixedName) {
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
					upload(data, body.contentType(), fileName(sourceUrl), expiresIn, false, fixedName)
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
		return upload(html, HTML, "transcript.html", transcriptExpiresIn, true, null)
			.thenApply(url -> {
				if (url != null) log.debug("Uploaded ticket transcript to Zipline: {}", url);
				return url;
			});
	}

	public @NotNull String getTranscriptExpiresIn() {
		return transcriptExpiresIn;
	}

	/**
	 * Uploads transcript avatars. Each avatar gets a fixed name for the current period,
	 * so one already on Zipline is reused instead of uploaded again - across restarts too.
	 * @param urls Discord avatar URLs
	 * @return avatar URL -> Zipline link, for avatars that were uploaded
	 */
	public CompletableFuture<Map<String, String>> rehostAvatars(@NotNull Collection<String> urls) {
		if (!isEnabled() || urls.isEmpty()) return CompletableFuture.completedFuture(Map.of());
		if (transcriptExpiry == null) {
			// Can't tell when a reused link would expire - upload each time
			return rehostAll(urls.stream().distinct().collect(Collectors.toMap(u -> u, u -> u)), transcriptExpiresIn)
				.thenApply(links -> {
					links.entrySet().removeIf(e -> e.getKey().equals(e.getValue())); // failed, kept Discord link
					return links;
				});
		}

		// Uploaded at time u in period p, reused until the period ends (< u + AVATAR_REUSE),
		// so transcripts reusing it expire before u + AVATAR_REUSE + transcriptExpiry - the avatar's expiry
		final long period = Instant.now().getEpochSecond() / AVATAR_REUSE.toSeconds();
		final String expiresIn = toDays(transcriptExpiry.plus(AVATAR_REUSE));
		Map<String, String> result = new HashMap<>();
		CompletableFuture<?>[] futures = urls.stream().distinct()
			.map(url -> rehostAvatar(url, period, expiresIn)
				.thenAccept(link -> {
					if (link != null) synchronized (result) { result.put(url, link); }
				}))
			.toArray(CompletableFuture[]::new);
		return CompletableFuture.allOf(futures).thenApply(_ -> result);
	}

	private CompletableFuture<String> rehostAvatar(String url, long period, String expiresIn) {
		assert baseUrl != null;
		// Hash of the avatar URL (contains user ID and image hash) - same avatar, same name
		final String name = "avatar-%s-%d".formatted(sha256(url).substring(0, 16), period);
		final String existing = baseUrl + "/raw/" + name + extension(fileName(url));
		return exists(existing).thenCompose(exists -> exists
			? CompletableFuture.completedFuture(existing)
			: rehost(url, expiresIn, name).thenApply(link -> url.equals(link) ? null : link) // upload failed
		);
	}

	/**
	 * HEAD request, without downloading the file.
	 */
	private CompletableFuture<Boolean> exists(String url) {
		CompletableFuture<Boolean> future = new CompletableFuture<>();
		client.newCall(new Request.Builder().url(url).head().build()).enqueue(new Callback() {
			@Override
			public void onFailure(@NotNull Call call, @NotNull IOException ex) {
				future.complete(false);
			}

			@Override
			public void onResponse(@NotNull Call call, @NotNull Response response) {
				try (response) {
					future.complete(response.isSuccessful());
				}
			}
		});
		return future;
	}

	/**
	 * Zipline accepts relative expiry like "97d".
	 */
	private static String toDays(Duration duration) {
		return Math.max(1, (duration.toSeconds() + 86399) / 86400) + "d";
	}

	/**
	 * @return when a transcript uploaded now expires, null if unknown
	 */
	public @Nullable Instant getTranscriptExpiresAt() {
		return transcriptExpiry == null ? null : Instant.now().plus(transcriptExpiry);
	}

	/**
	 * @param randomName use an unguessable UUID file name, regardless of the server's default name format
	 * @param fixedName file name without extension (Zipline v4). If it is taken, the existing file's link is returned.
	 */
	private CompletableFuture<String> upload(byte[] data, @Nullable MediaType contentType, @NotNull String fileName,
											 @NotNull String expiresIn, boolean randomName, @Nullable String fixedName) {
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
		if (fixedName != null) builder.header("x-zipline-filename", fixedName);
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
						// Uploaded meanwhile, e.g. by a transcript generated at the same time
						if (fixedName != null && text.contains("already exists")) {
							future.complete(baseUrl + "/raw/" + fixedName + extension(fileName));
							return;
						}
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
	 * @return link to the raw file
	 */
	@Nullable
	static String parseUrl(String text) {
		JSONArray files = new JSONObject(text).optJSONArray("files");
		if (files == null || files.isEmpty()) return null;
		Object first = files.get(0);
		if (first instanceof JSONObject obj) {
			String url = obj.optString("url", null);
			return url == null ? null : toRawUrl(url, "/raw/");
		}
		return toRawUrl(first.toString(), "/r/");
	}

	/**
	 * The returned link (default /u/) can redirect to Zipline's view page - text files (transcripts) are
	 * shown as code, and images break when embedded. The raw route always serves the file itself.
	 * @param rawRoute v4: /raw/, v3: /r/
	 */
	static String toRawUrl(String url, String rawRoute) {
		try {
			URI uri = URI.create(url);
			String path = uri.getRawPath();
			if (uri.getScheme() == null || uri.getRawAuthority() == null || path == null) return url;
			String name = path.substring(path.lastIndexOf('/') + 1);
			if (name.isEmpty()) return url;
			return uri.getScheme() + "://" + uri.getRawAuthority() + rawRoute + name;
		} catch (IllegalArgumentException ex) {
			return url;
		}
	}

	/**
	 * @return extension with the dot, or empty
	 */
	private static String extension(String fileName) {
		int dot = fileName.lastIndexOf('.');
		return dot < 0 ? "" : fileName.substring(dot);
	}

	private static String sha256(String text) {
		try {
			return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(text.getBytes(StandardCharsets.UTF_8)));
		} catch (NoSuchAlgorithmException ex) {
			throw new IllegalStateException(ex);
		}
	}

	private static String fileName(String url) {
		String path = URI.create(url).getPath();
		return path.substring(path.lastIndexOf('/') + 1);
	}

}
