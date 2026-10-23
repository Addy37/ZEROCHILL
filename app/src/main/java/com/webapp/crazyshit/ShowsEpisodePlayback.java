package com.webapp.crazyshit;

import java.util.List;

/** Selects from actual series membership using the existing history completion rules. */
final class ShowsEpisodePlayback {
    static boolean playable(NativeContentItem item) {
        return item != null && item.isVideo() && item.url != null && !item.url.trim().isEmpty();
    }

    static Selection select(List<NativeContentItem> episodes, List<PlaybackHistoryStore.Item> history) {
        NativeContentItem first = null, resume = null;
        long position = 0L, latest = -1L;
        for (NativeContentItem episode : episodes) {
            if (!playable(episode)) continue;
            if (first == null) first = episode;
            for (PlaybackHistoryStore.Item watched : history) {
                if (episode.url.equals(watched.pageUrl) && PlaybackHistoryStore.isShowsContinueCandidate(watched)
                        && watched.lastWatched > latest) {
                    resume = episode;
                    position = watched.positionMs;
                    latest = watched.lastWatched;
                }
            }
        }
        return new Selection(resume == null ? first : resume, resume == null ? 0L : position);
    }

    static final class Selection {
        final NativeContentItem item;
        final long positionMs;
        Selection(NativeContentItem item, long positionMs) { this.item = item; this.positionMs = positionMs; }
    }
}
