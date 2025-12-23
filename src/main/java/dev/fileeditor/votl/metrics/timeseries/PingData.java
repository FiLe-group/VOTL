package dev.fileeditor.votl.metrics.timeseries;

import java.time.Instant;

public class PingData extends DataStore<PingData.PingRecord> {
	public synchronized void addRecord(long webSocketPing, long restPing) {
		removeFirst();
		records.addLast(new PingRecord(Instant.now(), webSocketPing, restPing));
	}

	public class PingRecord extends DataRecord {
		public long webSocketPing;
		public long restPing;

		PingRecord(Instant timestamp, long webSocketPing, long restPing) {
			this.timestamp = timestamp.toEpochMilli();
			this.webSocketPing = webSocketPing;
			this.restPing = restPing;
		}
	}
}
