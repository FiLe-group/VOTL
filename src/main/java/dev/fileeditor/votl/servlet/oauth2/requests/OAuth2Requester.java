/*
 * Copyright 2016-2018 John Grosh (jagrosh) & Kaidan Gustave (TheMonitorLizard)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package dev.fileeditor.votl.servlet.oauth2.requests;

import java.io.IOException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.function.Consumer;

import dev.fileeditor.votl.servlet.oauth2.OkHttpResponseFuture;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.OkHttpClient;
import okhttp3.RequestBody;
import okhttp3.Response;

/**
 * @author Kaidan Gustave
 */
public class OAuth2Requester {
	public static final Logger log = LoggerFactory.getLogger(OAuth2Requester.class);
	protected static final String USER_AGENT = "VOTL Bot | JDA-Utils Oauth2";
	protected static final RequestBody EMPTY_BODY = RequestBody.create(new byte[0]);

	private final OkHttpClient httpClient;

	public OAuth2Requester(OkHttpClient httpClient) {
		this.httpClient = httpClient;
	}

	<T> void submitAsync(OAuth2Action<T> request, Consumer<T> success, Consumer<Throwable> failure) {
		httpClient.newCall(request.buildRequest()).enqueue(new Callback() {
			@Override
			public void onResponse(Call call, Response response) {
				try (response) {
					T value = request.handle(response);
					logSuccessfulRequest(request);

					// Handle end-user exception differently
					try {
						if (value != null)
							success.accept(value);
					} catch (Throwable t) {
						log.error("OAuth2Action success callback threw an exception!", t);
					}
				} catch (Throwable t) {
					// Handle end-user exception differently
					try {
						failure.accept(t);
					} catch (Throwable t1) {
						log.error("OAuth2Action success callback threw an exception!", t1);
					}
				}
			}

			@Override
			public void onFailure(Call call, IOException e) {
				log.error("Requester encountered an error when submitting a request!", e);
			}
		});
	}

	<T> T submitSync(OAuth2Action<T> request) throws IOException {
		try(Response response = httpClient.newCall(request.buildRequest()).execute()) {
			T value = request.handle(response);
			logSuccessfulRequest(request);
			return value;
		}
	}

	<T> CompletableFuture<T> returnAsync(OAuth2Action<T> request) {
		OkHttpResponseFuture callback = new OkHttpResponseFuture();
		httpClient.newCall(request.buildRequest()).enqueue(callback);

		return callback.future.thenApply(response -> {
			try (response) {
				T value = request.handle(response);
				logSuccessfulRequest(request);

				if (value == null)
					throw new NullPointerException("User not found.");
				return value;
			} catch (Throwable t) {
				throw new CompletionException(t);
			}
		});
	}

	private static <T> void logSuccessfulRequest(OAuth2Action<T> request) {
		log.debug("Got a response for {} - {}\nHeaders: {}", request.getMethod(),
			request.getUrl(), request.getHeaders());
	}
}