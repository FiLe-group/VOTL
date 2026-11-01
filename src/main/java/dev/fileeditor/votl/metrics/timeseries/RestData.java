package dev.fileeditor.votl.metrics.timeseries;

import java.time.Instant;

public class RestData extends DataStore<RestData.RestRecord> {
	public synchronized void addRecord(int requests, int rateLimited, int queuePeak, int queueNow) {
		removeFirst();
		records.addLast(new RestRecord(Instant.now(), requests, rateLimited, queuePeak, queueNow));
	}

	public class RestRecord extends DataRecord {
		// During the interval
		public int requests;
		public int rateLimited;
		public int queuePeak;
		// At the end of the interval
		public int queueNow;

		RestRecord(Instant timestamp, int requests, int rateLimited, int queuePeak, int queueNow) {
			this.timestamp = timestamp.toEpochMilli();
			this.requests = requests;
			this.rateLimited = rateLimited;
			this.queuePeak = queuePeak;
			this.queueNow = queueNow;
		}
	}
}
