package dev.fileeditor.votl.metrics.timeseries;

import java.time.Instant;

public class SystemData extends DataStore<SystemData.SystemRecord> {
	public synchronized void addRecord(double processCpu, double systemCpu, long heapUsed, long heapCommitted, long nonHeapUsed,
									   int threads, long gcCount, long gcTime, long gcMaxPause) {
		removeFirst();
		records.addLast(new SystemRecord(Instant.now(), processCpu, systemCpu, heapUsed, heapCommitted, nonHeapUsed,
			threads, gcCount, gcTime, gcMaxPause));
	}

	public class SystemRecord extends DataRecord {
		// CPU in % of all cores, -1 if unavailable
		public double processCpu;
		public double systemCpu;
		// Memory in bytes
		public long heapUsed;
		public long heapCommitted;
		public long nonHeapUsed;
		public int threads;
		// GC pauses during the interval, time in ms
		public long gcCount;
		public long gcTime;
		public long gcMaxPause;

		SystemRecord(Instant timestamp, double processCpu, double systemCpu, long heapUsed, long heapCommitted, long nonHeapUsed,
					 int threads, long gcCount, long gcTime, long gcMaxPause) {
			this.timestamp = timestamp.toEpochMilli();
			this.processCpu = processCpu;
			this.systemCpu = systemCpu;
			this.heapUsed = heapUsed;
			this.heapCommitted = heapCommitted;
			this.nonHeapUsed = nonHeapUsed;
			this.threads = threads;
			this.gcCount = gcCount;
			this.gcTime = gcTime;
			this.gcMaxPause = gcMaxPause;
		}
	}
}
