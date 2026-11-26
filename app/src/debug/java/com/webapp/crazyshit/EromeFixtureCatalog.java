package com.webapp.crazyshit;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/** Inert, invented data for the Phase 1 phone preview. No live provider access. */
final class EromeFixtureCatalog {
    static final String ORIGIN = "https://erome-preview.invalid/";
    static final int PAGE_SIZE = 3;
    static final String ALL = "all", PICTURES = "pictures", VIDEOS = "videos";

    static final class Album {
        final int id;
        final String title, uploader;
        final List<String> tags;
        final int pictures, videos;
        Album(int id, String title, String uploader, int pictures, int videos, String... tags) {
            this.id = id; this.title = title; this.uploader = uploader;
            this.pictures = pictures; this.videos = videos;
            this.tags = Collections.unmodifiableList(Arrays.asList(tags));
        }
        String cover() { return ORIGIN + "art/" + id * 10; }
        List<NativeContentItem> media(String filter) {
            List<NativeContentItem> result = new ArrayList<>();
            for (int i = 0; i < Math.max(pictures, videos); i++) {
                if (i < pictures && !VIDEOS.equals(filter)) result.add(item(i * 2, false));
                if (i < videos && !PICTURES.equals(filter)) result.add(item(i * 2 + 1, true));
            }
            return result;
        }
        private NativeContentItem item(int index, boolean video) {
            String idPart = id + "-" + index;
            return new NativeContentItem(video ? NativeContentItem.KIND_MEDIA : NativeContentItem.KIND_IMAGE,
                    (video ? "Sample clip " : "Sample photo ") + (index / 2 + 1),
                    ORIGIN + "media/" + idPart + (video ? ".mp4" : ".png"),
                    ORIGIN + "art/" + (id * 10 + index), "", uploader, "", "Synthetic fixture", "",
                    0L, 0f);
        }
    }
    private static final List<Album> ALBUMS = Collections.unmodifiableList(Arrays.asList(
            new Album(0, "Color study", "Studio North", 4, 2, "color", "design"),
            new Album(1, "After the rain", "Field Notes", 5, 1, "nature", "light"),
            new Album(2, "Shapes in motion", "Studio North", 3, 3, "motion", "geometry"),
            new Album(3, "Blue hour", "Quiet Frames", 4, 1, "light", "color"),
            new Album(4, "Paper textures", "Studio North", 6, 0, "design", "texture"),
            new Album(5, "Garden sketches", "Field Notes", 5, 2, "nature", "color"),
            new Album(6, "Motion loops", "Quiet Frames", 0, 4, "motion", "design"),
            new Album(7, "Geometry", "Studio North", 5, 1, "geometry", "design"),
            new Album(8, "Still life", "Quiet Frames", 3, 2, "texture", "light")));

    static Album album(int id) {
        for (Album album : ALBUMS) if (album.id == id) return album;
        return null;
    }
    static List<Album> matching(String uploader, String tag) {
        List<Album> result = new ArrayList<>();
        for (Album album : ALBUMS) {
            if ((uploader == null || uploader.isEmpty() || uploader.equals(album.uploader))
                    && (tag == null || tag.isEmpty() || album.tags.contains(tag))) result.add(album);
        }
        return result;
    }
    static List<Album> page(String uploader, String tag, int offset) {
        List<Album> matches = matching(uploader, tag);
        int from = Math.max(0, Math.min(matches.size(), offset));
        return new ArrayList<>(matches.subList(from, Math.min(matches.size(), from + PAGE_SIZE)));
    }
    static boolean isArtwork(String url) { return url != null && url.startsWith(ORIGIN + "art/"); }
    private EromeFixtureCatalog() { }
}
