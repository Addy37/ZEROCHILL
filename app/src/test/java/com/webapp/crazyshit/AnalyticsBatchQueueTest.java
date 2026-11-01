package com.webapp.crazyshit;

import org.junit.Test;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.PriorityQueue;
import static org.junit.Assert.*;

public class AnalyticsBatchQueueTest {
    private static final class Task implements Comparable<Task>, AnalyticsBatchQueue.Cancellation {
        long due; Runnable runnable; boolean cancelled;
        public int compareTo(Task other) { return Long.compare(due, other.due); }
        public void cancel() { cancelled = true; }
    }
    private static final class Clock implements AnalyticsBatchQueue.Scheduler {
        long now;
        final PriorityQueue<Task> tasks = new PriorityQueue<>();
        public AnalyticsBatchQueue.Cancellation schedule(Runnable runnable, long delay) {
            Task task = new Task(); task.due = now + delay; task.runnable = runnable;
            tasks.add(task); return task;
        }
        void advance(long delay) {
            long target = now + delay;
            while (!tasks.isEmpty() && tasks.peek().due <= target) {
                Task task = tasks.remove(); now = task.due;
                if (!task.cancelled) task.runnable.run();
            }
            now = target;
        }
    }
    @Test public void flushesBySizeWithoutDroppingAnyMetricsOrLeavingTimerRequests() {
        Clock clock = new Clock(); List<List<String>> calls = new ArrayList<>();
        AnalyticsBatchQueue queue = new AnalyticsBatchQueue(clock, events -> { calls.add(events); return 204; });
        for (int i = 0; i < 300; i++) queue.add("metric-" + i);
        assertEquals(10, calls.size());
        List<String> flattened = new ArrayList<>();
        for (List<String> call : calls) { assertEquals(30, call.size()); flattened.addAll(call); }
        for (int i = 0; i < 300; i++) assertEquals("metric-" + i, flattened.get(i));
        clock.advance(120_000); assertEquals(10, calls.size());
    }
    @Test public void startupAndSparseEventsShareOneTimeFlush() {
        Clock clock = new Clock(); List<List<String>> calls = new ArrayList<>();
        AnalyticsBatchQueue queue = new AnalyticsBatchQueue(clock, events -> { calls.add(events); return 204; });
        for (String metric : Arrays.asList("app_open", "app_version", "device_model", "device_manufacturer", "android_version")) queue.add(metric);
        clock.advance(59_999); assertTrue(calls.isEmpty());
        queue.add("section"); clock.advance(1);
        assertEquals(1, calls.size()); assertEquals(6, calls.get(0).size());
        clock.advance(120_000); assertEquals(1, calls.size());
    }
    @Test public void retriesSameBatchOnceAfterDelayThenCoolsDown() {
        Clock clock = new Clock(); List<List<String>> calls = new ArrayList<>();
        AnalyticsBatchQueue queue = new AnalyticsBatchQueue(clock, events -> { calls.add(events); return 503; });
        queue.add("event-with-rotating-keys"); clock.advance(60_000);
        for (int i = 0; i < 30; i++) queue.add("later-" + i);
        clock.advance(59_999); assertEquals(1, calls.size());
        clock.advance(1); assertEquals(2, calls.size()); assertEquals(calls.get(0), calls.get(1));
        clock.advance(299_999); assertEquals(2, calls.size());
        clock.advance(1); assertEquals(3, calls.size()); assertEquals(30, calls.get(2).size());
        assertFalse(calls.get(2).contains("event-with-rotating-keys"));
    }
    @Test public void retriesNetworkFailureAndRecoversWithoutReplayingSuccessfulBatch() {
        Clock clock = new Clock(); List<List<String>> calls = new ArrayList<>();
        AnalyticsBatchQueue queue = new AnalyticsBatchQueue(clock, events -> {
            calls.add(events); if (calls.size() == 1) throw new IOException("offline"); return 204;
        });
        queue.add("first"); clock.advance(60_000); queue.add("second");
        clock.advance(60_000); assertEquals(calls.get(0),calls.get(1));
        clock.advance(60_000); assertEquals(Arrays.asList("second"), calls.get(2));
        clock.advance(600_000); assertEquals(3,calls.size());
    }
    @Test public void validationErrorsAreNotRetried() {
        Clock clock = new Clock(); List<List<String>> calls = new ArrayList<>();
        AnalyticsBatchQueue queue = new AnalyticsBatchQueue(clock, events -> {calls.add(events); return 400;});
        queue.add("bad"); clock.advance(600_000); assertEquals(1,calls.size());
    }
    @Test public void emptyQueueNeverInvokesTransport() {
        Clock clock = new Clock();
        new AnalyticsBatchQueue(clock, events -> {fail("empty queue sent a request"); return 204;});
        clock.advance(600_000);
    }
}
