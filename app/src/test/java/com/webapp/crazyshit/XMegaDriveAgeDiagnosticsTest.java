package com.webapp.crazyshit;

import org.junit.Test;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class XMegaDriveAgeDiagnosticsTest {
    @Test public void reportShowsOnlyNamesAndChangeKinds() {
        Map<String, String> beforeCookies = new LinkedHashMap<>();
        beforeCookies.put("www.xmegadrive.com:cf_clearance", "fingerprint-a");
        beforeCookies.put("www.xmegadrive.com:age_gate", "fingerprint-old");
        Map<String, String> afterCookies = new LinkedHashMap<>();
        afterCookies.put("www.xmegadrive.com:cf_clearance", "fingerprint-a");
        afterCookies.put("www.xmegadrive.com:age_gate", "fingerprint-new");
        afterCookies.put("www.xmegadrive.com:age_verified", "fingerprint-secret");

        Map<String, String> beforeLocal = new LinkedHashMap<>();
        beforeLocal.put("age", "old-hash");
        Map<String, String> afterLocal = new LinkedHashMap<>();
        afterLocal.put("age", "new-hash");

        String report = XMegaDriveAgeDiagnostics.buildReportForTest(
                "https://www.xmegadrive.com/categories/?token=secret",
                beforeCookies,
                beforeLocal,
                new LinkedHashMap<>(),
                "https://www.xmegadrive.com/videos/#private",
                afterCookies,
                afterLocal,
                new LinkedHashMap<>()
        );

        assertTrue(report.contains("www.xmegadrive.com:age_gate"));
        assertTrue(report.contains("www.xmegadrive.com:age_verified"));
        assertTrue(report.contains("changed: age"));
        assertTrue(report.contains("Navigation: changed"));
        assertFalse(report.contains("fingerprint-old"));
        assertFalse(report.contains("fingerprint-new"));
        assertFalse(report.contains("fingerprint-secret"));
        assertFalse(report.contains("token=secret"));
        assertFalse(report.contains("#private"));
    }

    @Test public void storageScriptFingerprintsValuesBeforeReturningThem() {
        String script = XMegaDriveAgeDiagnostics.storageSnapshotScript();
        assertTrue(script.contains("out[k]=fp(store.getItem(k))"));
        assertTrue(script.contains("local:dump(window.localStorage)"));
        assertTrue(script.contains("session:dump(window.sessionStorage)"));
        assertFalse(script.contains("out[k]=store.getItem(k)"));
    }
}
