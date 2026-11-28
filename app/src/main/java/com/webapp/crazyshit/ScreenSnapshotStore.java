package com.webapp.crazyshit;

import android.content.Context;
import android.util.AtomicFile;
import org.json.JSONObject;
import java.io.File;
import java.io.FileOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Bounded disk snapshots keep result lists out of Android's small saved-state transaction. */
final class ScreenSnapshotStore {
    private static final Object LOCK = new Object();
    private static final ExecutorService IO = Executors.newSingleThreadExecutor();
    private ScreenSnapshotStore() { }
    static String newId() { return UUID.randomUUID().toString(); }

    static void save(Context context, String id, JSONObject value) {
        Context app = context.getApplicationContext();
        byte[] bytes = value.toString().getBytes(StandardCharsets.UTF_8);
        if (bytes.length > 4 * 1024 * 1024) return;
        IO.execute(() -> write(app, id, bytes));
    }

    /** Wait for preceding writes and verify this snapshot fits the existing cache bound. */
    static boolean saveChecked(Context context, String id, JSONObject value) throws Exception {
        Context app = context.getApplicationContext();
        byte[] bytes = value.toString().getBytes(StandardCharsets.UTF_8);
        if (bytes.length > 16 * 1024 * 1024) return false;
        return IO.submit(() -> writePrepared(app, id, bytes)).get();
    }

    private static File preparedFile(Context context, String id) {
        if (id == null || !id.matches("[a-zA-Z0-9_-]{1,100}")) return null;
        return new File(new File(context.getNoBackupFilesDir(), "prepared_gallery_indexes"), id + ".json");
    }

    private static boolean writePrepared(Context app, String id, byte[] bytes) {
        synchronized (LOCK) {
            File target = preparedFile(app, id);
            if (target == null) return false;
            File dir = target.getParentFile();
            if (!dir.exists() && !dir.mkdirs()) return false;
            long total = 0;
            File[] files = dir.listFiles((folder, name) -> name.endsWith(".json"));
            if (files != null) for (File file : files) if (!file.equals(target)) total += file.length();
            if (total + bytes.length > 64L * 1024 * 1024) return false;
            AtomicFile atomic = new AtomicFile(target);
            FileOutputStream stream = null;
            try { stream = atomic.startWrite(); stream.write(bytes); atomic.finishWrite(stream); return true; }
            catch (Exception error) { if (stream != null) atomic.failWrite(stream); return false; }
        }
    }

    private static boolean write(Context app, String id, byte[] bytes) {
        synchronized (LOCK) {
            File target = file(app, id);
            if (target == null) return false;
            File dir = target.getParentFile();
            if (!dir.exists() && !dir.mkdirs()) return false;
            AtomicFile atomic = new AtomicFile(target);
            FileOutputStream stream = null;
            try { stream = atomic.startWrite(); stream.write(bytes); atomic.finishWrite(stream); }
            catch (Exception error) {
                if (stream != null) atomic.failWrite(stream);
                return false;
            }
            File[] old = dir.listFiles();
            if (old != null) for (File entry : old) {
                if (System.currentTimeMillis() - entry.lastModified() > 24 * 60 * 60 * 1000L) entry.delete();
            }
            return true;
        }
    }

    static JSONObject read(Context context, String id) {
        synchronized (LOCK) {
            File normal = file(context, id);
            File prepared = preparedFile(context, id);
            JSONObject saved = readFile(prepared, 16 * 1024 * 1024);
            JSONObject recent = readFile(normal, 4 * 1024 * 1024);
            if (saved == null) return recent;
            if (recent == null) return saved;
            return recent.optInt("page") > saved.optInt("page") ? recent : saved;
        }
    }

    private static JSONObject readFile(File target, int limit) {
        if (target == null || !target.isFile() || target.length() > limit) return null;
        try { return new JSONObject(new String(new AtomicFile(target).readFully(), StandardCharsets.UTF_8)); }
        catch (Exception ignored) { return null; }
    }

    private static File file(Context context, String id) {
        if (id == null || !id.matches("[a-zA-Z0-9_-]{1,100}")) return null;
        return new File(new File(context.getCacheDir(), "screen_snapshots"), id + ".json");
    }
}
