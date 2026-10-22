package com.webapp.crazyshit;

import android.app.Application;
import android.content.Context;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;
import static org.junit.Assert.*;

/** The server and Android run the same schema mutation corpus. */
@RunWith(RobolectricTestRunner.class)
@Config(application = Application.class, sdk = 35)
public final class SourceConfigAgreementTest {
    @Test public void sharedValidationCases() throws Exception {
        Context context = RuntimeEnvironment.getApplication();
        String bundled;
        try (InputStream input = context.getAssets().open("source_config_defaults.json")) {
            bundled = new String(input.readAllBytes(), StandardCharsets.UTF_8);
        }
        java.nio.file.Path corpus = Paths.get("../supabase/tests/source-config-cases.json");
        if (!Files.exists(corpus)) corpus = Paths.get("supabase/tests/source-config-cases.json");
        JSONArray cases = new JSONArray(new String(Files.readAllBytes(corpus), StandardCharsets.UTF_8));
        for (int i = 0; i < cases.length(); i++) {
            JSONObject test = cases.getJSONObject(i);
            JSONObject document = new JSONObject(bundled);
            if (test.has("delete")) {
                String[] path = test.getString("delete").split("\\.");
                JSONObject parent = document;
                for (int part = 0; part < path.length - 1; part++) parent = parent.getJSONObject(path[part]);
                parent.remove(path[path.length - 1]);
            }
            if (test.has("set")) {
                String[] path = test.getString("set").split("\\.");
                JSONObject parent = document;
                for (int part = 0; part < path.length - 1; part++) parent = parent.getJSONObject(path[part]);
                parent.put(path[path.length - 1], test.get("value"));
            }
            boolean valid;
            try { SourceConfig.parseAndValidate(document.toString()); valid = true; }
            catch (SourceConfig.ValidationException expected) { valid = false; }
            assertEquals(test.getString("name"), test.getBoolean("valid"), valid);
        }
    }
}
