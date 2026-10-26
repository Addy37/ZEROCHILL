package com.webapp.crazyshit;

import android.content.Intent;

import org.json.JSONArray;
import org.json.JSONException;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Small, ephemeral launch context for a Shows watch page. */
@androidx.annotation.OptIn(markerClass = androidx.media3.common.util.UnstableApi.class)
final class ShowsWatchSession {
    static final String EXTRA_ITEMS = "shows_watch_items";
    static final String EXTRA_EPISODIC = "shows_watch_episodic";
    static final String EXTRA_LABEL = "shows_watch_label";
    private static final int MAX_ITEMS = 200;
    private static final int MAX_VISIBLE = 24;
    private static final int MAX_ENCODED_CHARS = 80_000;
    private static final Pattern SEASON_EPISODE = Pattern.compile("(?i)\\bs(?:eason\\s*)?(\\d{1,2})\\s*e(?:p(?:isode)?)?\\s*(\\d{1,3})\\b");
    private static final Pattern EPISODE = Pattern.compile("(?i)\\b(?:episode|ep)\\s*[.#:-]?\\s*(\\d{1,3})\\b");

    final List<NativeContentItem> items;
    final boolean episodic;
    final String label;

    private ShowsWatchSession(List<NativeContentItem> items, boolean episodic, String label) {
        this.items = items;
        this.episodic = episodic;
        this.label = label == null ? "" : label;
    }

    static Intent attach(Intent intent, List<NativeContentItem> items, boolean episodic, String label) {
        intent.putExtra(VideoDetailActivity.EXTRA_SHOWS_ORIGIN, true);
        intent.putExtra(EXTRA_EPISODIC, episodic);
        intent.putExtra(EXTRA_LABEL, label == null ? "" : label);
        JSONArray values = new JSONArray();
        Set<String> seen = new LinkedHashSet<>();
        int selected = -1;
        String currentUrl = intent.getStringExtra(PlayerActivity.EXTRA_PAGE_URL);
        if (items != null) for (int i = 0; i < items.size(); i++)
            if (items.get(i) != null && items.get(i).url.equals(currentUrl)) { selected = i; break; }
        int first = items == null ? 0 : Math.max(0, selected - 8);
        if (items != null) for (int i = first; i < items.size(); i++) {
            NativeContentItem item = items.get(i);
            if (item == null || !ShowsEpisodePlayback.playable(item) || !seen.add(item.url)) continue;
            try {
                NativeContentItem compact = new NativeContentItem(item.kind, item.title, item.url,
                        item.imageUrl, item.views, item.uploader, item.comments);
                String encoded = ContentItemCodec.encode(compact).toString();
                if (values.toString().length() + encoded.length() > MAX_ENCODED_CHARS) break;
                values.put(ContentItemCodec.encode(compact));
            } catch (JSONException ignored) { }
            if (values.length() >= MAX_ITEMS) break;
        }
        intent.putExtra(EXTRA_ITEMS, values.toString());
        return intent;
    }

    static ShowsWatchSession from(Intent intent) {
        List<NativeContentItem> items = new ArrayList<>();
        try {
            String raw = intent.getStringExtra(EXTRA_ITEMS);
            if (raw != null && raw.length() <= MAX_ENCODED_CHARS) {
                for (NativeContentItem item : ContentItemCodec.decodeList(new JSONArray(raw), MAX_ITEMS)) {
                    if (ShowsEpisodePlayback.playable(item)) items.add(item);
                }
            }
        } catch (JSONException ignored) { }
        return new ShowsWatchSession(items, intent.getBooleanExtra(EXTRA_EPISODIC, false),
                intent.getStringExtra(EXTRA_LABEL));
    }

    ShowsWatchSession withFallback(NativeContentItem current, List<NativeContentItem> fetched) {
        List<NativeContentItem> combined = new ArrayList<>(items);
        if (current != null && combined.stream().noneMatch(item -> item.url.equals(current.url)))
            combined.add(0, current);
        if (fetched != null) for (NativeContentItem item : fetched) {
            if (ShowsEpisodePlayback.playable(item) &&
                    combined.stream().noneMatch(existing -> existing.url.equals(item.url))) combined.add(item);
            if (combined.size() >= MAX_ITEMS) break;
        }
        return new ShowsWatchSession(combined, episodic, label);
    }

    List<NativeContentItem> related(String currentUrl) {
        List<NativeContentItem> related = new ArrayList<>();
        int index = -1;
        for (int i = 0; i < items.size(); i++) if (items.get(i).url.equals(currentUrl)) { index = i; break; }
        int start = Math.max(0, index - 5);
        for (int i = start; i < items.size() && related.size() < MAX_VISIBLE; i++) {
            NativeContentItem item = items.get(i);
            if (!item.url.equals(currentUrl)) related.add(item);
        }
        if (related.size() < MAX_VISIBLE) for (int i = 0; i < start && related.size() < MAX_VISIBLE; i++)
            if (!items.get(i).url.equals(currentUrl)) related.add(items.get(i));
        return related;
    }

    NativeContentItem next(String currentUrl) {
        return next(currentUrl, java.util.Collections.emptySet());
    }

    NativeContentItem next(String currentUrl, Set<String> visited) {
        if (!episodic) {
            for (int i = 0; i + 1 < items.size(); i++)
                if (items.get(i).url.equals(currentUrl)) {
                    for (int j = i + 1; j < items.size(); j++)
                        if (!visited.contains(items.get(j).url)) return items.get(j);
                    break;
                }
            for (NativeContentItem item : items)
                if (!item.url.equals(currentUrl) && !visited.contains(item.url)) return item;
            return null;
        }
        NativeContentItem current = null;
        for (NativeContentItem item : items) if (item.url.equals(currentUrl)) { current = item; break; }
        int[] currentNumber = current == null ? null : episodeNumber(current.title);
        if (currentNumber != null) {
            NativeContentItem nearest = null;
            int distance = Integer.MAX_VALUE;
            int seasonDistance = Integer.MAX_VALUE;
            for (NativeContentItem item : items) {
                int[] candidate = episodeNumber(item.title);
                if (candidate == null || visited.contains(item.url)) continue;
                int seasonGap = candidate[0] - currentNumber[0];
                if (seasonGap < 0 || (seasonGap == 0 && candidate[1] <= currentNumber[1])) continue;
                int gap = seasonGap == 0 ? candidate[1] - currentNumber[1] : candidate[1];
                if (seasonGap < seasonDistance || (seasonGap == seasonDistance && gap < distance)) {
                    nearest = item; seasonDistance = seasonGap; distance = gap;
                }
            }
            return nearest;
        }
        for (int i = 0; i + 1 < items.size(); i++) {
            if (items.get(i).url.equals(currentUrl) && !visited.contains(items.get(i + 1).url))
                return items.get(i + 1);
        }
        return null;
    }

    private static int[] episodeNumber(String title) {
        Matcher season = SEASON_EPISODE.matcher(title);
        if (season.find()) return new int[]{Integer.parseInt(season.group(1)), Integer.parseInt(season.group(2))};
        Matcher episode = EPISODE.matcher(title);
        if (episode.find()) return new int[]{0, Integer.parseInt(episode.group(1))};
        return null;
    }
}
