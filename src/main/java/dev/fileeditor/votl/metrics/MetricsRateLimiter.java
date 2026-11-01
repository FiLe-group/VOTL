package dev.fileeditor.votl.metrics;

import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.requests.RestRateLimiter;
import net.dv8tion.jda.api.requests.Route;
import net.dv8tion.jda.api.requests.SequentialRestRateLimiter;
import okhttp3.Response;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * JDA's default rate limiter, counting REST requests, 429 responses and queued requests.
 * JDA retries a 429 by itself, so it never reaches the {@code RestAction} or {@code HttpRequestEvent}.
 */
public class MetricsRateLimiter implements RestRateLimiter {

	private static final Set<Work> queue = ConcurrentHashMap.newKeySet();
	private static final AtomicInteger queuePeak = new AtomicInteger();

	private final RestRateLimiter delegate;

	public MetricsRateLimiter(@NotNull RateLimitConfig config) {
		this.delegate = new SequentialRestRateLimiter(config);
	}

	@Override
	public void enqueue(@NotNull Work task) {
		CountingWork work = new CountingWork(task);
		queue.add(work);
		queuePeak.accumulateAndGet(queue.size(), Math::max);
		delegate.enqueue(work);
	}

	@Override
	public void stop(boolean shutdown, @NotNull Runnable callback) {
		delegate.stop(shutdown, callback);
	}

	@Override
	public boolean isStopped() {
		return delegate.isStopped();
	}

	@Override
	public int cancelRequests() {
		return delegate.cancelRequests();
	}

	/** Requests waiting to be sent or retried. */
	public static int getQueueSize() {
		// Canceled requests are dropped by the rate limiter without being executed
		queue.removeIf(w -> w.isDone() || w.isCancelled() || w.isSkipped());
		return queue.size();
	}

	/** Most requests queued at once since the last call. */
	public static int takeQueuePeak() {
		return queuePeak.getAndSet(getQueueSize());
	}

	private record CountingWork(Work task) implements Work {
		@NotNull
		@Override
		public Route.CompiledRoute getRoute() {
			return task.getRoute();
		}

		@NotNull
		@Override
		public JDA getJDA() {
			return task.getJDA();
		}

		@Nullable
		@Override
		public Response execute() {
			Response response = null;
			try {
				response = task.execute();
				return response;
			} finally {
				Metrics.restRequests.inc();
				if (response != null && response.code() == 429) {
					Route route = task.getRoute().getBaseRoute();
					Metrics.restRateLimited.labelValue(route.getMethod()+" "+route.getRoute()).inc();
				}
				if (task.isDone()) queue.remove(this);
			}
		}

		@Override
		public boolean isSkipped() {
			return task.isSkipped();
		}

		@Override
		public boolean isDone() {
			return task.isDone();
		}

		@Override
		public boolean isPriority() {
			return task.isPriority();
		}

		@Override
		public boolean isCancelled() {
			return task.isCancelled();
		}

		@Override
		public void cancel() {
			task.cancel();
		}
	}

}
