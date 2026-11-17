package com.webapp.crazyshit;

import android.app.Activity;
import android.app.PendingIntent;
import android.app.DownloadManager;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageInfo;
import android.content.pm.PackageInstaller;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.net.Uri;
import android.os.Build;
import android.provider.Settings;
import android.os.Environment;
import android.util.Log;

import java.io.File;
import java.io.FileOutputStream;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

final class AppUpdater {
    private static final long CHECK_INTERVAL_MS = 4L * 60L * 60L * 1000L;
    private static final long CARD_LATER_MS = 12L * 60L * 60L * 1000L;
    private static final Pattern NUMBER = Pattern.compile("\\d+");
    private static final String PREF_CARD_LATER_VERSION = "update_card_later_version";
    private static final String PREF_CARD_LATER_UNTIL = "update_card_later_until";
    private static final String PREF_PREVIEW_NEXT_RESUME = "beta_update_preview_next_resume";

    private final Activity activity;
    private final ExecutorService io = Executors.newSingleThreadExecutor();
    private final boolean betaChannel;
    private final UpdateCardController card;

    private volatile boolean checking;
    private volatile boolean cancelDownload;
    private volatile boolean cancelInstall;
    private volatile boolean stagingInstall;
    private boolean waitingForInstallPermission;
    private long readyDownloadId = -1L;
    private ReleaseInfo readyRelease;
    private Uri pendingTestApkUri;
    private static final long MAX_UPDATE_APK_BYTES = 512L * 1024L * 1024L;

    AppUpdater(Activity activity) {
        this.activity = activity;
        this.betaChannel = activity.getPackageName().endsWith(".dev");
        this.card = new UpdateCardController(activity);
    }

    void check(boolean manual) {
        if (checking) {
            if (manual) {
                ZeroChillToast.makeText(activity, "Already checking for updates…", ZeroChillToast.LENGTH_SHORT).show();
            }
            return;
        }

        SharedPreferences prefs = activity.getSharedPreferences("app_prefs", Activity.MODE_PRIVATE);
        if (!manual && !prefs.getBoolean(NotificationCoordinator.PREF_UPDATE_ALERTS, true)) return;
        String key = betaChannel ? "beta_last_update_check" : "stable_last_update_check";
        long now = System.currentTimeMillis();
        long last = prefs.getLong(key, 0L);
        if (!manual && now - last < CHECK_INTERVAL_MS) return;
        prefs.edit().putLong(key, now).apply();

        checking = true;
        if (manual) ZeroChillToast.makeText(activity, "Checking for updates…", ZeroChillToast.LENGTH_SHORT).show();
        io.execute(() -> {
            try {
                ReleaseInfo release = betaChannel ? fetchLatestBeta() : fetchStable();
                String current = currentVersion();
                boolean newer = release != null && compareVersions(release.version, current) > 0;
                activity.runOnUiThread(() -> {
                    checking = false;
                    if (newer) {
                        if (!manual) {
                            NotificationCoordinator.showUpdateNotification(
                                    activity,
                                    release.version,
                                    release.title,
                                    release.beta
                            );
                        }
                        if (manual || !isCardSnoozed(release.version)) {
                            showUpdateCard(release, current);
                        }
                    } else if (manual) {
                        if (betaChannel) {
                            String latest = release == null ? "not found" : release.version;
                            ZeroChillToast.makeText(
                                    activity,
                                    "No newer beta found. Installed: " + current + " • Latest beta: " + latest,
                                    ZeroChillToast.LENGTH_LONG
                            ).show();
                        } else {
                            ZeroChillToast.makeText(activity, "You're up to date.", ZeroChillToast.LENGTH_SHORT).show();
                        }
                    }
                });
            } catch (Exception e) {
                activity.runOnUiThread(() -> {
                    checking = false;
                    if (manual) {
                        ZeroChillToast.makeText(
                                activity,
                                "Couldn't check for updates right now.",
                                ZeroChillToast.LENGTH_SHORT
                        ).show();
                    }
                });
            }
        });
    }

    void onHostResume() {
        if (betaChannel && consumePreviewRequest()) {
            previewUpdateExperience();
        }
        if (!waitingForInstallPermission) return;
        waitingForInstallPermission = false;
        if (activity.getPackageManager().canRequestPackageInstalls()) {
            if (pendingTestApkUri != null) {
                stageSelectedApk(pendingTestApkUri);
            } else {
                stageUpdateForAndroid(readyDownloadId, readyRelease);
            }
        } else if (pendingTestApkUri != null) {
            card.showInstallFallback("test APK", this::openDownloads,
                    () -> installPickedTestApk(pendingTestApkUri));
        } else if (readyRelease != null) {
            card.showInstallFallback(readyRelease.version, this::openDownloads,
                    () -> requestInstall(readyDownloadId, readyRelease));
        }
    }

    void close() {
        card.detachImmediately();
        io.shutdownNow();
    }

    static void requestPreviewOnNextResume(Context context) {
        if (context == null || !context.getPackageName().endsWith(".dev")) return;
        context.getSharedPreferences("app_prefs", Context.MODE_PRIVATE)
                .edit()
                .putBoolean(PREF_PREVIEW_NEXT_RESUME, true)
                .apply();
    }

    private boolean consumePreviewRequest() {
        SharedPreferences prefs = activity.getSharedPreferences("app_prefs", Activity.MODE_PRIVATE);
        if (!prefs.getBoolean(PREF_PREVIEW_NEXT_RESUME, false)) return false;
        prefs.edit().remove(PREF_PREVIEW_NEXT_RESUME).apply();
        return true;
    }

    private void previewUpdateExperience() {
        if (!betaChannel) return;
        ReleaseInfo preview = new ReleaseInfo(
                "4.3.1 preview",
                "ShitTok social polish and a smoother in-app updater.",
                "ShitTok social polish and a smoother in-app updater.",
                "",
                "",
                true,
                true
        );
        card.showAvailable(
                preview.version,
                preview.summary,
                () -> simulatePreviewDownload(preview),
                card::dismiss
        );
    }

    private void simulatePreviewDownload(ReleaseInfo preview) {
        cancelDownload = false;
        io.execute(() -> {
            long total = 14_800_000L;
            for (int percent = 0; percent <= 100; percent += 2) {
                if (cancelDownload || Thread.currentThread().isInterrupted()) {
                    activity.runOnUiThread(() -> showUpdateCard(preview, currentVersion()));
                    return;
                }
                long downloaded = total * percent / 100L;
                final int shown = percent;
                activity.runOnUiThread(() ->
                        card.showDownloading(
                                preview.version,
                                shown,
                                downloaded,
                                total,
                                () -> cancelDownload = true
                        )
                );
                try {
                    Thread.sleep(70L);
                } catch (InterruptedException interrupted) {
                    Thread.currentThread().interrupt();
                    return;
                }
            }
            activity.runOnUiThread(() ->
                    card.showPreviewReady(preview.version, card::dismiss)
            );
        });
    }

    private ReleaseInfo fetchStable() throws Exception {
        JSONObject release = new JSONObject(httpGetFirst(ZeroChillReleaseEndpoints.stableApis()));
        if (release.optBoolean("draft", false)) return null;
        return parseRelease(release, false);
    }

    private ReleaseInfo fetchLatestBeta() throws Exception {
        JSONArray releases = new JSONArray(httpGetFirst(ZeroChillReleaseEndpoints.releasesApis()));
        ReleaseInfo latest = null;
        for (int i = 0; i < releases.length(); i++) {
            JSONObject release = releases.optJSONObject(i);
            if (release == null || release.optBoolean("draft", false)) continue;
            if (!release.optBoolean("prerelease", false)) continue;
            String tag = release.optString("tag_name", "");
            if (!tag.toLowerCase(Locale.US).contains("beta")) continue;
            ReleaseInfo info = parseRelease(release, true);
            if (info == null) continue;
            if (latest == null || compareVersions(info.version, latest.version) > 0) {
                latest = info;
            }
        }
        return latest;
    }

    private ReleaseInfo parseRelease(JSONObject release, boolean beta) {
        String tag = release.optString("tag_name", "").replaceFirst("^[vV]", "");
        if (tag.isEmpty()) return null;

        JSONArray assets = release.optJSONArray("assets");
        String apkName = "";
        String apkUrl = "";
        if (assets != null) {
            for (int i = 0; i < assets.length(); i++) {
                JSONObject asset = assets.optJSONObject(i);
                if (asset == null) continue;
                String name = asset.optString("name", "");
                if (!name.toLowerCase(Locale.US).endsWith(".apk")) continue;
                String lower = name.toLowerCase(Locale.US);
                if (beta && !(lower.contains("test") || lower.contains("beta"))) continue;
                if (!beta && lower.contains("test")) continue;
                apkName = name;
                apkUrl = asset.optString("browser_download_url", "").trim();
                if (!apkUrl.isEmpty()) break;
            }
        }
        if (apkUrl.isEmpty()) return null;

        String title = release.optString("name", tag);
        String summary = ReleaseNotesSummary.fromMarkdown(
                release.optString("body", ""),
                title,
                tag
        );
        return new ReleaseInfo(tag, title, summary, apkName, apkUrl, beta, false);
    }

    private String httpGetFirst(List<String> addresses) throws Exception {
        Exception lastError = null;
        for (String address : addresses) {
            try {
                return httpGet(address);
            } catch (Exception error) {
                lastError = error;
            }
        }
        if (lastError != null) throw lastError;
        throw new Exception("No update endpoint was available");
    }

    private String httpGet(String address) throws Exception {
        HttpURLConnection connection = null;
        try {
            connection = (HttpURLConnection) new URL(address).openConnection();
            connection.setUseCaches(false);
            connection.setConnectTimeout(10000);
            connection.setReadTimeout(10000);
            connection.setRequestProperty("Accept", "application/vnd.github+json");
            connection.setRequestProperty("User-Agent", "ZeroChill-Android");
            connection.setRequestProperty("Cache-Control", "no-cache, no-store, max-age=0");
            connection.setRequestProperty("Pragma", "no-cache");
            int code = connection.getResponseCode();
            if (code < 200 || code >= 300) throw new Exception("HTTP " + code);
            InputStream in = connection.getInputStream();
            byte[] bytes = readAll(in);
            in.close();
            return new String(bytes, java.nio.charset.StandardCharsets.UTF_8);
        } finally {
            if (connection != null) connection.disconnect();
        }
    }

    private byte[] readAll(InputStream in) throws Exception {
        java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream();
        byte[] buffer = new byte[8192];
        int read;
        while ((read = in.read(buffer)) >= 0) out.write(buffer, 0, read);
        return out.toByteArray();
    }

    private void showUpdateCard(ReleaseInfo release, String current) {
        AnalyticsTracker.event(activity,"update_prompt");
        String summary = release.summary == null ? "" : release.summary.trim();
        if (summary.isEmpty()) {
            String channel = release.beta ? "beta" : "stable";
            summary = "Installed " + current + " · " + channel + " update";
        }
        String finalSummary = summary;
        card.showAvailable(
                release.version,
                finalSummary,
                () -> downloadWithSystem(release),
                () -> {
                    snoozeCard(release.version);
                    card.dismiss();
                }
        );
    }

    private void downloadWithSystem(ReleaseInfo release) {
        if (release == null) return;
        if (release.preview) {
            simulatePreviewDownload(release);
            return;
        }

        DownloadManager manager = (DownloadManager) activity.getSystemService(Context.DOWNLOAD_SERVICE);
        if (manager == null) {
            card.showError(release.version, () -> downloadWithSystem(release), card::dismiss);
            return;
        }

        cancelDownload = false;
        readyDownloadId = -1L;
        readyRelease = null;
        card.showDownloading(release.version, 0, 0L, -1L, () -> cancelDownload = true);

        try {
            String cleanVersion = release.version == null
                    ? "update"
                    : release.version.replaceAll("[^A-Za-z0-9._-]", "_");
            String fileName = "ZeroChill-" + cleanVersion + "-" + System.currentTimeMillis() + ".apk";
            DownloadManager.Request request = new DownloadManager.Request(Uri.parse(release.apkUrl))
                    .setTitle("ZEROCHILL " + release.version)
                    .setDescription("ZEROCHILL update")
                    .setMimeType("application/vnd.android.package-archive")
                    .setAllowedOverMetered(true)
                    .setAllowedOverRoaming(true)
                    .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
                    .setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, fileName);
            request.addRequestHeader("User-Agent", "ZeroChill-Android");

            long downloadId = manager.enqueue(request);
            io.execute(() -> monitorSystemDownload(manager, downloadId, release));
        } catch (Exception error) {
            card.showError(release.version, () -> downloadWithSystem(release), card::dismiss);
        }
    }

    private void monitorSystemDownload(DownloadManager manager, long downloadId, ReleaseInfo release) {
        try {
            while (!Thread.currentThread().isInterrupted()) {
                if (cancelDownload) {
                    manager.remove(downloadId);
                    activity.runOnUiThread(() -> showUpdateCard(release, currentVersion()));
                    return;
                }

                DownloadManager.Query query = new DownloadManager.Query().setFilterById(downloadId);
                try (Cursor cursor = manager.query(query)) {
                    if (cursor == null || !cursor.moveToFirst()) {
                        throw new Exception("Download no longer available");
                    }

                    int status = cursor.getInt(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_STATUS));
                    long downloaded = cursor.getLong(
                            cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_BYTES_DOWNLOADED_SO_FAR)
                    );
                    long total = cursor.getLong(
                            cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_TOTAL_SIZE_BYTES)
                    );
                    int percent = total > 0L
                            ? (int) Math.min(100L, downloaded * 100L / total)
                            : 0;

                    final long shownDownloaded = downloaded;
                    final long shownTotal = total;
                    final int shownPercent = percent;
                    activity.runOnUiThread(() ->
                            card.showDownloading(
                                    release.version,
                                    shownPercent,
                                    shownDownloaded,
                                    shownTotal,
                                    () -> cancelSystemDownload(manager, downloadId, release)
                            )
                    );

                    if (status == DownloadManager.STATUS_SUCCESSFUL) {
                        activity.runOnUiThread(() -> {
                            readyDownloadId = downloadId;
                            readyRelease = release;
                            card.showReady(release.version,
                                    () -> requestInstall(downloadId, release),
                                    this::openDownloads);
                        });
                        return;
                    }
                    if (status == DownloadManager.STATUS_FAILED) {
                        int reason = cursor.getInt(
                                cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_REASON)
                        );
                        throw new Exception("Download failed: " + reason);
                    }
                }

                Thread.sleep(350L);
            }
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
        } catch (Exception error) {
            activity.runOnUiThread(() ->
                    card.showError(
                            release.version,
                            () -> downloadWithSystem(release),
                            card::dismiss
                    )
            );
        }
    }

    private void cancelSystemDownload(DownloadManager manager, long downloadId, ReleaseInfo release) {
        cancelDownload = true;
        try {
            manager.remove(downloadId);
        } catch (Exception ignored) {
        }
        showUpdateCard(release, currentVersion());
    }


    /**
     * Only an explicit tap starts the Android installer. DownloadManager remains
     * responsible for the download and its notification; Files/Downloads remains
     * an option if the permission or install session is unavailable.
     */
    private void requestInstall(long downloadId, ReleaseInfo release) {
        if (release == null || downloadId < 0 || stagingInstall) return;
        readyDownloadId = downloadId;
        readyRelease = release;
        if (!activity.getPackageManager().canRequestPackageInstalls()) {
            waitingForInstallPermission = true;
            try {
                activity.startActivity(new Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                        Uri.parse("package:" + activity.getPackageName())));
            } catch (Exception error) {
                waitingForInstallPermission = false;
                card.showInstallFallback(release.version, this::openDownloads,
                        () -> requestInstall(downloadId, release));
            }
            return;
        }
        stageUpdateForAndroid(downloadId, release);
    }

    static boolean isInstallerTestBuild() {
        return isInstallerTestVersion(BuildConfig.VERSION_NAME);
    }

    static boolean isInstallerTestVersion(String versionName) {
        return versionName != null
                && versionName.startsWith("7.2.1-installer-beta.");
    }

    /**
     * Private two-APK test harness only. A Files-picked APK enters the same
     * verified PackageInstaller session as downloaded production APKs.
     */
    void installPickedTestApk(Uri apkUri) {
        if (!isInstallerTestBuild() || apkUri == null
                || !"content".equalsIgnoreCase(apkUri.getScheme())) {
            return;
        }
        pendingTestApkUri = apkUri;
        readyRelease = null;
        readyDownloadId = -1L;
        if (!activity.getPackageManager().canRequestPackageInstalls()) {
            waitingForInstallPermission = true;
            try {
                activity.startActivity(new Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                        Uri.parse("package:" + activity.getPackageName())));
            } catch (Exception error) {
                waitingForInstallPermission = false;
                card.showInstallFallback("test APK", this::openDownloads,
                        () -> installPickedTestApk(apkUri));
            }
            return;
        }
        stageSelectedApk(apkUri);
    }

    private void stageSelectedApk(Uri uri) {
        if (uri == null || stagingInstall || !isInstallerTestBuild()) return;
        stagingInstall = true;
        cancelInstall = false;
        card.showPreparing("test APK", () -> cancelInstall = true);
        io.execute(() -> {
            try {
                commitApkUri(uri, -1L);
                activity.runOnUiThread(card::dismiss);
            } catch (Exception error) {
                Log.w("ZeroChillInstaller", "Test APK installation staging failed", error);
                activity.runOnUiThread(() -> {
                    if (error instanceof InterruptedException) {
                        card.dismiss();
                    } else {
                        card.showInstallFallback("test APK", this::openDownloads,
                                () -> installPickedTestApk(uri));
                    }
                });
            } finally {
                stagingInstall = false;
            }
        });
    }

    private void stageUpdateForAndroid(long downloadId, ReleaseInfo release) {
        if (release == null || downloadId < 0 || stagingInstall) return;
        DownloadManager manager = (DownloadManager) activity.getSystemService(Context.DOWNLOAD_SERVICE);
        if (manager == null) {
            card.showInstallFallback(release.version, this::openDownloads,
                    () -> requestInstall(downloadId, release));
            return;
        }
        stagingInstall = true;
        cancelInstall = false;
        card.showPreparing(release.version, () -> cancelInstall = true);
        io.execute(() -> {
            try {
                long expectedSize;
                try (Cursor cursor = manager.query(
                        new DownloadManager.Query().setFilterById(downloadId))) {
                    if (cursor == null || !cursor.moveToFirst()
                            || cursor.getInt(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_STATUS))
                                    != DownloadManager.STATUS_SUCCESSFUL) {
                        throw new IOException("Update download is missing or incomplete");
                    }
                    expectedSize = cursor.getLong(
                            cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_TOTAL_SIZE_BYTES));
                }
                Uri uri = manager.getUriForDownloadedFile(downloadId);
                if (uri == null) throw new IOException("Downloaded APK is unavailable");
                commitApkUri(uri, expectedSize);
                activity.runOnUiThread(card::dismiss);
            } catch (Exception error) {
                Log.w("ZeroChillInstaller", "Downloaded APK installation failed", error);
                activity.runOnUiThread(() -> {
                    if (error instanceof InterruptedException) {
                        card.showReady(release.version,
                                () -> requestInstall(downloadId, release), this::openDownloads);
                    } else {
                        card.showInstallFallback(release.version, this::openDownloads,
                                () -> requestInstall(downloadId, release));
                    }
                });
            } finally {
                stagingInstall = false;
            }
        });
    }

    /**
     * Shared installer path for a DownloadManager URI or a private test file
     * selected through Android Files. Android still handles user confirmation
     * and the final signing-certificate / update compatibility checks.
     */
    private void commitApkUri(Uri uri, long expectedSize) throws Exception {
        File apk = File.createTempFile("zerochill-update-", ".apk", activity.getCacheDir());
        try {
            long copied = 0L;
            try (InputStream input = activity.getContentResolver().openInputStream(uri)) {
                if (input == null) throw new IOException("Cannot read update APK");
                try (FileOutputStream output = new FileOutputStream(apk)) {
                    byte[] buffer = new byte[64 * 1024];
                    int n;
                    while ((n = input.read(buffer)) != -1) {
                        if (cancelInstall || Thread.currentThread().isInterrupted()) {
                            throw new InterruptedException("Install staging cancelled");
                        }
                        copied += n;
                        if (copied > MAX_UPDATE_APK_BYTES || (expectedSize > 0 && copied > expectedSize)) {
                            throw new IOException("Downloaded APK exceeds expected size");
                        }
                        output.write(buffer, 0, n);
                    }
                    output.getFD().sync();
                }
            }
            if (!validDownloadedSize(copied, expectedSize)) {
                throw new IOException("APK size differs from completed download");
            }

            PackageManager pm = activity.getPackageManager();
            PackageInfo archive = pm.getPackageArchiveInfo(apk.getAbsolutePath(), 0);
            PackageInfo installed = pm.getPackageInfo(activity.getPackageName(), 0);
            if (archive == null || !activity.getPackageName().equals(archive.packageName)) {
                throw new IOException("APK belongs to another application");
            }
            long archiveVersion = Build.VERSION.SDK_INT >= 28
                    ? archive.getLongVersionCode() : archive.versionCode;
            long installedVersion = Build.VERSION.SDK_INT >= 28
                    ? installed.getLongVersionCode() : installed.versionCode;
            if (!isNewerPackageVersion(archiveVersion, installedVersion)) {
                throw new IOException("APK is not newer than the installed version");
            }

            PackageInstaller installer = pm.getPackageInstaller();
            int sessionId = -1;
            boolean committed = false;
            try {
                PackageInstaller.SessionParams params = new PackageInstaller.SessionParams(
                        PackageInstaller.SessionParams.MODE_FULL_INSTALL);
                params.setAppPackageName(activity.getPackageName());
                params.setInstallReason(PackageManager.INSTALL_REASON_USER);
                params.setSize(copied);
                if (Build.VERSION.SDK_INT >= 31) {
                    params.setRequireUserAction(
                            PackageInstaller.SessionParams.USER_ACTION_REQUIRED);
                }
                sessionId = installer.createSession(params);
                try (PackageInstaller.Session session = installer.openSession(sessionId)) {
                    try (InputStream input = new java.io.FileInputStream(apk);
                         OutputStream output = session.openWrite("base.apk", 0, copied)) {
                        byte[] buffer = new byte[64 * 1024];
                        int n;
                        while ((n = input.read(buffer)) != -1) {
                            if (cancelInstall || Thread.currentThread().isInterrupted()) {
                                throw new InterruptedException("Install staging cancelled");
                            }
                            output.write(buffer, 0, n);
                        }
                        session.fsync(output);
                    }
                    if (cancelInstall) throw new InterruptedException("Install staging cancelled");
                    Intent callback = new Intent(activity, UpdateInstallReceiver.class)
                            .setAction("com.webapp.crazyshit.UPDATE_INSTALL_RESULT");
                    PendingIntent status = PendingIntent.getBroadcast(activity, sessionId, callback,
                            PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_MUTABLE);
                    session.commit(status.getIntentSender());
                    committed = true;
                }
            } finally {
                if (!committed && sessionId >= 0) {
                    try {
                        installer.abandonSession(sessionId);
                    } catch (Exception ignored) {
                    }
                }
            }
        } finally {
            // PackageInstaller owns its staged copy after commit.
            if (!apk.delete()) apk.deleteOnExit();
        }
    }

    static boolean isNewerPackageVersion(long candidate, long installed) {
        return candidate > installed;
    }

    static boolean validDownloadedSize(long written, long expected) {
        return written > 0 && (expected <= 0 || written == expected);
    }

    private void openDownloads() {
        try {
            Intent downloads = new Intent(DownloadManager.ACTION_VIEW_DOWNLOADS);
            activity.startActivity(downloads);
            card.dismiss();
        } catch (Exception error) {
            ZeroChillToast.makeText(
                    activity,
                    "Update downloaded. Open Downloads and tap the ZEROCHILL APK to install it.",
                    ZeroChillToast.LENGTH_LONG
            ).show();
        }
    }

    private boolean isCardSnoozed(String version) {
        SharedPreferences prefs = activity.getSharedPreferences("app_prefs", Activity.MODE_PRIVATE);
        return version != null
                && version.equals(prefs.getString(PREF_CARD_LATER_VERSION, ""))
                && System.currentTimeMillis() < prefs.getLong(PREF_CARD_LATER_UNTIL, 0L);
    }

    private void snoozeCard(String version) {
        activity.getSharedPreferences("app_prefs", Activity.MODE_PRIVATE)
                .edit()
                .putString(PREF_CARD_LATER_VERSION, version == null ? "" : version)
                .putLong(PREF_CARD_LATER_UNTIL, System.currentTimeMillis() + CARD_LATER_MS)
                .apply();
    }

    private String currentVersion() {
        try {
            PackageInfo info = activity.getPackageManager().getPackageInfo(activity.getPackageName(), 0);
            return info.versionName == null ? "0" : info.versionName;
        } catch (Exception e) {
            return "0";
        }
    }

    private int compareVersions(String left, String right) {
        List<Integer> a = numbers(left);
        List<Integer> b = numbers(right);
        int count = Math.max(a.size(), b.size());
        for (int i = 0; i < count; i++) {
            int av = i < a.size() ? a.get(i) : 0;
            int bv = i < b.size() ? b.get(i) : 0;
            if (av != bv) return Integer.compare(av, bv);
        }
        return 0;
    }

    private List<Integer> numbers(String value) {
        ArrayList<Integer> out = new ArrayList<>();
        Matcher matcher = NUMBER.matcher(value == null ? "" : value);
        while (matcher.find()) {
            try {
                out.add(Integer.parseInt(matcher.group()));
            } catch (Exception ignored) {
            }
        }
        return out;
    }

    private static final class ReleaseInfo {
        final String version;
        final String title;
        final String summary;
        final String apkName;
        final String apkUrl;
        final boolean beta;
        final boolean preview;

        ReleaseInfo(
                String version,
                String title,
                String summary,
                String apkName,
                String apkUrl,
                boolean beta,
                boolean preview
        ) {
            this.version = version;
            this.title = title;
            this.summary = summary;
            this.apkName = apkName;
            this.apkUrl = apkUrl;
            this.beta = beta;
            this.preview = preview;
        }
    }
}
