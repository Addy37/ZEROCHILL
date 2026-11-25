package com.webapp.crazyshit;

import java.util.ArrayList;
import java.util.List;

/** Layout policy for an album with full-width video rows and a photo grid. */
final class AlbumMediaPresentation {
    static final int ALL = 0;
    static final int PICTURES = 1;
    static final int VIDEOS = 2;

    static int normalize(int filter) {
        return filter >= ALL && filter <= VIDEOS ? filter : ALL;
    }

    static int availableFilter(int selected, int pictures, int videos) {
        if (pictures == 0 || videos == 0) return ALL;
        return normalize(selected);
    }

    static List<NativeContentItem> pictures(List<NativeContentItem> items) {
        List<NativeContentItem> result = new ArrayList<>();
        for (NativeContentItem item : items) {
            if (item != null && item.isImage()) result.add(item);
        }
        return result;
    }

    static List<NativeContentItem> visible(List<NativeContentItem> items, int filter) {
        List<NativeContentItem> result = new ArrayList<>();
        for (NativeContentItem item : items) {
            if (item == null) continue;
            if (item.isImage() && filter != VIDEOS || item.isVideo() && filter != PICTURES)
                result.add(item);
        }
        return result;
    }

    static String countLabel(int pictures, int videos) {
        return pictures + (pictures == 1 ? " picture" : " pictures")
                + " · " + videos + (videos == 1 ? " video" : " videos");
    }
}
