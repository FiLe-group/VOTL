package dev.fileeditor.votl.servlet.utils;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import dev.fileeditor.oauth2.state.StateController;
import dev.fileeditor.votl.utils.RandomUtil;

import java.util.concurrent.TimeUnit;

public class AuthStateController implements StateController {
	// cache
	private static final Cache<String, String> states = Caffeine.newBuilder()
		.expireAfterWrite(5, TimeUnit.MINUTES)
		.build();

	@Override
	public String generateNewState(String redirectUri) {
		String state = randomState();
		states.put(state, redirectUri);
		return state;
	}

	@Override
	public String consumeState(String state) {
		String uri = states.getIfPresent(state);
		if (uri != null)
			states.invalidate(state);
		return uri;
	}

	private static String randomState() {
		return RandomUtil.randomString(20);
	}
}
