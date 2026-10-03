package com.webapp.crazyshit;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageInfo;
import android.net.Uri;
import android.os.Build;
import android.provider.Settings;
import android.widget.Toast;

import androidx.core.content.FileProvider;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
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
    private File pendingInstall;
    private boolean waitingForInstallPermission;
    private ReleaseInfo activeRelease;

    AppUpdater(Activity activity) {
        this.activity = activity;
        this.betaChannel = activity.getPackageName().endsWith(".dev");
        this.card = new UpdateCardController(activity);
    }

    void check(boolean manual) {
        if (checking) {
            if (manual) {
                Toast.makeText(activity, "Already checking for updates…", Toast.LENGTH_SHORT).show();
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
        if (manual) Toast.makeText(activity, "Checking for updates…", Toast.LENGTH_SHORT).show();
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
                            Toast.makeText(
                                    activity,
                                    "No newer beta found. Installed: " + current + " • Latest beta: " + latest,
                                    Toast.LENGTH_LONG
                            ).show();
                        } else {
                            Toast.makeText(activity, "You're up to date.", Toast.LENGTH_SHORT).show();
                        }
                    }
                });
            } catch (Exception e) {
                activity.runOnUiThread(() -> {
                    checking = false;
                    if (manual) {
                        Toast.makeText(
                                activity,
                                "Couldn't check for updates right now.",
                                Toast.LENGTH_SHORT
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
        if (!waitingForInstallPermission || pendingInstall == null) return;
        if (Build.VERSION.SDK_INT < 26 || activity.getPackageManager().canRequestPackageInstalls()) {
            waitingForInstallPermission = false;
            launchInstaller(pendingInstall);
        }
    }

    void close() {
        cancelDownload = true;
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
                "",
                "",
                "",
                true,
                true
        );
        activeRelease = preview;
        card.showAvailable(
                preview.version,
                preview.title,
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
                apkUrl = asset.optString("browser_download_url", "");
                if (!apkUrl.isEmpty()) break;
            }
        }
        if (apkUrl.isEmpty()) return null;

        String title = release.optString("name", tag);
        String page = release.optString("html_url", "");
        return new ReleaseInfo(tag, title, apkName, apkUrl, page, beta, false);
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
        activeRelease = release;
        String channel = release.beta ? "beta" : "stable";
        String summary = release.title == null ? "" : release.title.trim();
        if (summary.isEmpty() || summary.equalsIgnoreCase(release.version)) {
            summary = "Installed " + current + " · " + channel + " update";
        }
        String finalSummary = summary;
        card.showAvailable(
                release.version,
                finalSummary,
                () -> downloadAndStage(release),
                () -> {
                    snoozeCard(release.version);
                    card.dismiss();
                }
        );
    }

    private void downloadAndStage(ReleaseInfo release) {
        if (release == null) return;
        if (release.preview) {
            simulatePreviewDownload(release);
            return;
        }
        cancelDownload = false;
        activeRelease = release;
        card.showDownloading(release.version, 0, 0L, -1L, () -> cancelDownload = true);

        io.execute(() -> {
            HttpURLConnection connection = null;
            try {
                File dir = new File(activity.getCacheDir(), "updates");
                if (!dir.exists() && !dir.mkdirs()) {
                    throw new Exception("Couldn't create update folder");
                }
                File target = new File(dir, "ZeroChill-update.apk");
                if (target.exists()) target.delete();

                connection = (HttpURLConnection) new URL(release.apkUrl).openConnection();
                connection.setInstanceFollowRedirects(true);
                connection.setConnectTimeout(15000);
                connection.setReadTimeout(30000);
                connection.setRequestProperty("User-Agent", "ZeroChill-Android");
                int code = connection.getResponseCode();
                if (code < 200 || code >= 300) throw new Exception("HTTP " + code);

                long total = connection.getContentLengthLong();
                long downloaded = 0L;
                int lastPercent = -1;
                long lastUnknownUpdate = 0L;
                try (InputStream input = new BufferedInputStream(connection.getInputStream());
                     FileOutputStream output = new FileOutputStream(target)) {
                    byte[] buffer = new byte[32 * 1024];
                    int read;
                    while ((read = input.read(buffer)) >= 0) {
                        if (cancelDownload) throw new InterruptedException("Cancelled");
                        output.write(buffer, 0, read);
                        downloaded += read;

                        int percent = total > 0
                                ? (int) Math.min(100L, downloaded * 100L / total)
                                : 0;
                        boolean shouldUpdate = total > 0
                                ? percent != lastPercent
                                : downloaded - lastUnknownUpdate >= 256L * 1024L;
                        if (shouldUpdate) {
                            lastPercent = percent;
                            lastUnknownUpdate = downloaded;
                            final int p = percent;
                            final long bytes = downloaded;
                            final long all = total;
                            activity.runOnUiThread(() ->
                                    card.showDownloading(
                                            release.version,
                                            p,
                                            bytes,
                                            all,
                                            () -> cancelDownload = true
                                    )
                            );
                        }
                    }
                }

                activity.runOnUiThread(() ->
                        card.showPreparing(release.version, () -> cancelDownload = true)
                );
                if (cancelDownload) throw new InterruptedException("Cancelled");
                verifyPackage(target);
                pendingInstall = target;
                activity.runOnUiThread(() ->
                        card.showReady(
                                release.version,
                                () -> requestInstall(target),
                                card::dismiss
                        )
                );
            } catch (InterruptedException cancelled) {
                activity.runOnUiThread(() -> showUpdateCard(release, currentVersion()));
            } catch (Exception error) {
                activity.runOnUiThread(() ->
                        card.showError(
                                release.version,
                                () -> downloadAndStage(release),
                                card::dismiss
                        )
                );
            } finally {
                if (connection != null) connection.disconnect();
            }
        });
    }

    private void verifyPackage(File apk) throws Exception {
        PackageInfo archive = activity.getPackageManager().getPackageArchiveInfo(apk.getAbsolutePath(), 0);
        if (archive == null || archive.packageName == null) throw new Exception("Invalid APK");
        if (!activity.getPackageName().equals(archive.packageName)) {
            throw new Exception("Update package doesn't match installed app");
        }
    }

    private void requestInstall(File apk) {
        if (Build.VERSION.SDK_INT >= 26 && !activity.getPackageManager().canRequestPackageInstalls()) {
            pendingInstall = apk;
            waitingForInstallPermission = true;
            Toast.makeText(
                    activity,
                    "Allow updates from this app, then you'll return automatically.",
                    Toast.LENGTH_LONG
            ).show();
            Intent settings = new Intent(
                    Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                    Uri.parse("package:" + activity.getPackageName())
            );
            activity.startActivity(settings);
            return;
        }
        launchInstaller(apk);
    }

    private void launchInstaller(File apk) {
        try {
            pendingInstall = apk;
            Uri uri = FileProvider.getUriForFile(
                    activity,
                    activity.getPackageName() + ".files",
                    apk
            );
            Intent intent = new Intent(Intent.ACTION_VIEW);
            intent.setDataAndType(uri, "application/vnd.android.package-archive");
            intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            intent.putExtra(Intent.EXTRA_NOT_UNKNOWN_SOURCE, true);
            activity.startActivity(intent);
        } catch (Exception e) {
            Toast.makeText(activity, "Couldn't open Android's installer.", Toast.LENGTH_LONG).show();
            if (activeRelease != null) {
                card.showReady(
                        activeRelease.version,
                        () -> requestInstall(apk),
                        card::dismiss
                );
            }
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
        final String apkName;
        final String apkUrl;
        final String pageUrl;
        final boolean beta;
        final boolean preview;

        ReleaseInfo(
                String version,
                String title,
                String apkName,
                String apkUrl,
                String pageUrl,
                boolean beta,
                boolean preview
        ) {
            this.version = version;
            this.title = title;
            this.apkName = apkName;
            this.apkUrl = apkUrl;
            this.pageUrl = pageUrl;
            this.beta = beta;
            this.preview = preview;
        }
    }
}
