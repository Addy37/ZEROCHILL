package com.webapp.crazyshit;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.LongSupplier;

/** Small session scheduler state, independent of provider request configuration and taste. */
final class ShitTokAdmissionHealth {
    private final LongSupplier clock;
    private final Map<String, State> sources = new LinkedHashMap<>();
    private static final class State { int failures, windows; long retryAt; double cost; }
    ShitTokAdmissionHealth(LongSupplier clock) { this.clock = clock; }
    synchronized void result(String source, boolean accepted, boolean error, long elapsed) {
        State state = sources.computeIfAbsent(source, ignored -> new State());
        while (sources.size() > 16) sources.remove(sources.keySet().iterator().next());
        double cost = Math.min(3, Math.max(0, elapsed) / 1000.0) + (accepted ? -1 : 1);
        state.cost = state.cost * .7 + cost * .3;
        if (accepted) { state.failures = state.windows = 0; state.retryAt = 0; }
        else if (error && ++state.failures >= 3) {
            state.failures = 0;
            state.windows = Math.min(5, state.windows + 1);
            state.retryAt = clock.getAsLong() + Math.min(60_000L, 5_000L << (state.windows - 1));
        }
    }
    synchronized boolean allowed(String source) {
        State state = sources.get(source);
        return state == null || clock.getAsLong() >= state.retryAt;
    }
    synchronized double cost(String source) {
        State state = sources.get(source); return state == null ? 0 : state.cost;
    }
    synchronized String summary() {
        State state = sources.get("bunkr");
        return "bunkrLaneLimit=1 bunkrBackoffMs=" + (state == null ? 0 : Math.max(0, state.retryAt - clock.getAsLong()));
    }
    synchronized void reset() { sources.clear(); }
}
