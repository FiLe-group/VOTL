package dev.fileeditor.votl.servlet.oauth2;

import java.io.IOException;
import java.util.concurrent.CompletableFuture;

import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.Response;
import org.jetbrains.annotations.NotNull;

public class OkHttpResponseFuture implements Callback {
	public final CompletableFuture<Response> future = new CompletableFuture<>();

	public OkHttpResponseFuture() {}

	@Override
	public void onResponse(@NotNull Call call, @NotNull Response response) throws IOException {
		future.complete(response);
	}

	@Override
	public void onFailure(@NotNull Call call, @NotNull IOException e) {
		future.completeExceptionally(e);
	}
}
