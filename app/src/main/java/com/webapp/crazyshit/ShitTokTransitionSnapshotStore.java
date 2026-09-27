package com.webapp.crazyshit;

import android.app.Activity;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.view.TextureView;
import android.view.View;
import android.view.ViewGroup;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/** Keeps a short-lived rendered ShitTok frame for the reversible creator-gallery transition. */
final class ShitTokTransitionSnapshotStore {
    private static final int MAX_ENTRIES = 3;
    private static final LinkedHashMap<String, Entry> ENTRIES = new LinkedHashMap<>();

    private static final class Entry {
        Bitmap bitmap;
        Bitmap galleryPreview;
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

        captureFrame(token, view, target);
        return token;
    }

    static void captureGalleryPreview(String token, View preview) {
        if (token == null || token.isEmpty() || preview == null
                || preview.getWidth() <= 0 || preview.getHeight() <= 0) return;
        synchronized (ENTRIES) {
            if (!ENTRIES.containsKey(token)) return;
        }
        try {
            Bitmap bitmap = Bitmap.createBitmap(
                    preview.getWidth(), preview.getHeight(), Bitmap.Config.ARGB_8888);
            preview.draw(new Canvas(bitmap));
            synchronized (ENTRIES) {
                Entry entry = ENTRIES.get(token);
                if (entry != null) entry.galleryPreview = bitmap;
            }
        } catch (RuntimeException ignored) {
            // The real gallery still opens if a frame cannot be captured.
        }
    }

    static Bitmap galleryPreview(String token) {
        if (token == null || token.isEmpty()) return null;
        synchronized (ENTRIES) {
            Entry entry = ENTRIES.get(token);
            return entry == null ? null : entry.galleryPreview;
        }
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

    private static void captureFrame(String token, View view, Bitmap target) {
        try {
            Canvas canvas = new Canvas(target);
            view.draw(canvas);
            // ShitTok inflates a TextureView player. Read its current frame before the
            // gesture translates the window; PixelCopy's queued window read can instead
            // capture a later, partially translated frame at the old source rectangle.
            drawTextures(view, view, canvas);
            setBitmap(token, target);
        } catch (Throwable error) {
            remove(token);
        }
    }

    private static void drawTextures(View root, View current, Canvas canvas) {
        if (current instanceof TextureView && current.getVisibility() == View.VISIBLE) {
            TextureView texture = (TextureView) current;
            if (texture.isAvailable() && texture.getWidth() > 0 && texture.getHeight() > 0) {
                int[] origin = new int[2];
                int[] position = new int[2];
                root.getLocationInWindow(origin);
                texture.getLocationInWindow(position);
                float x = position[0] - origin[0];
                float y = position[1] - origin[1];
                if (x < root.getWidth() && x + texture.getWidth() > 0
                        && y < root.getHeight() && y + texture.getHeight() > 0) {
                    Bitmap frame = texture.getBitmap();
                    if (frame == null) return;
                    int save = canvas.save();
                    canvas.clipRect(x, y, x + texture.getWidth(), y + texture.getHeight());
                    canvas.drawBitmap(frame, x, y, null);
                    canvas.restoreToCount(save);
                    frame.recycle();
                    redrawViewsAbove(root, texture, canvas);
                }
            }
        }
        if (current instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) current;
            for (int i = 0; i < group.getChildCount(); i++) {
                drawTextures(root, group.getChildAt(i), canvas);
            }
        }
    }

    private static void redrawViewsAbove(View root, View texture, Canvas canvas) {
        int[] origin = new int[2];
        root.getLocationInWindow(origin);
        View child = texture;
        while (child != root && child.getParent() instanceof ViewGroup) {
            ViewGroup parent = (ViewGroup) child.getParent();
            int index = parent.indexOfChild(child);
            for (int i = index + 1; i < parent.getChildCount(); i++) {
                View sibling = parent.getChildAt(i);
                if (sibling.getVisibility() != View.VISIBLE) continue;
                int[] position = new int[2];
                sibling.getLocationInWindow(position);
                int save = canvas.save();
                canvas.translate(position[0] - origin[0], position[1] - origin[1]);
                sibling.draw(canvas);
                canvas.restoreToCount(save);
            }
            child = parent;
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
