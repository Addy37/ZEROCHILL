package com.webapp.crazyshit;

import android.content.Context;
import android.content.SharedPreferences;
import android.security.keystore.KeyGenParameterSpec;
import android.security.keystore.KeyProperties;
import android.util.Base64;

import org.json.JSONObject;

import java.nio.charset.StandardCharsets;
import java.security.KeyStore;

import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;

/** Stores the ZeroChill account session encrypted by Android Keystore. */
final class ZeroChillSessionStore {
    private static final String KEY_ALIAS = "zerochill_account_session_key";
    private static final String PREFS = "zerochill_account_secure_state";
    private static final String VALUE = "session_value";
    private static final String IV = "session_iv";

    private ZeroChillSessionStore() {}

    static void save(Context context, String value) throws Exception {
        SecretKey key = getOrCreateKey();
        Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
        cipher.init(Cipher.ENCRYPT_MODE, key);
        byte[] encrypted = cipher.doFinal(value.getBytes(StandardCharsets.UTF_8));
        context.getApplicationContext().getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
                .putString(VALUE, Base64.encodeToString(encrypted, Base64.NO_WRAP))
                .putString(IV, Base64.encodeToString(cipher.getIV(), Base64.NO_WRAP))
                .apply();
    }

    static String read(Context context) {
        try {
            SharedPreferences prefs = context.getApplicationContext()
                    .getSharedPreferences(PREFS, Context.MODE_PRIVATE);
            String value = prefs.getString(VALUE, "");
            String iv = prefs.getString(IV, "");
            if (value == null || value.isEmpty() || iv == null || iv.isEmpty()) return "";
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(
                    Cipher.DECRYPT_MODE,
                    getOrCreateKey(),
                    new GCMParameterSpec(128, Base64.decode(iv, Base64.NO_WRAP))
            );
            return new String(
                    cipher.doFinal(Base64.decode(value, Base64.NO_WRAP)),
                    StandardCharsets.UTF_8
            );
        } catch (Exception ignored) {
            return "";
        }
    }

    static void clear(Context context) {
        context.getApplicationContext()
                .getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .edit()
                .clear()
                .apply();
    }

    static String currentUserId(Context context) {
        try {
            String session = read(context);
            if (session.isEmpty()) return "";
            String token = new JSONObject(session).optString("access_token", "");
            String[] parts = token.split("\\.");
            if (parts.length < 2) return "";
            byte[] decoded = Base64.decode(
                    parts[1],
                    Base64.URL_SAFE | Base64.NO_WRAP | Base64.NO_PADDING
            );
            return new JSONObject(new String(decoded, StandardCharsets.UTF_8))
                    .optString("sub", "");
        } catch (Exception ignored) {
            return "";
        }
    }

    private static SecretKey getOrCreateKey() throws Exception {
        KeyStore store = KeyStore.getInstance("AndroidKeyStore");
        store.load(null);
        KeyStore.Entry existing = store.getEntry(KEY_ALIAS, null);
        if (existing instanceof KeyStore.SecretKeyEntry) {
            return ((KeyStore.SecretKeyEntry) existing).getSecretKey();
        }
        KeyGenerator generator = KeyGenerator.getInstance(
                KeyProperties.KEY_ALGORITHM_AES,
                "AndroidKeyStore"
        );
        generator.init(new KeyGenParameterSpec.Builder(
                KEY_ALIAS,
                KeyProperties.PURPOSE_ENCRYPT | KeyProperties.PURPOSE_DECRYPT
        )
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .build());
        return generator.generateKey();
    }
}
