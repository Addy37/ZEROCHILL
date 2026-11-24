package com.webapp.crazyshit;

import android.content.Context;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/** Local synthetic metadata only. No provider client, HTTP calls, media or real URLs. */
final class AlbumSearchFixture {
    static final String ASSET = "album_search_fixture.tsv";
    private static final String PREFIX = "fixture://album/";
    private static final int LIMIT = 48;
    private AlbumSearchFixture() { }

    static boolean available(Context context) {
        if (!context.getPackageName().endsWith(".dev")) return false;
        try (java.io.InputStream ignored = context.getAssets().open(ASSET)) {
            return true;
        } catch (IOException unavailable) { return false; }
    }

    static boolean isSample(String id) { return id != null && id.startsWith(PREFIX); }

    static List<NativeContentItem> search(Context context, String query) throws IOException {
        if (!available(context)) return Collections.emptyList();
        try (Reader reader = new InputStreamReader(context.getAssets().open(ASSET), StandardCharsets.UTF_8)) {
            return read(reader, query);
        }
    }

    static List<NativeContentItem> read(Reader source, String query) throws IOException {
        String q = query == null ? "" : query.trim().toLowerCase(Locale.ROOT);
        if (q.length() < 2 || q.length() > 100) return Collections.emptyList();
        String[] tokens = q.split("\\s+");
        BufferedReader reader = new BufferedReader(source);
        List<NativeContentItem> results = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        String line;
        int rows = 0;
        while ((line = reader.readLine()) != null && rows++ < 200 && results.size() < LIMIT) {
            String[] fields = line.split("\\t", -1);
            if (fields.length != 6 || !fields[0].matches("[A-Za-z0-9_-]{1,48}")) continue;
            String title = fields[1].trim(), uploader = fields[2].trim(), tags = fields[5].trim();
            if (title.isEmpty() || uploader.isEmpty() || title.length() > 150 || uploader.length() > 100 || tags.length() > 200) continue;
            int photos, videos;
            try { photos = Integer.parseInt(fields[3]); videos = Integer.parseInt(fields[4]); }
            catch (NumberFormatException malformed) { continue; }
            if (photos < 0 || videos < 0 || photos > 100 || videos > 100 || photos + videos > 100 || photos + videos == 0) continue;
            String corpus = (title + " " + uploader + " " + tags).toLowerCase(Locale.ROOT);
            boolean match = true;
            for (String token : tokens) if (!corpus.contains(token)) { match = false; break; }
            if (!match || !seen.add(fields[0])) continue;
            results.add(new NativeContentItem(NativeContentItem.KIND_SERIES, title, PREFIX + fields[0],
                    "", "", uploader, "", photos + " pictures · " + videos + " videos", tags));
        }
        return results;
    }
}
