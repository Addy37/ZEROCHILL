package com.webapp.crazyshit;

import android.app.Activity;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Rect;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.view.PixelCopy;
import android.view.View;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/** Keeps a short-lived rendered ShitTok frame for the reversible creator-gallery transition. */
final class ShitTokTransitionSnapshotStore {
    private static final int MAX_ENTRIES = 3;
    private static final Handler MAIN = new Handler(Looper.getMainLooper());
    private static final LinkedHashMap<String, Entry> ENTRIES = new LinkedHashMap<>();

    private static final class Entry {
        Bitmap bitmap;
    }

    private ShitTokTransitionSnapshotStore() {
    }

    static String beginCapture(Activity activity, View view) {
        if (activity == null || view == null || view.getWidth() <= 0 || view.getHeight() <= 0) {
            return "";
        }
        String token = UUID.randomUUID().toString();
        Entry entry = new Entry();
        synchronized (ENTRIES) {
            ENTRIES.put(token, entry);
            trimLocked();
        }

        int width = view.getWidth();
        int height = view.getHeight();
        Bitmap target;
        try {
            target = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
        } catch (Throwable error) {
            remove(token);
            return "";
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            int[] location = new int[2];
            view.getLocationInWindow(location);
            Rect source = new Rect(
                    location[0],
                    location[1],
                    location[0] + width,
                    location[1] + height
            );
            try {
                PixelCopy.request(
                        activity.getWindow(),
                        source,
                        target,
                        result -> {
                            if (result == PixelCopy.SUCCESS) {
                                setBitmap(token, target);
                            } else {
                                captureFallback(token, view, target);
                            }
                        },
                        MAIN
                );
                return token;
            } catch (Exception ignored) {
            }
        }

        captureFallback(token, view, target);
        return token;
    }

    static Bitmap snapshot(String token) {
        if (token == null || token.trim().isEmpty()) return null;
        synchronized (ENTRIES) {
            Entry entry = ENTRIES.get(token.trim());
            return entry == null ? null : entry.bitmap;
        }
    }

    static void remove(String token) {
        if (token == null || token.trim().isEmpty()) return;
        synchronized (ENTRIES) {
            ENTRIES.remove(token.trim());
        }
    }

    private static void captureFallback(String token, View view, Bitmap target) {
        try {
            Canvas canvas = new Canvas(target);
            view.draw(canvas);
            setBitmap(token, target);
        } catch (Throwable error) {
            remove(token);
        }
    }

    private static void setBitmap(String token, Bitmap bitmap) {
        synchronized (ENTRIES) {
            Entry entry = ENTRIES.get(token);
            if (entry != null) entry.bitmap = bitmap;
        }
    }

    private static void trimLocked() {
        while (ENTRIES.size() > MAX_ENTRIES) {
            Map.Entry<String, Entry> oldest = ENTRIES.entrySet().iterator().next();
            ENTRIES.remove(oldest.getKey());
        }
    }
}
