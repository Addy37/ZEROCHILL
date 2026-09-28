package com.webapp.crazyshit;

import java.util.LinkedHashMap;
import java.util.Locale;

/** Temporary, process-local source trace for the device-test candidate. No media URLs or cookies. */
final class CoomerFansDiagnostics {
    private static final int MAX_QUERIES = 8;
    private static final LinkedHashMap<String, LinkedHashMap<String, String>> TRACES =
            new LinkedHashMap<>();
    private static String selected = "";

    private CoomerFansDiagnostics() { }

    static synchronized void select(String query) {
        selected = key(query);
        if (!selected.isEmpty()) {
            TRACES.remove(selected);
            trace(selected);
        }
    }

    static synchronized void put(String query, String stage, String value) {
        String id = key(query);
        if (id.isEmpty()) return;
        trace(id).put(stage, value == null ? "" : value);
    }

    static synchronized void increment(String query, String stage) {
        String id = key(query);
        if (id.isEmpty()) return;
        LinkedHashMap<String, String> values = trace(id);
        int count = 0;
        try { count = Integer.parseInt(values.get(stage)); }
        catch (Exception ignored) { }
        values.put(stage, String.valueOf(count + 1));
    }

    static synchronized String summary() {
        if (selected.isEmpty()) return "Open a creator gallery, then return here.";
        LinkedHashMap<String, String> trace = TRACES.get(selected);
        StringBuilder result = new StringBuilder("Creator: ").append(selected);
        if (trace == null || trace.isEmpty()) return result.append("\nNo source attempt recorded.").toString();
        for (java.util.Map.Entry<String, String> entry : trace.entrySet()) {
            result.append('\n').append(entry.getKey()).append(": ").append(entry.getValue());
        }
        return result.toString();
    }

    private static LinkedHashMap<String, String> trace(String key) {
        LinkedHashMap<String, String> result = TRACES.get(key);
        if (result == null) {
            result = new LinkedHashMap<>();
            TRACES.put(key, result);
            while (TRACES.size() > MAX_QUERIES) TRACES.remove(TRACES.keySet().iterator().next());
        }
        return result;
    }

    private static String key(String query) {
        return query == null ? "" : query.trim().toLowerCase(Locale.US);
    }
}
