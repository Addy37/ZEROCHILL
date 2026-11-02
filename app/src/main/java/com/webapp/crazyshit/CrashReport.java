package com.webapp.crazyshit;

import org.json.JSONObject;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.List;

final class CrashReport {
    String id, fingerprint, version, androidVersion, device, summary, diagnostics, threadId = "";
    long firstAt, lastAt, deliveredAt;
    int repeats = 1, reported;
    boolean diagnosticsSent;

    boolean pending() {
        return repeats > reported;
    }

    String marker() {
        return "[Crash " + fingerprint.substring(0, 12) + " " + id.substring(0, 8) + "]";
    }

    String firstMessage() {
        return CrashSanitizer.bound(CrashSanitizer.clean(summary), 1740)
                + "\nRepeated "
                + repeats
                + " times\nLast seen: "
                + java.time.Instant.ofEpochMilli(lastAt)
                + "\n"
                + marker();
    }

    List<String> chunks() {
        String safe = CrashSanitizer.bound(CrashSanitizer.clean(diagnostics), 5_550);
        List<String> chunks = new ArrayList<>();
        int total = Math.max(1, (safe.length() + 1849) / 1850), offset = 0;
        while (offset < safe.length() && chunks.size() < 3) {
            String chunk = CrashSanitizer.bound(safe.substring(offset), 1850);
            chunks.add(
                    "Crash diagnostics "
                            + (chunks.size() + 1)
                            + "/"
                            + total
                            + " "
                            + marker()
                            + "\n"
                            + chunk);
            offset += chunk.length();
        }
        return chunks;
    }

    static CrashReport capture(
            Throwable error,
            String thread,
            String screen,
            String version,
            int code,
            String revision,
            String android,
            String device,
            long now,
            String cachedDiagnostics)
            throws Exception {
        CrashReport r = new CrashReport();
        r.id = java.util.UUID.randomUUID().toString();
        r.fingerprint = fingerprint(error);
        r.version = CrashSanitizer.clean(version);
        r.androidVersion = CrashSanitizer.clean(android);
        r.device = CrashSanitizer.clean(device);
        r.firstAt = r.lastAt = now;
        StringBuilder trace = new StringBuilder();
        Throwable cause = error;
        java.util.Set<Throwable> seen =
                java.util.Collections.newSetFromMap(new java.util.IdentityHashMap<>());
        for (int depth = 0;
                cause != null && depth < 4 && seen.add(cause);
                depth++, cause = cause.getCause()) {
            trace.append(depth == 0 ? "" : "Caused by: ")
                    .append(cause.getClass().getName())
                    .append('\n');
            StackTraceElement[] frames = cause.getStackTrace();
            for (int i = 0; i < Math.min(36, frames.length); i++)
                trace.append("  at ").append(frames[i]).append('\n');
            if (frames.length > 36) trace.append("  [remaining frames omitted]\n");
        }
        String title =
                screen.equals("NativeMainActivity")
                        ? "main screen"
                        : screen.replace("ZeroChill", "").replace("Activity", "");
        r.summary =
                CrashSanitizer.bound(
                        CrashSanitizer.clean(
                                "ZEROCHILL crashed in "
                                        + title
                                        + "\n"
                                        + error.getClass().getName()
                                        + ": "
                                        + CrashSanitizer.message(error)
                                        + "\nThread: "
                                        + CrashSanitizer.thread(thread)
                                        + "\nScreen: "
                                        + screen
                                        + "\nApp: "
                                        + version
                                        + " ("
                                        + code
                                        + ")\nSource: "
                                        + revision
                                        + "\nAndroid: "
                                        + android
                                        + "\nDevice: "
                                        + device
                                        + "\nCrash time: "
                                        + java.time.Instant.ofEpochMilli(now)
                                        + "\n"
                                        + CrashSanitizer.bound(trace.toString(), 600)),
                        1740);
        r.diagnostics =
                CrashSanitizer.bound(
                        CrashSanitizer.clean(
                                "Stack trace\n"
                                        + CrashSanitizer.bound(trace.toString(), 2800)
                                        + "\nSession diagnostics\n"
                                        + CrashSanitizer.bound(cachedDiagnostics, 2700)),
                        5700);
        return r;
    }

    static String fingerprint(Throwable error) throws Exception {
        StringBuilder stable = new StringBuilder(error.getClass().getName());
        StackTraceElement[] frames = error.getStackTrace();
        for (int i = 0; i < Math.min(6, frames.length); i++)
            stable.append('\n')
                    .append(frames[i].getClassName())
                    .append('.')
                    .append(frames[i].getMethodName());
        byte[] hash =
                MessageDigest.getInstance("SHA-256")
                        .digest(stable.toString().getBytes(StandardCharsets.UTF_8));
        StringBuilder out = new StringBuilder();
        for (byte b : hash) out.append(String.format(java.util.Locale.US, "%02x", b & 255));
        return out.toString();
    }

    JSONObject json() throws Exception {
        return new JSONObject()
                .put("id", id)
                .put("fingerprint", fingerprint)
                .put("version", version)
                .put("android", androidVersion)
                .put("device", device)
                .put("summary", CrashSanitizer.clean(summary))
                .put("diagnostics", CrashSanitizer.clean(diagnostics))
                .put("thread_id", threadId)
                .put("first", firstAt)
                .put("last", lastAt)
                .put("delivered", deliveredAt)
                .put("repeats", repeats)
                .put("reported", reported)
                .put("diagnostics_sent", diagnosticsSent);
    }

    static CrashReport parse(JSONObject o) throws Exception {
        CrashReport r = new CrashReport();
        r.id = java.util.UUID.fromString(o.getString("id")).toString();
        r.fingerprint = o.getString("fingerprint");
        if (!r.fingerprint.matches("[a-f0-9]{64}")) throw new IllegalArgumentException();
        r.version = CrashSanitizer.bound(CrashSanitizer.clean(o.getString("version")), 100);
        r.androidVersion = CrashSanitizer.bound(CrashSanitizer.clean(o.getString("android")), 100);
        r.device = CrashSanitizer.bound(CrashSanitizer.clean(o.getString("device")), 200);
        r.summary = CrashSanitizer.bound(CrashSanitizer.clean(o.getString("summary")), 1740);
        r.diagnostics =
                CrashSanitizer.bound(CrashSanitizer.clean(o.getString("diagnostics")), 5700);
        r.threadId = o.optString("thread_id");
        if (!r.threadId.isEmpty()) java.util.UUID.fromString(r.threadId);
        r.firstAt = o.getLong("first");
        r.lastAt = o.getLong("last");
        r.deliveredAt = o.optLong("delivered");
        r.repeats = Math.max(1, Math.min(1_000_000, o.getInt("repeats")));
        r.reported = Math.min(r.repeats, Math.max(0, o.optInt("reported")));
        r.diagnosticsSent = o.optBoolean("diagnostics_sent");
        return r;
    }
}
