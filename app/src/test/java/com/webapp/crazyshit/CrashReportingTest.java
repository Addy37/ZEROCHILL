package com.webapp.crazyshit;

import static org.junit.Assert.*;

import android.app.Application;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

import java.io.File;
import java.nio.file.Files;
import java.util.*;

@RunWith(RobolectricTestRunner.class)
@Config(application = Application.class, sdk = 35)
public class CrashReportingTest {
    @Rule public TemporaryFolder folder = new TemporaryFolder();

    private CrashReport report(long now) throws Exception {
        RuntimeException error = new IllegalStateException("private unlabelled DM text");
        StackTraceElement[] trace = new StackTraceElement[100];
        Arrays.fill(
                trace,
                new StackTraceElement(
                        "com.webapp.crazyshit.ShitTokController",
                        "play",
                        "ShitTokController.java",
                        42));
        error.setStackTrace(trace);
        return CrashReport.capture(
                error,
                "main",
                "ShitTokActivity",
                "5.7.0-beta.1",
                50700,
                "559e52574b9700132fb45e8b1628ed949b50f7c7",
                "16",
                "OnePlus CPH2655",
                now,
                "Performance: 10ms\nRecent: https://private.example/content?token=secret");
    }

    private CrashStore store() throws Exception {
        return new CrashStore(new File(folder.newFolder(), "crashes.json"));
    }

    @Test
    public void payloadPreservesMetadataFramesAndBoundsWithoutPrivateExceptionText()
            throws Exception {
        CrashReport r = report(1000);
        assertTrue(r.firstMessage().contains("ZEROCHILL crashed in ShitTok"));
        assertTrue(r.summary.contains("50700"));
        assertTrue(r.summary.contains("559e52574b9700132fb45e8b1628ed949b50f7c7"));
        assertTrue(r.summary.contains("OnePlus"));
        assertTrue(r.summary.contains("Thread: main"));
        assertTrue(r.diagnostics.contains("remaining frames omitted"));
        assertTrue(r.diagnostics.contains("Performance"));
        assertFalse(r.summary.contains("private unlabelled"));
        assertFalse(r.diagnostics.contains("https://"));
        assertTrue(r.firstMessage().length() <= 2000);
        r.diagnostics = "x ".repeat(10000);
        assertEquals(3, r.chunks().size());
        for (String chunk : r.chunks()) assertTrue(chunk.length() <= 2000);
        r.diagnostics = "😀".repeat(4000);
        assertTrue(r.chunks().size() <= 3);
        for (String chunk : r.chunks())
            assertFalse(Character.isHighSurrogate(chunk.charAt(chunk.length() - 1)));
    }

    @Test
    public void redactsSecretsUrlsEmailAndDraftsButKeepsSourceAndFrames() {
        String text =
                "Authorization: Bearer abc.def\n"
                    + "Cookie: secret\n"
                    + "https://host/path?q=secret person@example.com access_token=xyz"
                    + " password='pass' api_key=key sb_secret_12345 eyJabc.eyJabc.abc "
                        + "Z".repeat(64)
                        + "\n"
                        + "dm_body='private message' comment_draft=secret\n"
                        + "com.webapp.Player.play(Player.java:123)"
                        + " 559e52574b9700132fb45e8b1628ed949b50f7c7";
        String safe = CrashSanitizer.clean(text);
        for (String secret :
                new String[] {
                    "abc.def",
                    "https://",
                    "person@example",
                    "xyz",
                    "'pass'",
                    "sb_secret_12345",
                    "eyJabc",
                    "private message",
                    "draft=secret",
                    "ZZZZZZ"
                }) assertFalse(secret, safe.contains(secret));
        assertFalse(CrashSanitizer.clean("{\"Cookie\":\"session=private-cookie\"}").contains("private-cookie"));
        assertTrue(safe.contains("Player.java:123"));
        assertTrue(safe.contains("559e52574b9700132fb45e8b1628ed949b50f7c7"));
    }

    @Test
    public void snapshotIncludesPerformanceOnceAndBoundsRecentEvents() {
        boolean enabled = ShitTokPoolDiagnostics.isEnabled();
        ShitTokPoolDiagnostics.setEnabled(true);
        try {
            for (int i = 0; i < 160; i++)
                ShitTokPoolDiagnostics.record(
                        "crash-fixture", "counter=" + i + " stats ".repeat(100));
            String snapshot = CrashReporter.snapshot();
            assertTrue(snapshot.length() < 2700);
            assertEquals(1, snapshot.split("This app session", -1).length - 1);
            assertTrue(snapshot.contains("counter=159"));
            assertFalse(snapshot.contains("https://"));
        } finally {
            ShitTokPoolDiagnostics.setEnabled(enabled);
        }
    }

    @Test
    public void fingerprintIgnoresLineNumbersAndMessages() throws Exception {
        Throwable a = new RuntimeException("one"), b = new RuntimeException("two");
        a.setStackTrace(
                new StackTraceElement[] {
                    new StackTraceElement("Player", "play", "Player.java", 1)
                });
        b.setStackTrace(
                new StackTraceElement[] {
                    new StackTraceElement("Player", "play", "Player.java", 900)
                });
        assertEquals(CrashReport.fingerprint(a), CrashReport.fingerprint(b));
        assertNotEquals(
                CrashReport.fingerprint(a),
                CrashReport.fingerprint(new IllegalArgumentException()));
    }

    @Test
    public void repeatsAndStorageAreBoundedAndMalformedStorageIsDiscarded() throws Exception {
        File file = new File(folder.newFolder(), "crash.json");
        CrashStore store = new CrashStore(file);
        for (int i = 0; i < 4; i++) store.capture(report(1000 + i));
        assertEquals(1, store.read().size());
        assertEquals(4, store.read().get(0).repeats);
        String oldMarker = store.read().get(0).marker();
        CrashReport upgrade = report(1100);
        upgrade.version = "5.8.0";
        store.capture(upgrade);
        assertEquals(2, store.read().size());
        assertNotEquals(oldMarker, store.read().get(1).marker());
        for (int i = 0; i < 8; i++) {
            CrashReport r = report(2000 + i);
            r.fingerprint = String.format("%064x", i);
            store.capture(r);
        }
        assertEquals(3, store.read().size());
        assertTrue(file.length() < 131072);
        Files.write(file.toPath(), "{broken".getBytes(java.nio.charset.StandardCharsets.UTF_8));
        assertTrue(store.read().isEmpty());
        store.capture(report(3000));
        assertEquals(1, store.read().size());
    }

    private static final class Fake implements CrashDelivery.Transport {
        int created;
        boolean fail;
        final Set<String> sent = new HashSet<>();

        public String find(String marker) {
            return created == 0 ? "" : "00000000-0000-0000-0000-000000000001";
        }

        public String create(CrashReport r) {
            created++;
            return "00000000-0000-0000-0000-000000000001";
        }

        public Set<String> messages(String id) {
            return sent;
        }

        public void reply(String id, String text) throws Exception {
            if (fail) throw new java.io.IOException("offline");
            sent.add(text);
        }
    }

    @Test
    public void failedDeliveryKeepsPendingSuccessRemovesPayloadAndRepeatsReuseThread()
            throws Exception {
        CrashStore store = store();
        store.capture(report(1000));
        Fake transport = new Fake();
        transport.fail = true;
        try {
            CrashDelivery.flush(store, transport, 2000);
            fail();
        } catch (java.io.IOException expected) {
        }
        assertTrue(store.read().get(0).pending());
        assertEquals(1, transport.created);
        transport.fail = false;
        assertTrue(CrashDelivery.flush(store, transport, 3000));
        assertFalse(store.read().get(0).pending());
        assertEquals("", store.read().get(0).diagnostics);
        assertFalse(CrashDelivery.flush(store, transport, 4000));
        store.capture(report(5000));
        assertFalse(CrashDelivery.flush(store, transport, 6000));
        assertTrue(CrashDelivery.flush(store, transport, 3604000));
        assertEquals(1, transport.created);
        assertTrue(transport.sent.stream().anyMatch(s -> s.contains("Repeated 2 times")));
    }

    @Test
    public void automaticCreationBudgetReservesNormalFeedbackCapacity() throws Exception {
        CrashStore store = store();
        store.capture(report(1000));
        Fake transport = new Fake();
        CrashDelivery.flush(store, transport, 2000);
        CrashReport other = report(3000);
        other.fingerprint = "1".repeat(64);
        store.capture(other);
        Fake next = new Fake();
        assertFalse(CrashDelivery.flush(store, next, 4000));
        assertEquals(0, next.created);
        assertTrue(store.read().stream().anyMatch(CrashReport::pending));
    }

    @Test
    public void feedbackTransportUsesExistingCrashThreadAndReplyContract() throws Exception {
        List<org.json.JSONObject> requests = new ArrayList<>();
        FeedbackRepository.requestOverride =
                payload -> {
                    requests.add(payload);
                    return new org.json.JSONObject()
                            .put("id", "thread")
                            .put("messages", new org.json.JSONArray())
                            .put("items", new org.json.JSONArray());
                };
        try {
            CrashDelivery.Transport transport =
                    FeedbackRepository.crashTransport(
                            org.robolectric.RuntimeEnvironment.getApplication());
            CrashReport report = report(1000);
            transport.find(report.marker());
            transport.create(report);
            transport.messages("thread");
            transport.reply("thread", "Crash diagnostics https://private.example/token");
            assertEquals(
                    Arrays.asList("list", "submit", "thread", "reply"),
                    requests.stream()
                            .map(p -> p.optString("action"))
                            .collect(java.util.stream.Collectors.toList()));
            assertEquals("bug_report", requests.get(1).getString("type"));
            assertEquals("Crash", requests.get(1).getString("section"));
            assertEquals(report.version, requests.get(1).getString("app_version"));
            assertFalse(requests.get(3).getString("message").contains("https://"));
        } finally {
            FeedbackRepository.requestOverride = null;
        }
    }

    @Test
    public void ordinaryFeedbackRemainsUnredactedAndKeepsExistingPayload() throws Exception {
        java.util.concurrent.CountDownLatch done = new java.util.concurrent.CountDownLatch(1);
        List<org.json.JSONObject> requests = new ArrayList<>();
        Throwable[] failure = {null};
        FeedbackRepository.requestOverride =
                payload -> {
                    requests.add(payload);
                    return new org.json.JSONObject().put("id", "normal-thread");
                };
        try {
            String text = "User chosen feedback https://example.com contact@example.com";
            FeedbackRepository.submit(
                    org.robolectric.RuntimeEnvironment.getApplication(),
                    "feedback",
                    text,
                    4,
                    "More",
                    (id, error) -> {
                        failure[0] = error;
                        done.countDown();
                    });
            assertTrue(done.await(5, java.util.concurrent.TimeUnit.SECONDS));
            assertNull(failure[0]);
            assertEquals(text, requests.get(0).getString("message"));
            assertEquals("More", requests.get(0).getString("section"));
            assertEquals(4, requests.get(0).getInt("rating"));
        } finally {
            FeedbackRepository.requestOverride = null;
        }
    }

    @Test
    public void handlerAlwaysDelegatesEvenIfReporterFails() {
        Throwable original = new RuntimeException();
        Thread current = Thread.currentThread();
        boolean[] called = {false};
        new CrashReporter.Handler(
                        (thread, error) -> {
                            assertSame(current, thread);
                            assertSame(original, error);
                            called[0] = true;
                        },
                        (thread, error) -> {
                            throw new OutOfMemoryError();
                        })
                .uncaughtException(current, original);
        assertTrue(called[0]);
    }
}
