package dev.fileeditor.votl.metrics.timeseries;

import java.time.Instant;

public class UsageData extends DataStore<UsageData.UsageRecord> {
	public synchronized void addRecord(long commands, long interactions) {
		removeFirst();
		records.addLast(new UsageRecord(Instant.now(), commands, interactions));
	}

	public class UsageRecord extends DataRecord {
		// Received during the interval
		public long commands;
		public long interactions;

		UsageRecord(Instant timestamp, long commands, long interactions) {
			this.timestamp = timestamp.toEpochMilli();
			this.commands = commands;
			this.interactions = interactions;
		}
	}
}
