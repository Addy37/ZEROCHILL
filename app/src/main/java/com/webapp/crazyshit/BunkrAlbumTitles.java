package com.webapp.crazyshit;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;

/** Display-only filename cleanup. Source items and persisted titles are never rewritten. */
final class BunkrAlbumTitles {
    private BunkrAlbumTitles() { }

    static String display(NativeContentItem item) {
        if (item == null) return "";
        String original = item.title;
        if (!item.isVideo() || !BunkrRepository.isBunkrUrl(item.url)) return original;
        String title = original.replaceFirst("(?i)\\.(mp4|m4v|mov|webm|mkv|avi|mpeg|mpg|wmv|ts)\\s*$", "");
        boolean filename = !title.equals(original) || title.contains("_");
        if (!filename) return original;
        title = title.replaceAll("_+", " ");
        // Preserve decimal numbers and punctuation in prose; separate filename word dots.
        title = title.replaceAll("(?<=\\p{L})\\.(?=[\\p{L}\\d])|(?<=\\d)\\.(?=\\p{L})", " ")
                .replaceAll("\\s+", " ").trim();
        return title.isEmpty() ? original : title;
    }

    static void showOriginal(Context context, NativeContentItem item) {
        new ZeroChillDialog.Builder(context).setTitle("Original filename")
                .setMessage(item.title)
                .setPositiveButton("Copy", (dialog, which) -> {
                    ((ClipboardManager) context.getSystemService(Context.CLIPBOARD_SERVICE))
                            .setPrimaryClip(ClipData.newPlainText("Filename", item.title));
                    ZeroChillToast.makeText(context, "Filename copied", ZeroChillToast.LENGTH_SHORT).show();
                })
                .setNegativeButton("Close", null).show();
    }
}
