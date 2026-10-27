package com.webapp.crazyshit;

import android.app.Activity;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;

import androidx.media3.common.PlaybackException;

import java.time.Instant;

/** User-copyable diagnostics for Bunkr playback and refresh failures. */
final class BunkrPlaybackDiagnostics {
    private BunkrPlaybackDiagnostics() { }

    static String report(
            String phase,
            String pageUrl,
            String mediaUrl,
            PlaybackException playbackError,
            Throwable refreshError,
            int playbackState,
            long playbackPositionMs
    ) {
        StringBuilder report = new StringBuilder();
        report.append("ZEROCHILL ").append(BuildConfig.VERSION_NAME)
                .append(" (").append(BuildConfig.VERSION_CODE).append(")")
                .append("\nSource: ").append(BuildConfig.SOURCE_REVISION)
                .append("\nAndroid ").append(android.os.Build.VERSION.RELEASE)
                .append(" / ").append(android.os.Build.MODEL)
                .append("\nCaptured: ").append(Instant.now())
                .append("\n\nBunkr playback failure")
                .append("\nPhase: ").append(value(phase))
                .append("\nItem page: ").append(safeUrl(pageUrl));

        if (!value(mediaUrl).isEmpty()) {
            Uri parsed = Uri.parse(mediaUrl);
            report.append("\nMedia: ").append(safeUrl(mediaUrl));
            if (parsed.getHost() != null) report.append("\nMedia host: ").append(parsed.getHost());
            String path = value(parsed.getPath()).toLowerCase(java.util.Locale.US);
            report.append("\nMP4 repair wrapper eligible: ").append(path.endsWith(".mp4") ? "yes" : "no");
        } else {
            report.append("\nMedia: unavailable before failure");
        }
        report.append("\nPlayer state: ").append(playerState(playbackState))
                .append("\nPlayer position: ").append(Math.max(0L, playbackPositionMs)).append(" ms");

        if (playbackError != null) {
            report.append("\nMedia3 error: ").append(playbackError.getErrorCodeName())
                    .append(" (").append(playbackError.errorCode).append(")");
            appendCauses(report, "Playback", playbackError);
        }
        if (refreshError != null) appendCauses(report, "Refresh", refreshError);
        return report.toString();
    }

    static void show(Activity activity, String report) {
        if (activity == null || activity.isFinishing() || report == null || report.isEmpty()) return;
        new ZeroChillDialog.Builder(activity)
                .setTitle("Bunkr playback diagnostics")
                .setMessage(report)
                .setPositiveButton("Copy", (dialog, which) -> copy(activity, report))
                .setNeutralButton("Share", (dialog, which) -> share(activity, report))
                .setNegativeButton("Close", null)
                .show();
    }

    private static void copy(Activity activity, String report) {
        ClipboardManager clipboard = (ClipboardManager) activity.getSystemService(
                Context.CLIPBOARD_SERVICE
        );
        if (clipboard == null) {
            ZeroChillToast.makeText(activity, "Clipboard isn't available.", ZeroChillToast.LENGTH_SHORT).show();
            return;
        }
        clipboard.setPrimaryClip(ClipData.newPlainText("ZEROCHILL Bunkr diagnostics", report));
        ZeroChillToast.makeText(activity, "Bunkr diagnostics copied.", ZeroChillToast.LENGTH_SHORT).show();
    }

    private static void share(Activity activity, String report) {
        Intent share = new Intent(Intent.ACTION_SEND);
        share.setType("text/plain");
        share.putExtra(Intent.EXTRA_SUBJECT, "ZEROCHILL Bunkr playback diagnostics");
        share.putExtra(Intent.EXTRA_TEXT, report);
        activity.startActivity(Intent.createChooser(share, "Share diagnostics"));
    }

    private static void appendCauses(StringBuilder report, String label, Throwable error) {
        Throwable current = error;
        int depth = 0;
        while (current != null && depth < 8) {
            report.append("\n").append(label).append(" cause ").append(depth).append(": ")
                    .append(current.getClass().getName());
            String message = sanitizeText(current.getMessage());
            if (!message.isEmpty()) report.append(": ").append(message);
            current = current.getCause();
            depth++;
        }
    }

    private static String safeUrl(String raw) {
        String clean = value(raw);
        int query = clean.indexOf('?');
        int fragment = clean.indexOf('#');
        int cut = clean.length();
        if (query >= 0) cut = Math.min(cut, query);
        if (fragment >= 0) cut = Math.min(cut, fragment);
        return clean.substring(0, cut);
    }

    private static String sanitizeText(String raw) {
        return value(raw).replaceAll(
                "(https?://[^\\s?#]+)(?:\\?[^\\s#]*)?(?:#[^\\s]*)?",
                "$1"
        );
    }

    private static String playerState(int state) {
        switch (state) {
            case 1: return "IDLE";
            case 2: return "BUFFERING";
            case 3: return "READY";
            case 4: return "ENDED";
            default: return String.valueOf(state);
        }
    }

    private static String value(String value) {
        return value == null ? "" : value.trim();
    }
}
