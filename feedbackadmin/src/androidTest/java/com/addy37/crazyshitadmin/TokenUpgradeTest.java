package com.addy37.crazyshitadmin;

import android.content.Context;
import androidx.test.platform.app.InstrumentationRegistry;
import java.security.KeyStore;
import org.junit.Test;
import static org.junit.Assert.*;

/** Explicit before/after adb install -r check. Synthetic token, no hosted authentication. */
public final class TokenUpgradeTest {
    private static final String FIXTURE = "synthetic-admin-token-upgrade-check-only";
    private Context context() { return InstrumentationRegistry.getInstrumentation().getTargetContext(); }
    private void probe() throws Exception {
        Context c = context();
        KeyStore keys = KeyStore.getInstance("AndroidKeyStore");
        keys.load(null);
        System.out.println("Upgrade probe: package=" + c.getPackageName() + " uid=" + c.getApplicationInfo().uid
                + " marker=" + c.getSharedPreferences("upgrade_probe", 0).getBoolean("seeded", false)
                + " ciphertext=" + c.getSharedPreferences("admin_secure_state", 0).contains("token_value")
                + " key=" + keys.containsAlias("crazyshit_admin_token_key"));
    }
    @Test public void seed() throws Exception {
        SecureTokenStore.save(context(), FIXTURE);
        // Instrumentation has no Activity stop to drain apply(); persist before killing the process.
        assertTrue(context().getSharedPreferences("admin_secure_state", 0).edit().commit());
        assertTrue(context().getSharedPreferences("upgrade_probe", 0).edit().putBoolean("seeded", true).commit());
        probe();
        assertEquals(FIXTURE, SecureTokenStore.read(context()));
    }
    @Test public void beforeUpgrade() throws Exception {
        probe();
        assertEquals(FIXTURE, SecureTokenStore.read(context()));
    }
    @Test public void verify() throws Exception {
        probe();
        assertEquals(FIXTURE, SecureTokenStore.read(context()));
        SecureTokenStore.clear(context());
    }
}
