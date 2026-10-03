package com.webapp.crazyshit;

import java.util.LinkedHashSet;
import java.util.Set;
import java.util.UUID;

/** Stages a new immutable image, then swaps the profile with a compare-and-set. */
final class ProfileAvatarUpload {
    interface Backend {
        String currentPath() throws Exception;
        void upload(String path, byte[] jpeg) throws Exception;
        boolean replace(String previous, String next) throws Exception;
        void remove(String path) throws Exception;
        default StoredImages oldestImages() throws Exception { return new StoredImages(java.util.Collections.emptyList(), 0L); }
        default long monotonicMillis() { return System.nanoTime() / 1000000L; }
    }
    interface Pending {
        Set<String> read();
        void write(Set<String> paths) throws Exception;
    }

    static String save(String owner, byte[] jpeg, Backend backend, Pending pending) throws Exception {
        if (jpeg == null || jpeg.length == 0) throw new IllegalArgumentException("Choose an image first.");
        if (jpeg.length > 512 * 1024) throw new IllegalArgumentException("Avatar image is too large.");
        // Do not create another staged object while an earlier deletion still needs retrying.
        cleanup(owner, backend, pending);
        recoverAbandoned(owner, backend);
        String previous = backend.currentPath();
        String next = owner + "/avatar-" + UUID.randomUUID() + ".jpg";
        LinkedHashSet<String> debt = new LinkedHashSet<>(pending.read());
        debt.add(next);
        if (owned(owner, previous) && !legacy(owner, previous)) debt.add(previous);
        pending.write(debt); // Durable before the first network mutation, including process death.
        try {
            long started = backend.monotonicMillis();
            backend.upload(next, jpeg);
            // Leave an hour of margin before the server-time 24-hour staging cleanup window.
            if (backend.monotonicMillis() - started >= 23 * 60 * 60 * 1000L)
                throw new IllegalStateException("Avatar upload expired. Try again.");
            if (!backend.replace(previous, next)) {
                throw new IllegalStateException("Your avatar changed on another device. Try again.");
            }
        } catch (Exception error) {
            // A lost PATCH response is ambiguous. Never delete an object the profile now uses.
            boolean committed = false;
            try { committed = next.equals(backend.currentPath()); } catch (Exception ignored) { }
            try { cleanup(owner, backend, pending); } catch (Exception ignored) { }
            if (!committed) {
                try { committed = next.equals(backend.currentPath()); } catch (Exception ignored) { }
            }
            if (!committed) throw error;
        }
        // Deletion failure does not roll back a committed profile. Retry debt before the next upload.
        try { cleanup(owner, backend, pending); } catch (Exception ignored) { }
        return next;
    }

    static final class StoredImage {
        final String name;
        final long createdAt;
        StoredImage(String name, long createdAt) { this.name = name; this.createdAt = createdAt; }
    }

    static final class StoredImages {
        final java.util.List<StoredImage> images;
        final long serverTime;
        StoredImages(java.util.List<StoredImage> images, long serverTime) {
            this.images = images; this.serverTime = serverTime;
        }
    }

    /** Recover deletion debt lost by app-data removal without touching another device's staging. */
    static void recoverAbandoned(String owner, Backend backend) throws Exception {
        for (int page = 0; page < 5; page++) {
            StoredImages listed = backend.oldestImages();
            // Never compare server timestamps to the device wall clock.
            if (listed.serverTime <= 0) return;
            int removed = 0;
            for (StoredImage image : listed.images) {
                if (image.createdAt <= 0 || image.createdAt >= listed.serverTime - 86400000L
                        || image.name == null || !image.name.startsWith("avatar-") || !image.name.endsWith(".jpg")) continue;
                try { UUID.fromString(image.name.substring(7, image.name.length() - 4)); }
                catch (IllegalArgumentException ignored) { continue; }
                String path = owner + "/" + image.name;
                if (owned(owner, path) && !path.equals(backend.currentPath())) {
                    backend.remove(path);
                    removed++;
                }
            }
            if (removed == 0) return;
        }
    }

    static void cleanup(String owner, Backend backend, Pending pending) throws Exception {
        LinkedHashSet<String> remaining = new LinkedHashSet<>(pending.read());
        for (String path : new LinkedHashSet<>(remaining)) {
            if (!owned(owner, path)) throw new IllegalStateException("Invalid avatar cleanup path.");
            if (!legacy(owner, path) && !path.equals(backend.currentPath())) backend.remove(path);
            remaining.remove(path);
            pending.write(remaining);
        }
    }

    private static boolean legacy(String owner, String path) {
        // Old clients still overwrite this one name. Retain that bounded compatibility file.
        return (owner + "/avatar.jpg").equals(path);
    }

    static boolean owned(String owner, String path) {
        return owner != null && !owner.isEmpty() && path != null
                && path.startsWith(owner + "/") && !path.substring(owner.length() + 1).contains("/")
                && !path.contains("..") && !path.contains("?");
    }
}
