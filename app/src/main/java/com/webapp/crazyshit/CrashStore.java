package com.webapp.crazyshit;

import android.util.AtomicFile;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.File;
import java.io.FileOutputStream;
import java.util.ArrayList;

/** Short synchronized local operations only. Network and diagnostic locks never enter this lock. */
final class CrashStore {
    static final int MAX_REPORTS = 3;
    static final long DAY = 24 * 60 * 60 * 1000L;
    private final AtomicFile file;
    private long lastCreated;

    CrashStore(File file) {
        this.file = new AtomicFile(file);
    }

    synchronized ArrayList<CrashReport> read() {
        ArrayList<CrashReport> rows = new ArrayList<>();
        lastCreated = 0;
        try {
            if (!file.getBaseFile().exists()) return rows;
            if (file.getBaseFile().length() > 131_072) throw new IllegalArgumentException();
            JSONObject data =
                    new JSONObject(
                            new String(file.readFully(), java.nio.charset.StandardCharsets.UTF_8));
            lastCreated = data.optLong("created");
            JSONArray reports = data.getJSONArray("reports");
            for (int i = 0; i < Math.min(MAX_REPORTS, reports.length()); i++)
                try {
                    rows.add(CrashReport.parse(reports.getJSONObject(i)));
                } catch (Exception ignored) {
                }
        } catch (Exception ignored) {
            file.delete();
        }
        return rows;
    }

    synchronized void capture(CrashReport incoming) throws Exception {
        ArrayList<CrashReport> rows = read();
        for (CrashReport r : rows)
            if (r.fingerprint.equals(incoming.fingerprint)
                    && r.version.equals(incoming.version)
                    && incoming.lastAt >= r.lastAt
                    && incoming.lastAt - r.lastAt < DAY) {
                incoming.id = r.id;
                incoming.firstAt = r.firstAt;
                incoming.repeats = Math.min(1_000_000, r.repeats + 1);
                incoming.threadId = r.threadId;
                incoming.reported = r.reported;
                incoming.deliveredAt = r.deliveredAt;
                incoming.diagnosticsSent = r.diagnosticsSent;
                rows.remove(r);
                break;
            }
        rows.add(incoming);
        while (rows.size() > MAX_REPORTS) rows.remove(0);
        write(rows);
    }

    synchronized boolean mayCreate(long now) {
        read();
        return lastCreated == 0 || now - lastCreated >= DAY;
    }

    synchronized void linked(String id, String thread, long now) throws Exception {
        ArrayList<CrashReport> rows = read();
        for (CrashReport r : rows)
            if (r.id.equals(id)) {
                r.threadId = thread;
                lastCreated = now;
            }
        write(rows);
    }

    synchronized void completed(String id, int count, long now) throws Exception {
        ArrayList<CrashReport> rows = read();
        for (CrashReport r : rows)
            if (r.id.equals(id)) {
                r.reported = Math.max(r.reported, count);
                r.deliveredAt = now;
                r.diagnosticsSent = true;
                if (!r.pending()) {
                    r.summary = "";
                    r.diagnostics = "";
                } // Keep only a bounded dedupe receipt after delivery.
            }
        write(rows);
    }

    private void write(ArrayList<CrashReport> rows) throws Exception {
        JSONArray data = new JSONArray();
        for (CrashReport r : rows) data.put(r.json());
        byte[] bytes =
                new JSONObject()
                        .put("created", lastCreated)
                        .put("reports", data)
                        .toString()
                        .getBytes(java.nio.charset.StandardCharsets.UTF_8);
        if (bytes.length > 131_072) throw new IllegalStateException("Crash storage limit exceeded");
        FileOutputStream stream = null;
        try {
            stream = file.startWrite();
            stream.write(bytes);
            file.finishWrite(stream);
        } catch (Exception error) {
            if (stream != null) file.failWrite(stream);
            throw error;
        }
    }
}
