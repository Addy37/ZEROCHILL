package com.webapp.crazyshit;

import static org.junit.Assert.*;

import android.app.Application;
import org.jsoup.Connection;
import org.jsoup.Jsoup;
import org.jsoup.HttpStatusException;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.Implementation;
import org.robolectric.annotation.Implements;
import org.robolectric.shadows.ShadowSystemClock;
import java.io.IOException;
import java.lang.reflect.Proxy;
import java.net.URI;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;

@RunWith(RobolectricTestRunner.class)
@Config(application = Application.class, sdk = 35, shadows = BunkrResolutionTest.Wire.class)
public class BunkrResolutionTest {
    static final String PAGE = "https://bunkr.cr/f/sample";
    static final String CDN = "https://c3mb-b.cdn.cr/storage/media/movie%20name.mp4?quality=original";
    private static final String MAINTENANCE_NOTICE =
            "<p>The server hosting this file is <strong>temporarily unavailable</strong> for maintenance. "
            + "Playback and downloads are disabled while maintenance is in progress. "
            + "Please check back again later.</p>";
    static final String MAINTENANCE_FILE_PAGE =
            "<title>Example.mp4</title><h1>Example.mp4</h1><button disabled>Download unavailable</button>"
            + "<section><h2>Server under maintenance</h2>" + MAINTENANCE_NOTICE + "</section>";

    @Implements(value = Jsoup.class, isInAndroidSdk = false)
    public static class Wire {
        static String html;
        static boolean signFails, legacyWorks;
        static int signStatus;
        static int legacyRequests;
        static boolean signerChallenge;
        static boolean legacyChallenge;
        static int signRequests;
        static String signRequest;
        static Map<String, String> signHeaders;
        static int pageRequests;
        static int pageStatus;
        static boolean transportFails;
        @Implementation protected static Connection connect(String url) {
            Map<String, String> headers = new LinkedHashMap<>();
            return (Connection) Proxy.newProxyInstance(Connection.class.getClassLoader(),
                    new Class<?>[]{Connection.class}, (proxy, method, args) -> {
                        if ("get".equals(method.getName())) {
                            pageRequests++;
                            if (pageStatus != 0) throw new HttpStatusException("upstream", pageStatus, url);
                            if (transportFails) throw new IOException("connection timed out");
                            return Jsoup.parse(html, url);
                        }
                        if ("header".equals(method.getName()) && args.length == 2)
                            headers.put((String) args[0], (String) args[1]);
                        if ("execute".equals(method.getName())) {
                            final String response;
                            if (url.contains("/sign?")) {
                                signRequests++;
                                signRequest = url;
                                signHeaders = headers;
                                if (signStatus != 0) throw new HttpStatusException("denied", signStatus, url);
                                if (signFails) throw new IOException("sign unavailable");
                                response = signerChallenge ? "<title>Checkpoint Sentinel</title>Verify you are human"
                                        : "{\"token\":\"a+b/=\",\"ex\":\"2000000000\"}";
                            } else if (url.contains("/api/_001_v2")) {
                                legacyRequests++;
                                if (!legacyWorks && !legacyChallenge) throw new IOException("legacy unavailable");
                                response = legacyChallenge ? "<title>Checkpoint Sentinel</title>Verify you are human"
                                        : "{\"url\":\"https://legacy.cdn.cr/old.mp4\"}";
                            } else throw new IOException("legacy unavailable");
                            return Proxy.newProxyInstance(Connection.Response.class.getClassLoader(),
                                    new Class<?>[]{Connection.Response.class}, (r, m, a) ->
                                            "body".equals(m.getName()) ? response : null);
                        }
                        if (method.getReturnType().isInstance(proxy)) return proxy;
                        return null;
                    });
        }
    }

    @Before public void reset() {
        BunkrMaintenance.retry(PAGE);
        Wire.signFails = false;
        Wire.signStatus = 0;
        Wire.legacyRequests = 0;
        Wire.signRequests = 0;
        Wire.signerChallenge = false;
        Wire.legacyChallenge = false;
        Wire.pageRequests = 0;
        Wire.pageStatus = 0;
        Wire.transportFails = false;
        Wire.legacyWorks = false;
        Wire.signHeaders = new LinkedHashMap<>();
        Wire.html = "<h1>Example.mp4</h1><div data-file-id='123'></div><script>"
                + "var videoCoverUrl='https://static.scdn.st/thumbs/movie.mp4_grid.png';"
                + "var jsCDN='" + CDN + "';var signUrl='https://glb-apisign.cdn.cr/sign';</script>";
    }

    @Test public void resolvesModernPageWithEncodedSigningPathAndRetainedCdnQuery() throws Exception {
        CrazyShitRepository.StreamInfo stream = PlayableSourceRouter.resolve(null, PAGE);
        assertEquals(CDN + "&token=a%2Bb%2F%3D&ex=2000000000", stream.mediaUrl);
        assertEquals("/storage/media/movie name.mp4",
                android.net.Uri.parse(Wire.signRequest).getQueryParameter("path"));
        assertEquals(PAGE, stream.requestReferer);
        assertEquals("https://bunkr.cr", Wire.signHeaders.get("Origin"));
        assertFalse(Wire.signHeaders.containsKey("Cookie"));
    }

    @Test public void legacyDownloadApiSurvivesSignerFailure() throws Exception {
        Wire.signFails = true;
        Wire.legacyWorks = true;
        CrazyShitRepository.StreamInfo stream = new BunkrRepository().resolvePlayable(null, PAGE);
        assertEquals("https://legacy.cdn.cr/old.mp4", stream.mediaUrl);
        assertEquals("https://dl.bunkr.cr/file/123", stream.requestReferer);
    }

    @Test public void signerFailureCannotTurnGridArtworkOrUnsignedCdnIntoPlayable() {
        Wire.signFails = true;
        assertThrows(IOException.class, () -> new BunkrRepository().resolvePlayable(null, PAGE));
    }

    @Test public void signerFailureCannotReturnUnsignedVideoElement() {
        Wire.signFails = true;
        Wire.html += "<video><source src='" + CDN + "'></video>";
        assertThrows(IOException.class, () -> new BunkrRepository().resolvePlayable(null, PAGE));
    }

    @Test public void deniedSearchIsNotAutomaticallyRetried() {
        for (int status : new int[]{401, 403, 429}) {
            Wire.pageRequests = 0;
            Wire.pageStatus = status;
            assertThrows(HttpStatusException.class, () ->
                    new BunkrRepository().searchAlbums(null, "example", 1));
            assertEquals(1, Wire.pageRequests);
        }
    }

    @Test public void deniedFileDoesNotCycleThroughMirrorOrigins() {
        Wire.pageStatus = 403;
        HttpStatusException error = assertThrows(HttpStatusException.class, () ->
                new BunkrRepository().resolvePlayable(null, PAGE));
        assertFalse(BunkrMaintenance.isConfirmed(error));
        assertFalse(BunkrMaintenance.isKnown(PAGE));
        assertEquals(1, Wire.pageRequests);
    }

    @Test public void signingDenialDoesNotFallBackToLegacyOrUnsignedVideo() {
        Wire.signStatus = 403;
        Wire.legacyWorks = true;
        Wire.html += "<video src='" + CDN + "'></video>";
        assertThrows(HttpStatusException.class, () ->
                new BunkrRepository().resolvePlayable(null, PAGE));
        assertEquals(0, Wire.legacyRequests);
    }

    @Test public void verificationHtmlDoesNotCycleThroughMirrorOrigins() {
        Wire.html = "<title>Checkpoint Sentinel</title><p>Verify you are human</p>"
                + MAINTENANCE_NOTICE;
        IOException error = assertThrows(IOException.class, () ->
                new BunkrRepository().resolvePlayable(null, PAGE));
        assertFalse(BunkrMaintenance.isConfirmed(error));
        assertFalse(BunkrMaintenance.isKnown(PAGE));
        assertEquals(1, Wire.pageRequests);
    }

    @Test public void fileMaintenanceShortCircuitsRepeatPlaybackAcrossMirrors() {
        Wire.html += MAINTENANCE_NOTICE;
        BunkrRepository repository = new BunkrRepository();
        IOException error = assertThrows(BunkrMaintenance.UnavailableException.class,
                () -> repository.resolvePlayable(null, PAGE));
        assertTrue(BunkrMaintenance.isConfirmed(new IllegalStateException(error)));
        String mirror = "https://bunkr.fi/f/sample?from=album#bunkr-page";
        assertTrue(BunkrMaintenance.isKnown(mirror));
        assertFalse(BunkrMaintenance.isKnown("https://bunkr.cr/f/Sample"));
        assertFalse(BunkrMaintenance.isKnown("https://other.example/f/sample"));
        assertThrows(BunkrMaintenance.UnavailableException.class,
                () -> PlayableSourceRouter.resolve(null, mirror));
        assertEquals(1, Wire.pageRequests);
        assertEquals(0, Wire.signRequests);
        assertEquals(0, Wire.legacyRequests);
    }

    @Test public void fileNoticeTakesPrecedenceOverMaintenanceHeading() {
        Wire.html = MAINTENANCE_FILE_PAGE;
        assertThrows(BunkrMaintenance.UnavailableException.class, () ->
                PlayableSourceRouter.resolve(null, PAGE));
        assertTrue(BunkrMaintenance.isKnown(PAGE));
        assertEquals(1, Wire.pageRequests);
        assertEquals(0, Wire.signRequests);
        assertEquals(0, Wire.legacyRequests);
    }

    @Test public void maintenanceExpiresAndPlaybackRecoversNormally() throws Exception {
        String healthy = Wire.html;
        Wire.html += MAINTENANCE_NOTICE;
        BunkrRepository repository = new BunkrRepository();
        assertThrows(BunkrMaintenance.UnavailableException.class,
                () -> repository.resolvePlayable(null, PAGE));
        Wire.html = healthy;
        ShadowSystemClock.advanceBy(Duration.ofMinutes(4));
        assertEquals(60_000, BunkrMaintenance.remainingMillis(PAGE));
        assertThrows(BunkrMaintenance.UnavailableException.class,
                () -> repository.resolvePlayable(null, PAGE));
        ShadowSystemClock.advanceBy(Duration.ofMinutes(1));
        assertFalse(BunkrMaintenance.isKnown(PAGE));
        assertTrue(repository.resolvePlayable(null, PAGE).mediaUrl.startsWith(CDN));
        assertEquals(2, Wire.pageRequests);
        assertEquals(1, Wire.signRequests);
    }

    @Test public void artworkRepairRecordsMaintenanceAndExplicitRetryCanRecover() throws Exception {
        String healthy = Wire.html;
        String artwork = "https://static.scdn.st/thumbs/movie.jpg";
        Wire.html = "<meta property='og:image' content='" + artwork + "'>" + MAINTENANCE_NOTICE;
        BunkrRepository repository = new BunkrRepository();
        assertEquals(artwork, repository.fetchFileArtwork(null, PAGE, ""));
        assertTrue(BunkrMaintenance.isKnown(PAGE));
        assertThrows(BunkrMaintenance.UnavailableException.class,
                () -> repository.resolvePlayable(null, PAGE));
        assertEquals(1, Wire.pageRequests);

        Wire.html = healthy;
        BunkrMaintenance.retry(PAGE);
        assertFalse(BunkrMaintenance.isKnown(PAGE));
        assertTrue(repository.resolvePlayable(null, PAGE).mediaUrl.startsWith(CDN));
        assertEquals(2, Wire.pageRequests);
    }

    @Test public void maintenancePlaceholderRequiresActualPlayerSource() throws Exception {
        String placeholder = "https://static.scdn.st/maintenance-vid.mp4?fallback=1";
        String healthy = Wire.html;
        Wire.html = healthy + "<script>const fallback = '" + placeholder + "';</script>"
                + "<div hidden>" + MAINTENANCE_NOTICE + "</div>"
                + "<video hidden src='" + placeholder + "'></video>";
        BunkrRepository repository = new BunkrRepository();
        assertTrue(repository.resolvePlayable(null, PAGE).mediaUrl.startsWith(CDN));
        assertFalse(BunkrMaintenance.isKnown(PAGE));

        Wire.html = "<video><source src='" + placeholder + "'></video>";
        assertThrows(BunkrMaintenance.UnavailableException.class,
                () -> repository.resolvePlayable(null, PAGE));
        assertTrue(BunkrMaintenance.isKnown(PAGE));
    }

    @Test public void realPlayerSourceTakesPrecedenceOverPlaceholderAlternative() throws Exception {
        String video = "https://legacy.cdn.cr/movie.mp4";
        Wire.html = "<video><source src='https://static.bunkr.ru/maint.mp4'>"
                + "<source src='" + video + "'></video>";
        assertEquals(video, new BunkrRepository().resolvePlayable(null, PAGE).mediaUrl);
        assertFalse(BunkrMaintenance.isKnown(PAGE));
    }

    @Test public void onlyPositiveFileEvidenceMarksMaintenance() {
        BunkrRepository repository = new BunkrRepository();
        for (String page : new String[]{
                "<p>File not found</p><video src='https://static.bunkr.ru/maint.mp4'></video>",
                "<title>503 Service unavailable</title><p>Servers are currently under heavy load</p>",
                "<h2>Server under maintenance</h2>",
                "<p>The server is temporarily unavailable</p>",
                "<video src='https://external.example/maint.mp4'></video>",
                "<h1>Unsupported.avi</h1><video src='https://legacy.cdn.cr/movie.avi'></video>"}) {
            Wire.html = page;
            IOException error = assertThrows(IOException.class,
                    () -> repository.resolvePlayable(null, PAGE));
            assertFalse(BunkrMaintenance.isConfirmed(error));
            assertFalse(BunkrMaintenance.isKnown(PAGE));
        }
        Wire.transportFails = true;
        IOException error = assertThrows(IOException.class, () -> repository.resolvePlayable(null, PAGE));
        assertFalse(BunkrMaintenance.isConfirmed(error));
        assertFalse(BunkrMaintenance.isKnown(PAGE));
    }

    @Test public void filePageWithMediaExtensionAlsoChecksForMaintenance() {
        String slugWithExtension = PAGE + "-file.mp4";
        Wire.html = MAINTENANCE_NOTICE;
        assertThrows(BunkrMaintenance.UnavailableException.class,
                () -> new BunkrRepository().resolvePlayable(null, slugWithExtension));
        assertEquals(1, Wire.pageRequests);
    }

    @Test public void signerVerificationHtmlDoesNotRetryOrUseLegacyApi() {
        Wire.signerChallenge = true;
        Wire.legacyWorks = true;
        assertThrows(IOException.class, () -> new BunkrRepository().resolvePlayable(null, PAGE));
        assertEquals(1, Wire.signRequests);
        assertEquals(0, Wire.legacyRequests);
    }

    @Test public void legacyVerificationHtmlDoesNotSwitchApiEndpoints() {
        Wire.html = "<h1>Legacy.mp4</h1><div data-file-id='123'></div>";
        Wire.legacyChallenge = true;
        assertThrows(IOException.class, () -> new BunkrRepository().resolvePlayable(null, PAGE));
        assertEquals(1, Wire.legacyRequests);
    }

    @Test public void transportFailureStillUsesBoundedSearchRetry() {
        Wire.transportFails = true;
        assertThrows(IOException.class, () ->
                new BunkrRepository().searchAlbums(null, "example", 1));
        assertEquals(2, Wire.pageRequests);
    }

    @Test public void legacyEmbeddedVideoStillWorks() throws Exception {
        Wire.html = "<h1>Old movie</h1><video src='https://legacy.cdn.cr/movie.mp4'></video>";
        assertEquals("https://legacy.cdn.cr/movie.mp4",
                new BunkrRepository().resolvePlayable(null, PAGE).mediaUrl);
    }

    @Test public void browserChallengeIsNotReportedAsNoSearchResults() {
        Wire.html = "<html><head><title>Checkpoint Sentinel</title></head><body>"
                + "Verify you are human before continuing.</body></html>";
        assertThrows(IOException.class, () ->
                new BunkrRepository().searchAlbums(null, "example", 1));
        assertEquals(1, Wire.pageRequests);
    }

    @Test public void currentSentinelTitleWithSeparatorIsNotAnEmptySearch() {
        Wire.html = "<html><title>Checkpoint | Sentinel</title><body>Security checkpoint</body></html>";
        assertThrows(IOException.class, () ->
                new BunkrRepository().searchAlbums(null, "sample", 1));
        assertEquals(1, Wire.pageRequests);
    }

    @Test public void maintenance503PageIsNotReportedAsNoSearchResults() {
        Wire.html = "<html><head><title>503 Service unavailable | BUNKR</title></head><body>"
                + "Our servers are currently under heavy load.</body></html>";
        assertThrows(IOException.class, () ->
                new BunkrRepository().searchAlbums(null, "example", 1));
    }

    @Test public void ordinaryEmptySearchCanStillReturnZeroResults() throws Exception {
        Wire.html = "<html><head><title>Bunkr Albums</title></head>"
                + "<body><p>No matches found</p></body></html>";
        assertTrue(new BunkrRepository().searchAlbums(null, "example", 1).isEmpty());
    }

    @Test public void constrainedBunkrCdnIsNotPrewarmed() {
        assertTrue(BunkrRepository.shouldSkipMediaWarm(PAGE,
                "https://s1.scdn.st/storage/video.mp4?token=abc"));
        assertTrue(BunkrRepository.shouldSkipMediaWarm(PAGE,
                "https://scdn.st/file.mp4"));
        assertFalse(BunkrRepository.shouldSkipMediaWarm(PAGE,
                "https://c1.cdn.cr/storage/video.mp4?token=abc"));
        assertFalse(BunkrRepository.shouldSkipMediaWarm(
                "https://fapello.com/creator/123", "https://s1.scdn.st/storage/video.mp4"));
        assertFalse(BunkrRepository.shouldSkipMediaWarm(PAGE,
                "https://malicious-scdn.st/media.mp4"));
    }

    @Test public void cachedSignedStreamsExpireBeforeReuseButLegacyAndOtherSourcesRemain() {
        String signed = "https://media.cdn.cr/movie.mp4?token=abc&ex=2000000000";
        assertTrue(BunkrRepository.usableCachedPlayable(PAGE, signed, 1999999900L));
        assertFalse(BunkrRepository.usableCachedPlayable(PAGE, signed, 1999999990L));
        assertFalse(BunkrRepository.usableCachedPlayable(PAGE, signed, 2000000001L));
        assertFalse(BunkrRepository.usableCachedPlayable(PAGE, signed.replace("2000000000", "bad"), 1));
        assertTrue(BunkrRepository.usableCachedPlayable(PAGE, "https://legacy.cdn.cr/movie.mp4", 2000000001L));
        assertTrue(BunkrRepository.usableCachedPlayable("https://example.com/file", signed, 2000000001L));
    }
}
