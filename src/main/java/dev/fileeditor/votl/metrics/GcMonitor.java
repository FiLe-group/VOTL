package dev.fileeditor.votl.metrics;

import com.sun.management.GarbageCollectionNotificationInfo;

import javax.management.NotificationEmitter;
import javax.management.openmbean.CompositeData;
import java.lang.management.GarbageCollectorMXBean;
import java.lang.management.ManagementFactory;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Garbage collection pauses. Concurrent collector work (G1 Concurrent GC, ZGC cycles) runs beside the
 * application, so only stop-the-world pauses are counted.
 */
public final class GcMonitor {

	private static final AtomicLong maxPause = new AtomicLong();
	private static boolean installed = false;

	private GcMonitor() {}

	public static synchronized void install() {
		if (installed) return;
		for (GarbageCollectorMXBean bean : ManagementFactory.getGarbageCollectorMXBeans()) {
			if (!isPause(bean.getName()) || !(bean instanceof NotificationEmitter emitter)) continue;
			emitter.addNotificationListener((notification, _) -> {
				if (!GarbageCollectionNotificationInfo.GARBAGE_COLLECTION_NOTIFICATION.equals(notification.getType())) return;
				GarbageCollectionNotificationInfo info = GarbageCollectionNotificationInfo.from((CompositeData) notification.getUserData());
				maxPause.accumulateAndGet(info.getGcInfo().getDuration(), Math::max);
			}, null, null);
		}
		installed = true;
	}

	/** Pause count since start. */
	public static long getCount() {
		long count = 0;
		for (GarbageCollectorMXBean bean : ManagementFactory.getGarbageCollectorMXBeans()) {
			if (isPause(bean.getName())) count += Math.max(0, bean.getCollectionCount());
		}
		return count;
	}

	/** Pause time since start, in ms. */
	public static long getTime() {
		long time = 0;
		for (GarbageCollectorMXBean bean : ManagementFactory.getGarbageCollectorMXBeans()) {
			if (isPause(bean.getName())) time += Math.max(0, bean.getCollectionTime());
		}
		return time;
	}

	/** Longest pause since the last call, in ms. */
	public static long takeMaxPause() {
		return maxPause.getAndSet(0);
	}

	private static boolean isPause(String name) {
		return !name.contains("Concurrent") && !name.contains("Cycles");
	}

}
