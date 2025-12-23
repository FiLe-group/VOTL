package dev.fileeditor.votl.metrics.timeseries;

import java.util.ArrayDeque;
import java.util.Deque;

import static dev.fileeditor.votl.metrics.Metrics.TIMESERIES_INTERVAL;

public class DataStore<T extends DataRecord> {
	protected static final int MAX_RECORDS = (24 * 60) / TIMESERIES_INTERVAL; // 24 hours with 5-minute intervals
	protected final Deque<T> records = new ArrayDeque<>(MAX_RECORDS);

	public synchronized void removeFirst() {
		if (records.size() >= MAX_RECORDS) {
			records.pollFirst(); // Remove the oldest record
		}
	}

	public synchronized Deque<T> getRecords() {
		return new ArrayDeque<>(records); // Return a copy to avoid external modification
	}
}
