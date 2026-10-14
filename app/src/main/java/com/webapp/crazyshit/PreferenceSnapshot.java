package com.webapp.crazyshit;

import android.content.SharedPreferences;
import android.util.Base64;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;

/** Compact change key; callers do not retain a second map of an entire preference store. */
final class PreferenceSnapshot {
    private PreferenceSnapshot() { }

    static String key(SharedPreferences preferences) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            for (Map.Entry<String, ?> entry : new TreeMap<>(preferences.getAll()).entrySet()) {
                update(digest, entry.getKey());
                Object value = entry.getValue();
                update(digest, value == null ? "null" : value.getClass().getName());
                if (value instanceof Set<?>) {
                    TreeSet<String> sorted = new TreeSet<>();
                    for (Object item : (Set<?>) value) sorted.add(String.valueOf(item));
                    for (String item : sorted) update(digest, item);
                } else update(digest, String.valueOf(value));
                digest.update((byte) 0xff);
            }
            return Base64.encodeToString(digest.digest(), Base64.NO_WRAP);
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException(impossible);
        }
    }

    private static void update(MessageDigest digest, String value) {
        byte[] bytes = value.getBytes(StandardCharsets.UTF_8);
        digest.update(java.nio.ByteBuffer.allocate(4).putInt(bytes.length).array());
        digest.update(bytes);
    }
}
